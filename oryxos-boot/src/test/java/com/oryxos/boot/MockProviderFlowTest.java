package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.AgentService;
import com.oryxos.core.SessionPersistencePort;
import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.exception.ProviderNotFoundException;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.PromptBuilder;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.react.ToolAuditPort;
import com.oryxos.core.react.ToolCall;
import com.oryxos.core.react.ToolExecutor;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.memory.MarkdownMemoryStore;
import com.oryxos.memory.MemoryServiceImpl;
import com.oryxos.memory.MemoryTools;
import com.oryxos.provider.MockChatModel;
import com.oryxos.provider.ProviderProperties;
import com.oryxos.provider.ProviderService;
import com.oryxos.provider.ToolSchemaAdapter;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

/**
 * MockProviderFlowTest(第 27 节):手工装配、不依赖 Spring 上下文的全链路测试——mock provider 驱动一次真实的
 * ReAct(LLM→ToolExecutor→Memory 写 MEMORY.md→第二轮答复),外加 provider 未配置的失败审计路径。 只有模型是假的,其余全部走真实类。
 */
class MockProviderFlowTest {

  @TempDir Path workspace;

  private InMemoryAudit audit;
  private LlmCallRepository llmCallRepository;
  private AgentService agentService;
  private ProviderService providerService;

  @BeforeEach
  void assemble() {
    ToolRegistry toolRegistry = new ToolRegistry();
    MemoryService memoryService =
        new MemoryServiceImpl(new MarkdownMemoryStore(workspace.toString()));
    MemoryTools memoryTools = new MemoryTools(memoryService);
    toolRegistry.register(memoryTools.new SaveMemoryTool());
    toolRegistry.register(memoryTools.new RecallMemoryTool());

    audit = new InMemoryAudit();
    ToolExecutor toolExecutor = new ToolExecutor(toolRegistry, audit);

    llmCallRepository = fakeLlmCallRepository();
    providerService =
        new ProviderService(
            java.util.Map.of("mock", new MockChatModel()),
            java.util.Map.of("mock", new ProviderProperties.ProviderEntry("mock", null, null)),
            RestClient.builder(),
            new ToolSchemaAdapter(),
            llmCallRepository);

    PromptBuilder promptBuilder =
        new PromptBuilder(new ContextLoader(workspace), memoryService, toolRegistry);
    ReActLoop reActLoop =
        new ReActLoop(promptBuilder, providerService, toolExecutor, memoryService, toolRegistry);

    ProfileRegistry profileRegistry = new ProfileRegistry();
    Profile mockProfile = new Profile();
    mockProfile.setName("mock-agent");
    mockProfile.setProvider(new Profile.Provider("mock", "mock", null));
    mockProfile.setTools(List.of("save_memory", "recall_memory"));
    profileRegistry.register(mockProfile);

    SessionPersistencePort sessionPort = Mockito.mock(SessionPersistencePort.class);
    agentService =
        new AgentService(
            profileRegistry, new AgentLoader(workspace.resolve("agents")), reActLoop, sessionPort);
  }

  @Test
  @DisplayName("mock全链路:记住→save_memory真实写MEMORY.md→第二轮答复;两轮llm_calls成功,工具调用落审计")
  void mockProvider_drivesFullReActChain() throws Exception {
    Session session = new Session("cli:test:mock-agent", "mock-agent", "cli", "test");

    String reply = agentService.process(session, "记住:我住在上海");

    assertThat(reply).isEqualTo("好的，已记住：我住在上海");

    // 第二轮答复成功,说明 tool 结果经 toSpringAiMessages 保真还原成 ToolResponseMessage(会话恢复×Function Calling 回归点)
    List<String> roles = session.getMessages().stream().map(Message::role).toList();
    assertThat(roles).containsExactly("user", "assistant", "tool", "assistant");

    // save_memory 真实写入 MEMORY.md 归档区
    String memoryFile =
        Files.readString(workspace.resolve("memory/MEMORY.md"), StandardCharsets.UTF_8);
    assertThat(memoryFile).contains("## 归档记忆").contains("我住在上海");

    // 工具执行审计:一条 save_memory 成功
    assertThat(audit.rows).hasSize(1);
    assertThat(audit.rows.get(0).toolName()).isEqualTo("save_memory");
    assertThat(audit.rows.get(0).success()).isTrue();

    // LLM 调用审计:两轮都成功(10/20/30 usage 入库)
    assertThat(llmCallRepository.findAll()).hasSize(2);
    assertThat(llmCallRepository.findAll()).allSatisfy(row -> assertThat(row.isSuccess()).isTrue());
  }

  @Test
  @DisplayName("provider未配置:抛ProviderNotFoundException且llm_calls落一条success=false审计行")
  void providerNotRegistered_failsAndAudits() {
    Profile broken = new Profile();
    broken.setName("broken");
    broken.setProvider(new Profile.Provider("no-such-provider", "x", null));

    assertThatThrownBy(
            () ->
                providerService.chat(
                    "cli:test:broken",
                    broken,
                    new com.oryxos.core.react.Prompt(List.of(Message.user("hi")), List.of())))
        .isInstanceOf(ProviderNotFoundException.class)
        .hasMessageContaining("no-such-provider");

    List<LlmCallEntity> rows = llmCallRepository.findBySessionId("cli:test:broken");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).isSuccess()).isFalse();
    assertThat(rows.get(0).getProvider()).isEqualTo("no-such-provider");
  }

  @Test
  @DisplayName("ToolExecutor对未注册工具:返回not found且落success=false审计行")
  void toolNotFound_failsAndAudits() {
    ToolExecutor toolExecutor = new ToolExecutor(new ToolRegistry(), audit);
    String result =
        toolExecutor.execute("s:1", new ToolCall("call_1", "no_such_tool", "{}"), new Profile());

    assertThat(result).contains("not found");
    assertThat(audit.rows).hasSize(1);
    assertThat(audit.rows.get(0).success()).isFalse();
  }

  /** 简易内存版 ToolAuditPort。 */
  private static class InMemoryAudit implements ToolAuditPort {
    final List<AuditRow> rows = new ArrayList<>();

    record AuditRow(String sessionId, String toolName, boolean success, String errorMessage) {}

    @Override
    public void record(
        String sessionId,
        String toolName,
        String inputJson,
        String resultJson,
        boolean success,
        String errorMessage,
        long durationMs) {
      rows.add(new AuditRow(sessionId, toolName, success, errorMessage));
    }
  }

  /** JPA 接口太重,用 Mockito 桩一个内存仓库:save 落内存列表,findBySessionId/count/findAll 真实返回。 */
  private static LlmCallRepository fakeLlmCallRepository() {
    List<LlmCallEntity> rows = new ArrayList<>();
    AtomicLong idSeq = new AtomicLong();
    LlmCallRepository repo = mock(LlmCallRepository.class);
    when(repo.save(any(LlmCallEntity.class)))
        .thenAnswer(
            inv -> {
              LlmCallEntity e = inv.getArgument(0);
              e.setId(idSeq.incrementAndGet());
              rows.add(e);
              return e;
            });
    when(repo.findBySessionId(any()))
        .thenAnswer(
            inv -> rows.stream().filter(r -> inv.getArgument(0).equals(r.getSessionId())).toList());
    when(repo.findAll()).thenReturn(rows);
    when(repo.count()).thenReturn((long) rows.size());
    return repo;
  }
}
