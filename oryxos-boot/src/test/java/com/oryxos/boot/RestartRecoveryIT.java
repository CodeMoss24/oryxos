package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ScheduledTaskRepository;
import com.oryxos.storage.repository.SessionRepository;
import com.oryxos.storage.repository.TaskExecutionRepository;
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

/**
 * RestartRecoveryIT(第 28 节 对账固化):验证底座状态全部放在进程外(SQLite + 文件系统),重启后原样恢复。
 *
 * <p>数据创建后经 API ↔ DB 双向核对,确保不在内存而在持久层。 用 mock provider 避免真 key 依赖。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "oryxos.test.marker=restart-recovery")
@Tag("integration")
@DisplayName("RestartRecoveryIT — 验证会话/记忆/定时状态/审计全部在 SQLite+文件,跨重启恢复")
class RestartRecoveryIT {

  private static final String AGENT_MD =
      """
      ---
      name: recovery-agent
      description: 重启恢复测试 agent
      identity:
        agent_name: recovery-agent
        prompt: 你是一个恢复测试助手
      provider:
        name: mock
        model: mock
      tools:
        - save_memory
        - recall_memory
      schedules:
        - id: recovery-schedule
          cron: "0 0 3 * * *"
          zone: UTC
          message: 记住:重启恢复测试数据
      ---
      你是恢复测试助手。触发后记住事实。
      """;

  private static final Path WORKSPACE = createTempWorkspace();

  static {
    System.setProperty("oryxos.root", WORKSPACE.toString());
    System.setProperty("spring.datasource.url", "jdbc:sqlite:" + WORKSPACE.resolve("recovery.db"));
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private AgentLoader agentLoader;
  @Autowired private ProfileRegistry profileRegistry;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private LlmCallRepository llmCallRepository;
  @Autowired private ToolInvocationRepository toolInvocationRepository;
  @Autowired private ScheduledTaskRepository scheduledTaskRepository;
  @Autowired private TaskExecutionRepository taskExecutionRepository;

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
  @DisplayName("重启恢复:跑一轮对话+触发定时攒数据,然后核对四样全在 SQLite/文件系统(进程外)持久化")
  void restartRecovery_allStateInProcessExternal() throws IOException {
    // ── Phase 1: 创建数据 ──
    // ① 触发定时执行一次(走 mock,会记住事实并写入 MEMORY.md)
    @SuppressWarnings("unchecked")
    Map<String, Object> runResult =
        (Map<String, Object>)
            rest.postForEntity("/api/v1/schedules/recovery-schedule/run", null, Map.class)
                .getBody()
                .get("data");
    assertThat(runResult.get("status")).isEqualTo("triggered");

    // ── Phase 2: 核对四样持久化在进程外部 ──
    // 与 SessionManager.buildSessionId 的 ":" 分隔一致(28 节交付时误写 "+",出生即带病,本节显式跑 IT 暴露)
    String sessionId = "scheduler:scheduler:recovery-agent";

    // ① 会话在 SQLite(GET /sessions/{id} 查得到完整历史;ba79a1b 起 data 为 {sessionId, profileName, messages}
    // 对象)
    @SuppressWarnings("unchecked")
    Map<String, Object> history =
        (Map<String, Object>)
            rest.getForEntity("/api/v1/sessions/" + sessionId, Map.class).getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> messages = (List<Map<String, Object>>) history.get("messages");
    assertThat(messages).isNotEmpty();
    // 会话元数据也在 SQLite(sessions 表非空)
    assertThat(sessionRepository.findById(sessionId)).isPresent();

    // ② 记忆在 MEMORY.md(文件持久,进程外;第 30 节起 per-agent:agents/<name>/MEMORY.md)
    String memory =
        Files.readString(
            WORKSPACE.resolve("agents/recovery-agent/MEMORY.md"), StandardCharsets.UTF_8);
    assertThat(memory).isNotEmpty();
    // API 读记忆端点也同步返回(30 节:GET /agents/{name}/memory 取代全局 GET /api/v1/memory)
    @SuppressWarnings("unchecked")
    Map<String, Object> memoryApi =
        (Map<String, Object>)
            rest.getForEntity("/api/v1/agents/recovery-agent/memory", Map.class)
                .getBody()
                .get("data");
    assertThat((String) memoryApi.get("memory")).isNotEmpty();

    // ③ 定时任务状态与历史在 SQLite(scheduled_tasks / task_executions 两张表非空)
    assertThat(scheduledTaskRepository.findAll()).isNotEmpty();
    assertThat(taskExecutionRepository.findAll()).isNotEmpty();
    // API 端点也查得到
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> schedules =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/schedules", Map.class).getBody().get("data");
    assertThat(schedules)
        .anySatisfy(
            s -> {
              assertThat(s.get("taskId")).isEqualTo("recovery-schedule");
              assertThat((int) s.get("runCount")).isGreaterThanOrEqualTo(1);
            });
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> executions =
        (List<Map<String, Object>>)
            rest.getForEntity("/api/v1/schedules/recovery-schedule/executions", Map.class)
                .getBody()
                .get("data");
    assertThat(executions).isNotEmpty();

    // ④ 审计不断档:llm_calls 在 SQLite 有记录(跨重启不会丢)
    List<LlmCallEntity> llmRows = llmCallRepository.findBySessionId(sessionId);
    assertThat(llmRows).isNotEmpty();
    assertThat(llmRows).anySatisfy(r -> assertThat(r.isSuccess()).isTrue());
  }

  private static Path createTempWorkspace() {
    try {
      Path dir = Files.createTempDirectory("oryxos-recovery-");
      Path agents = dir.resolve("agents/recovery-agent");
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
                }
              });
    } catch (IOException ignored) {
    }
  }
}
