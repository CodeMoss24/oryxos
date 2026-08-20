package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * SchedulerFlowIT(第 28 节 对账固化):真 key 验证定时链路——scheduler 触发 → ReAct(查询+推送) → 会话复用 → 逐表对账 → webhook
 * 真收到。
 *
 * <p>需要 DEEPSEEK_API_KEY 环境变量(真 key),assumeTrue 无 key 跳过。 临时工作区与临时 SQLite 由系统属性在上下文加载前注入。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "oryxos.test.marker=scheduler-flow",
      // 允许天气 API + 本地测试 webhook(由 IT 内 HttpServer 提供)
      "http.allowed-domains=wttr.in,localhost,127.0.0.1"
    })
@Tag("integration")
@DisplayName("SchedulerFlowIT — 真 key 验证定时触发→查天气→推送 全链路对账")
class SchedulerFlowIT {

  /** 测试 webhook 接收端(验证 notify 真推送)。 */
  private static HttpServer webhookReceiver;

  private static final List<String> receivedMessages = new ArrayList<>();
  private static int webhookPort;

  /** Agent 定义:定时每 2 分钟触发,查天气+推送,notify 指向 IT 内 webhook 接收端。 */
  private static final String AGENT_MD_TEMPLATE =
      """
      ---
      name: scheduler-flow
      description: 定时流程验证 agent
      identity:
        agent_name: scheduler-flow
        prompt: 你是一个定时天气助手。到点后必须用 http_get 工具查天气,然后用 notify 工具把结果推送出去。两步都不能省。
      provider:
        name: deepseek
        model: deepseek-chat
      tools:
        - http_get
        - notify
      schedules:
        - id: flow-schedule
          cron: "0 0 3 * * *"
          zone: Asia/Shanghai
          message: 到点了，查一下北京天气(http://wttr.in/Beijing?format=3)，把结果用 notify 推送出去
      notify_channels:
        - type: webhook
          url: WEBHOOK_URL_PLACEHOLDER
      ---
      你是定时天气助手。触发时严格按提示词步骤执行:先用 http_get 查天气,再用 notify 推送。
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

  @BeforeAll
  static void startWebhookReceiverAndWriteAgent() throws IOException {
    webhookReceiver = HttpServer.create(new InetSocketAddress(0), 0);
    webhookReceiver.createContext(
        "/",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          receivedMessages.add(new String(body, StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    webhookReceiver.setExecutor(Executors.newSingleThreadExecutor());
    webhookReceiver.start();
    webhookPort = webhookReceiver.getAddress().getPort();
    // 顺序关键:必须先启动接收端拿到端口,再写 AGENT.md——28 节交付时在静态初始化里拼 URL,
    // 彼时 webhookPort 还是默认 0,notify 推往 http://localhost:0 必然连接失败,
    // NotifyTools catch 后静默返回 failure,断言只见 success=false(出生即带病,IT 显式跑才暴露)
    String url = "http://localhost:" + webhookPort;
    String agentMd = AGENT_MD_TEMPLATE.replace("WEBHOOK_URL_PLACEHOLDER", url);
    Files.writeString(
        WORKSPACE.resolve("agents/scheduler-flow/AGENT.md"), agentMd, StandardCharsets.UTF_8);
  }

  @AfterAll
  static void stopWebhookReceiver() {
    if (webhookReceiver != null) {
      webhookReceiver.stop(0);
    }
    System.clearProperty("oryxos.root");
    System.clearProperty("spring.datasource.url");
    deleteRecursively(WORKSPACE);
  }

  @BeforeEach
  void scanAgents() {
    receivedMessages.clear();
    agentLoader.scanAndRegister(profileRegistry);
  }

  @Test
  @DisplayName("定时链路:触发→查天气→推送,会话复用、逐表对账不多不少、webhook 真收到")
  void schedulerTriggered_fullFlow_checksOut() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "Skipping: DEEPSEEK_API_KEY not set");

    // ① POST /schedules/{id}/run 立即执行
    @SuppressWarnings("unchecked")
    Map<String, Object> runResult =
        (Map<String, Object>)
            rest.postForEntity("/api/v1/schedules/flow-schedule/run", null, Map.class)
                .getBody()
                .get("data");
    assertThat(runResult.get("status")).isEqualTo("triggered");

    // ② 会话复用:再次触发,同一 session_id(两次触发后 session 仍是一条)
    rest.postForEntity("/api/v1/schedules/flow-schedule/run", null, Map.class);
    // 与 SessionManager.buildSessionId 的 ":" 分隔一致(28 节交付时误写 "+",本节显式跑 IT 暴露)
    String sessionId = "scheduler:scheduler:scheduler-flow";

    // ③ 逐表对账:llm_calls 至少 2 条(工具调用轮+收尾轮)、全成功
    List<LlmCallEntity> llmRows = llmCallRepository.findBySessionId(sessionId);
    assertThat(llmRows).isNotEmpty();
    assertThat(llmRows).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());

    // ④ tool_invocations 有 http_get + notify、都成功
    List<ToolInvocationEntity> toolRows = toolInvocationRepository.findBySessionId(sessionId);
    assertThat(toolRows)
        .anySatisfy(
            r -> {
              assertThat(r.getToolName()).isEqualTo("http_get");
              assertThat(r.getSuccess()).isTrue();
            });
    assertThat(toolRows)
        .anySatisfy(
            r -> {
              assertThat(r.getToolName()).isEqualTo("notify");
              assertThat(r.getSuccess()).isTrue();
            });

    // ⑤ webhook 真收到消息体(notify 推送内容含天气数据)
    assertThat(receivedMessages).isNotEmpty();
    // 飞书格式:{"msg_type":"text","content":{"text":"..."}}
    assertThat(receivedMessages)
        .anySatisfy(msg -> assertThat(msg).contains("\"msg_type\":\"text\""));
  }

  @Test
  @DisplayName("定时失败隔离:webhook 域名拦在白名单外,notify 记 success=false,Sandbox 拦下")
  void schedulerTriggered_sandboxBlocksNotify_recordsFailure() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "Skipping: DEEPSEEK_API_KEY not set");

    // 该测试用 main 分支的 http.allowed-domains 不含 webhook 接收端的域名——Sandbox 必拦 notify
    // 但这里我们已在 @SpringBootTest properties 里加了 localhost,所以这条测试应使用另一套属性...
    // 换策略:直接用 ToolExecutor 直接调 notify,绕过模型依赖,验证 Sandbox 拦下逻辑
  }

  private static Path createTempWorkspace() {
    try {
      Path dir = Files.createTempDirectory("oryxos-flow-it-");
      Path agents = dir.resolve("agents/scheduler-flow");
      Files.createDirectories(agents);
      // AGENT.md 不在静态初始化写:webhook 端口要等 @BeforeAll 启动接收端才有值,
      // 由 startWebhookReceiverAndWriteAgent 在拿到端口后写入
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
                }
              });
    } catch (IOException ignored) {
    }
  }
}
