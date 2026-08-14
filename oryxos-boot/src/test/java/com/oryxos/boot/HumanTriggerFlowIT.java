package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ToolCall;
import com.oryxos.core.react.ToolExecutor;
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
import org.junit.jupiter.api.Tag;
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
 * HumanTriggerFlowIT(第 27 节 对账固化):"人推"全流程——REST 无状态调用 → ReAct → 工具执行 → 审计落库 → 会话列表可见,
 * 外加三条确定性失败路径(provider 未配置 / 沙箱越界 / 工具抛异常),成败都落审计。
 *
 * <p>成功路径用真 key(与 ProviderSmokeIT 同口径,assumeTrue 无 key 跳过);失败路径不依赖模型,恒跑。 临时工作区与临时 SQLite
 * 由系统属性在上下文加载前注入,marker 属性保证上下文不复用。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "oryxos.test.marker=human-trigger-flow",
      // 天气 agent 只允许访问 wttr.in(第 26 节演示同款域名),替换 yaml 的 open.feishu.cn
      "http.allowed-domains=wttr.in"
    })
@Tag("integration")
@DisplayName("HumanTriggerFlowIT — 人推全流程:真模型天气查询 + 三条失败路径,成败都落审计")
class HumanTriggerFlowIT {

  /** 注意:必须声明在 WORKSPACE 之前——静态初始化按文本顺序执行。 */
  private static final String WEATHER_AGENT_MD =
      """
      ---
      name: weather
      description: 天气助手
      identity:
        agent_name: weather
        prompt: 你是一个天气助手
      provider:
        name: deepseek
        model: deepseek-chat
      tools:
        - http_get
        - notify
      ---
      天气查询:必须调用 http_get 工具访问 http://wttr.in/Shanghai?format=3 获取天气,然后把返回内容汇报给用户,不要编造数据。
      """;

  private static final String BROKEN_PROVIDER_AGENT_MD =
      """
      ---
      name: broken-provider
      description: 配置了不存在 provider 的 agent
      identity:
        agent_name: broken-provider
        prompt: 测试用
      provider:
        name: no-such-provider
        model: x
      tools: []
      ---
      用于验证 provider 未配置的失败路径。
      """;

  private static final Path WORKSPACE = createTempWorkspace();

  static {
    System.setProperty("oryxos.root", WORKSPACE.toString());
    System.setProperty("spring.datasource.url", "jdbc:sqlite:" + WORKSPACE.resolve("it.db"));
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private AgentLoader agentLoader;
  @Autowired private ProfileRegistry profileRegistry;
  @Autowired private LlmCallRepository llmCallRepository;
  @Autowired private ToolInvocationRepository toolInvocationRepository;
  @Autowired private ToolExecutor toolExecutor;

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
  @DisplayName("人推天气查询(真模型):REST invoke 200,http_get 成功落审计,llm_calls 成功,会话列表可见")
  void humanTriggeredWeatherQuery_fullFlow() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "Skipping: DEEPSEEK_API_KEY not set");

    ResponseEntity<Map> resp =
        rest.postForEntity(
            "/api/v1/agents/weather/invoke",
            jsonBody(Map.of("content", "今天上海天气怎么样?", "user_id", "it-user")),
            Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat((String) data.get("reply")).isNotBlank();

    String sessionId = "web:it-user:weather";

    // 工具执行审计:http_get 成功一次
    List<ToolInvocationEntity> toolRows = toolInvocationRepository.findBySessionId(sessionId);
    assertThat(toolRows)
        .anySatisfy(
            r -> {
              assertThat(r.getToolName()).isEqualTo("http_get");
              assertThat(r.getSuccess()).isTrue();
            });

    // LLM 调用审计:至少一轮成功(工具轮 + 收尾轮)
    List<LlmCallEntity> llmRows = llmCallRepository.findBySessionId(sessionId);
    assertThat(llmRows).isNotEmpty();
    assertThat(llmRows).anySatisfy(r -> assertThat(r.isSuccess()).isTrue());

    // 会话列表(第 27 节摘要端点)包含本次会话
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sessions =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/sessions", Map.class).getBody().get("data");
    assertThat(sessions)
        .anySatisfy(
            s -> {
              assertThat(s.get("sessionId")).isEqualTo(sessionId);
              assertThat(s.get("profileName")).isEqualTo("weather");
            });
  }

  @Test
  @DisplayName("失败路径1:provider未配置 → invoke 503(Provider故障),llm_calls 落 success=false 审计行(不依赖模型)")
  void invokeBrokenProvider_returns503AndAuditsFailure() {
    ResponseEntity<Map> resp =
        rest.postForEntity(
            "/api/v1/agents/broken-provider/invoke",
            jsonBody(Map.of("content", "hi", "user_id", "it-user")),
            Map.class);

    // AgentApiController 把引擎异常包成 IllegalStateException,GlobalExceptionHandler 映射为 503 Provider 故障
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    List<LlmCallEntity> rows = llmCallRepository.findBySessionId("web:it-user:broken-provider");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).isSuccess()).isFalse();
    assertThat(rows.get(0).getProvider()).isEqualTo("no-such-provider");
    assertThat(rows.get(0).getErrorMessage()).contains("no-such-provider");
  }

  @Test
  @DisplayName("失败路径2:沙箱越界 → read_file 拒绝执行,tool_invocations 落 success=false(不依赖模型)")
  void directToolCall_sandboxViolation_auditsFailure() {
    String result =
        toolExecutor.execute(
            "it:sandbox",
            new ToolCall("call_sb", "read_file", "{\"path\":\"/etc/passwd\"}"),
            new Profile());

    assertThat(result).contains("Tool failed");
    assertThat(result).doesNotContain("root:"); // 内容绝不能泄露

    List<ToolInvocationEntity> rows = toolInvocationRepository.findBySessionId("it:sandbox");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getToolName()).isEqualTo("read_file");
    assertThat(rows.get(0).getSuccess()).isFalse();
    assertThat(rows.get(0).getErrorMessage()).isNotBlank();
  }

  @Test
  @DisplayName("失败路径3:工具执行抛异常 → ask_user 失败,tool_invocations 落 success=false(不依赖模型)")
  void directToolCall_throwingTool_auditsFailure() {
    String result =
        toolExecutor.execute(
            "it:throw",
            new ToolCall("call_ask", "ask_user", "{\"question\":\"你好吗\"}"),
            new Profile());

    assertThat(result).contains("Tool failed");

    List<ToolInvocationEntity> rows = toolInvocationRepository.findBySessionId("it:throw");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getToolName()).isEqualTo("ask_user");
    assertThat(rows.get(0).getSuccess()).isFalse();
    assertThat(rows.get(0).getErrorMessage()).contains("用户交互");
  }

  private static HttpEntity<Map<String, String>> jsonBody(Map<String, String> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static Path createTempWorkspace() {
    try {
      Path dir = Files.createTempDirectory("oryxos-it-");
      Path agents = dir.resolve("agents");
      Files.createDirectories(agents.resolve("weather"));
      Files.writeString(
          agents.resolve("weather/AGENT.md"), WEATHER_AGENT_MD, StandardCharsets.UTF_8);
      Files.createDirectories(agents.resolve("broken-provider"));
      Files.writeString(
          agents.resolve("broken-provider/AGENT.md"),
          BROKEN_PROVIDER_AGENT_MD,
          StandardCharsets.UTF_8);
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
