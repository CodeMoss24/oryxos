package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.AgentService;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskStore;
import com.oryxos.core.session.SessionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * 第 29 节 harness:启动扫描注册。 扫一个含 N 个 Agent 目录的目录 → ProfileRegistry 出现 N 个;带 schedules 的都进了
 * AgentScheduler (句柄表有句柄);坏目录/缺必填项目录不阻断其余。
 */
@DisplayName("AgentScanRegister — 扫描 → 派生 → 注册 → 进调度器")
class AgentScanRegisterTest {

  @TempDir Path agentsDir;

  private void writeAgent(String name, String content) {
    try {
      Path dir = agentsDir.resolve(name);
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("AGENT.md"), content);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private AgentScheduler newScheduler(ProfileRegistry registry) {
    TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
    Mockito.when(taskScheduler.schedule(Mockito.any(Runnable.class), Mockito.any(Trigger.class)))
        .thenAnswer(inv -> Mockito.mock(java.util.concurrent.ScheduledFuture.class));
    return new AgentScheduler(
        registry,
        Mockito.mock(AgentService.class),
        Mockito.mock(SessionManager.class),
        taskScheduler,
        Mockito.mock(ScheduledTaskStore.class));
  }

  @Test
  @DisplayName("扫 N 个合法 Agent 目录 → ProfileRegistry 出现 N 个")
  void scanRegistersAllLegalAgents() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "a",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # a
            """);
    writeAgent(
        "b",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # b
            """);
    writeAgent(
        "c",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # c
            """);

    int count = loader.scanAndRegister(registry);
    assertThat(count).isEqualTo(3);
    assertThat(registry.list()).hasSize(3);
    assertThat(registry.exists("a")).isTrue();
    assertThat(registry.exists("b")).isTrue();
    assertThat(registry.exists("c")).isTrue();
  }

  @Test
  @DisplayName("带 schedules 的 Agent 扫描后进了 AgentScheduler(句柄表有句柄)")
  void scanRegistersSchedulesIntoScheduler() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "recon",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            schedules:
              - id: recon-morning
                cron: "0 0 9 * * *"
                zone: Asia/Shanghai
                message: 到点了
            ---
            # recon
            """);
    writeAgent(
        "plain",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # plain
            """);
    AgentScheduler scheduler = newScheduler(registry);

    loader.scanAndRegister(registry);
    scheduler.registerAll();

    Object handle = scheduler.scheduledFutureFor("recon-morning");
    assertThat(handle).isNotNull();
  }

  @Test
  @DisplayName("缺必填项的 Agent 不阻断其余加载")
  void scanSkipsInvalidDoesNotBlockOthers() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "broken",
        """
            ---
            description: "no provider"
            ---
            # broken
            """);
    writeAgent(
        "good",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # good
            """);

    int count = loader.scanAndRegister(registry);
    assertThat(count).isEqualTo(1);
    assertThat(registry.exists("good")).isTrue();
    assertThat(registry.exists("broken")).isFalse();
  }

  @Test
  @DisplayName("无 AGENT.md 的目录被跳过不阻断其余")
  void scanSkipsDirWithoutAgentMd() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    try {
      Files.createDirectories(agentsDir.resolve("empty"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    writeAgent(
        "good",
        """
            ---
            provider:
              name: deepseek
              model: deepseek-chat
            ---
            # good
            """);

    int count = loader.scanAndRegister(registry);
    assertThat(count).isEqualTo(1);
    assertThat(registry.exists("good")).isTrue();
  }
}
