package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.scheduler.AgentScheduler;
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
import org.springframework.http.MediaType;

/**
 * ScheduledTaskE2ETest(第 28 节):mock provider 驱动定时任务子系统全流程——登记 / 立即执行 / 持久化 / 启停管理,都在 gate 内无 key
 * 跑通。
 *
 * <p>模型是假的(mock),ProviderService / ReActLoop / ToolExecutor / Memory / SQLite 审计 / Scheduler 全部真实。
 * 临时工作区与临时 SQLite 由系统属性在上下文加载前注入,marker 属性保证上下文不与其它 IT 复用。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "oryxos.test.marker=scheduled-task-e2e")
@DisplayName("ScheduledTaskE2ETest — mock 驱动定时任务子系统全流程:登记→执行→持久化→管理")
class ScheduledTaskE2ETest {

  /** 注意:必须声明在 WORKSPACE 之前——静态初始化按文本顺序执行。 */
  private static final String AGENT_MD =
      """
      ---
      name: scheduler-e2e
      description: 定时任务 E2E 测试 agent
      identity:
        agent_name: scheduler-e2e
        prompt: 你是一个定时任务测试助手
      provider:
        name: mock
        model: mock
      tools:
        - save_memory
        - recall_memory
      schedules:
        - id: e2e-schedule
          cron: "0 0 3 * * *"
          zone: UTC
          message: 记住:第28节定时任务E2E测试
      ---
      你是定时任务测试助手。用户说"记住：…"时用 save_memory 工具记住。
      """;

  private static final Path WORKSPACE = createTempWorkspace();

  static {
    System.setProperty("oryxos.root", WORKSPACE.toString());
    System.setProperty("spring.datasource.url", "jdbc:sqlite:" + WORKSPACE.resolve("e2e.db"));
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private AgentLoader agentLoader;
  @Autowired private ProfileRegistry profileRegistry;
  @Autowired private AgentScheduler scheduler;

  @BeforeEach
  void scanAgents() {
    agentLoader.scanAndRegister(profileRegistry);
    // 静态 Agent 扫描完再补调用注册定时任务(因为 @PostConstruct 在扫描前就已执行)
    scheduler.registerAll();
  }

  @AfterAll
  static void cleanup() {
    System.clearProperty("oryxos.root");
    System.clearProperty("spring.datasource.url");
    deleteRecursively(WORKSPACE);
  }

  @Test
  @DisplayName("定时任务子系统全流程:启动登记→立即执行→持久化→启停管理")
  void scheduledTaskFullFlow_registerExecuteManage() throws IOException {
    // ① 启动即登记:GET /api/v1/schedules 有这条任务、run_count=0、enabled=true
    @SuppressWarnings("unchecked")
    var respEntity = rest.getForEntity("/api/v1/schedules", Map.class);
    assertThat(respEntity.getStatusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> before = (List<Map<String, Object>>) respEntity.getBody().get("data");
    assertThat(before).isNotNull();
    assertThat(before)
        .anySatisfy(
            s -> {
              assertThat(s.get("taskId")).isEqualTo("e2e-schedule");
              assertThat(s.get("profileName")).isEqualTo("scheduler-e2e");
              assertThat(s.get("cron")).isEqualTo("0 0 3 * * *");
              assertThat(s.get("message")).isEqualTo("记住:第28节定时任务E2E测试");
              assertThat(s.get("enabled")).isEqualTo(true);
              assertThat((int) s.get("runCount")).isEqualTo(0);
            });

    // ② POST /schedules/{id}/run 立即执行 → 走真实 ReAct(mock 触发一次 save_memory)
    @SuppressWarnings("unchecked")
    Map<String, Object> runResult =
        (Map<String, Object>)
            rest.postForEntity("/api/v1/schedules/e2e-schedule/run", null, Map.class)
                .getBody()
                .get("data");
    assertThat(runResult.get("status")).isEqualTo("triggered");
    assertThat(runResult.get("taskId")).isEqualTo("e2e-schedule");

    // ③ 断言落库:run_count=1、last_status=success
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> after =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/schedules", Map.class).getBody().get("data");
    assertThat(after)
        .anySatisfy(
            s -> {
              assertThat(s.get("taskId")).isEqualTo("e2e-schedule");
              assertThat((int) s.get("runCount")).isEqualTo(1);
              assertThat(s.get("lastStatus")).isEqualTo("success");
            });

    // ④ GET /schedules/{id}/executions 有一条成功记录
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> executions =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/schedules/e2e-schedule/executions", Map.class)
                .getBody()
                .get("data");
    assertThat(executions).hasSize(1);
    assertThat(executions.get(0).get("success")).isEqualTo(true);
    assertThat(executions.get(0).get("taskId")).isEqualTo("e2e-schedule");

    // ⑤ 记忆写入:MEMORY.md 查得到(因为 mock 触发了 save_memory)
    String memory = Files.readString(WORKSPACE.resolve("memory/MEMORY.md"), StandardCharsets.UTF_8);
    assertThat(memory).contains("第28节定时任务E2E测试");

    // ⑥ PUT /schedules/{id} 停用 → 列表显示已停用
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    rest.put("/api/v1/schedules/e2e-schedule", new HttpEntity<>(Map.of("enabled", false), headers));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> afterDisable =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/schedules", Map.class).getBody().get("data");
    assertThat(afterDisable)
        .anySatisfy(
            s -> {
              assertThat(s.get("taskId")).isEqualTo("e2e-schedule");
              assertThat(s.get("enabled")).isEqualTo(false);
            });
  }

  @Test
  @DisplayName("不存在的 taskId 调用 runNow → 400(参数错误)")
  void runNow_unknownTaskId_returns400() {
    var resp = rest.postForEntity("/api/v1/schedules/no-such-task/run", null, Map.class);
    assertThat(resp.getStatusCode().value()).isEqualTo(400);
  }

  private static Path createTempWorkspace() {
    try {
      Path dir = Files.createTempDirectory("oryxos-scheduler-e2e-");
      Path agents = dir.resolve("agents/scheduler-e2e");
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
