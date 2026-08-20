package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * MockAgentE2ETest(第 27 节):@SpringBootTest 起真实上下文,经 REST 端点跑 mock provider 的完整人推链路——
 * 模型是假的,ProviderService / ReActLoop / ToolExecutor / Memory / SQLite 审计 / Session 持久化全部真实。 临时工作区与临时
 * SQLite 由系统属性在上下文加载前注入,marker 属性保证上下文不与其它 IT 复用。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "oryxos.test.marker=mock-agent-e2e")
@DisplayName("MockAgentE2ETest — mock provider 经 REST 全链路:记住→MEMORY.md→会话列表可见→审计落库")
class MockAgentE2ETest {

  /** 注意:必须声明在 WORKSPACE 之前——静态初始化按文本顺序执行。 */
  private static final String AGENT_MD =
      """
      ---
      name: mock-agent
      description: 记忆测试 agent
      identity:
        agent_name: mock-agent
        prompt: 你是一个记忆助手
      provider:
        name: mock
        model: mock
      tools:
        - save_memory
        - recall_memory
      ---
      你是记忆助手。用户说"记住：…"时用 save_memory 工具记住;需要回忆时用 recall_memory。
      """;

  private static final Path WORKSPACE = createTempWorkspace();

  static {
    System.setProperty("oryxos.root", WORKSPACE.toString());
    System.setProperty("spring.datasource.url", "jdbc:sqlite:" + WORKSPACE.resolve("e2e.db"));
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private AgentLoader agentLoader;
  @Autowired private ProfileRegistry profileRegistry;
  @Autowired private LlmCallRepository llmCallRepository;
  @Autowired private ToolInvocationRepository toolInvocationRepository;

  @BeforeEach
  void scanAgents() {
    agentLoader.scanAndRegister(profileRegistry);
  }

  @AfterAll
  static void cleanup() {
    System.clearProperty("oryxos.root");
    System.clearProperty("spring.datasource.url");
    deleteRecursively(WORKSPACE);
  }

  @Test
  @DisplayName("POST /agents/mock-agent/invoke 记住一条事实:答复含事实、MEMORY.md 落盘、会话列表可见、审计两表落库")
  void invoke_rememberFact_endToEnd() throws IOException {
    ResponseEntity<Map> resp =
        rest.postForEntity(
            "/api/v1/agents/mock-agent/invoke",
            jsonBody(Map.of("content", "记住:我是E2E用户", "user_id", "e2e-user")),
            Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(data.get("reply")).isEqualTo("好的，已记住：我是E2E用户");

    // 记忆真实写入临时工作区(第 30 节起 per-agent:agents/<name>/MEMORY.md 归档区)
    String memory =
        Files.readString(WORKSPACE.resolve("agents/mock-agent/MEMORY.md"), StandardCharsets.UTF_8);
    assertThat(memory).contains("## 归档记忆").contains("我是E2E用户");

    // 会话列表(第 27 节升级的摘要端点)能查到本次会话
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sessions =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/sessions", Map.class).getBody().get("data");
    assertThat(sessions)
        .anySatisfy(
            s -> {
              assertThat(s.get("sessionId")).isEqualTo("web:e2e-user:mock-agent");
              assertThat(s.get("profileName")).isEqualTo("mock-agent");
              assertThat(s.get("messageCount")).isEqualTo(4); // user/assistant/tool/assistant
            });

    // 会话历史完整(assistant 带 tool 消息,会话恢复×Function Calling 回归点)
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> history =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/sessions/web:e2e-user:mock-agent", Map.class)
                .getBody()
                .get("data");
    assertThat(history).hasSize(4);
    assertThat(history.get(2).get("role")).isEqualTo("tool");

    // 审计:两轮 llm_calls 都成功;save_memory 一条 success=true
    List<LlmCallEntity> llmRows = llmCallRepository.findBySessionId("web:e2e-user:mock-agent");
    assertThat(llmRows).hasSize(2).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());

    List<ToolInvocationEntity> toolRows =
        toolInvocationRepository.findBySessionId("web:e2e-user:mock-agent");
    assertThat(toolRows).hasSize(1);
    assertThat(toolRows.get(0).getToolName()).isEqualTo("save_memory");
    assertThat(toolRows.get(0).getSuccess()).isTrue();
  }

  private static HttpEntity<Map<String, String>> jsonBody(Map<String, String> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static Path createTempWorkspace() {
    try {
      Path dir = Files.createTempDirectory("oryxos-mock-e2e-");
      Path agents = dir.resolve("agents/mock-agent");
      Files.createDirectories(agents);
      Files.writeString(agents.resolve("AGENT.md"), AGENT_MD, StandardCharsets.UTF_8);
      return dir;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temp workspace", e);
    }
  }

  private static void deleteRecursively(Path dir) {
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // 清理失败不影响测试结果
                }
              });
    } catch (IOException ignored) {
      // 目录不存在等,忽略
    }
  }
}
