package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.scheduler.ScheduleConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 第 29 节 harness:deriveProfile。 frontmatter 各字段正确映射到 Profile;尤其 schedules 原样带进派生 Profile(定时来自 Agent
 * 的直接证据); notify_channels 的 ${ENV} 占位解析。
 */
@DisplayName("deriveProfile — frontmatter 字段映射到 Profile")
class DeriveProfileTest {

  @TempDir Path agentsDir;

  private AgentLoader loader;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    loader = new AgentLoader(agentsDir);
  }

  private int counter = 0;

  private AgentLoader.ParsedAgentMd parse(String yaml, String body) {
    try {
      // 每次用唯一目录名,避免多测试复用同目录
      Path dir = agentsDir.resolve("agent-" + (counter++));
      Files.createDirectories(dir);
      Path md = dir.resolve("AGENT.md");
      Files.writeString(md, "---\n" + yaml + "\n---\n" + body);
      return loader.parseAgentMd(md);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("identity/provider/tools 全字段映射到 Profile")
  void allFieldsMapped() {
    var parsed =
        parse(
            """
                    description: "每日对账"
                    identity:
                      agent_name: "对账小欧"
                      prompt: "你是一个严谨的对账助手"
                    provider:
                      name: deepseek
                      model: deepseek-chat
                      temperature: 0.2
                    tools:
                      - shell
                      - read_file
                      - notify
                      - save_memory
                    """,
            "# body");

    Profile p = loader.deriveProfile("daily-reconcile", parsed);
    assertThat(p.getName()).isEqualTo("daily-reconcile");
    assertThat(p.getDescription()).isEqualTo("每日对账");
    assertThat(p.getIdentity().agentName()).isEqualTo("对账小欧");
    assertThat(p.getIdentity().prompt()).isEqualTo("你是一个严谨的对账助手");
    assertThat(p.getProvider().name()).isEqualTo("deepseek");
    assertThat(p.getProvider().model()).isEqualTo("deepseek-chat");
    assertThat(p.getProvider().temperature()).isEqualTo(0.2);
    assertThat(p.getTools()).containsExactly("shell", "read_file", "notify", "save_memory");
  }

  @Test
  @DisplayName("schedules 原样带进派生的 Profile(定时来自 Agent 的直接证据)")
  void schedulesCarriedIntoDerivedProfile() {
    var parsed =
        parse(
            """
                    provider:
                      name: deepseek
                      model: deepseek-chat
                    schedules:
                      - id: reconcile-morning
                        cron: "0 0 9 * * *"
                        zone: Asia/Shanghai
                        message: 到点了，核对昨天的订单对账。
                      - id: reconcile-evening
                        cron: "0 0 18 * * *"
                        zone: Asia/Shanghai
                        message: 晚复核
                    """,
            "# body");

    Profile p = loader.deriveProfile("daily-reconcile", parsed);
    assertThat(p.getSchedules()).hasSize(2);
    ScheduleConfig morning = p.getSchedules().get(0);
    assertThat(morning.id()).isEqualTo("reconcile-morning");
    assertThat(morning.cron()).isEqualTo("0 0 9 * * *");
    assertThat(morning.zone()).isEqualTo("Asia/Shanghai");
    assertThat(morning.message()).isEqualTo("到点了，核对昨天的订单对账。");
    assertThat(p.getSchedules().get(1).id()).isEqualTo("reconcile-evening");
  }

  @Test
  @DisplayName("notify_channels 映射为 NotifyChannel(type + config)")
  void notifyChannelsMappedToNotifyChannel() {
    var parsed =
        parse(
            """
                    provider:
                      name: deepseek
                      model: deepseek-chat
                    notify_channels:
                      - type: webhook
                        url: https://hooks.example.com/ops
                    """,
            "# body");

    Profile p = loader.deriveProfile("daily-reconcile", parsed);
    assertThat(p.getNotifyChannels()).hasSize(1);
    Profile.NotifyChannel ch = p.getNotifyChannels().get(0);
    assertThat(ch.type()).isEqualTo("webhook");
    assertThat(ch.config()).containsEntry("url", "https://hooks.example.com/ops");
  }

  @Test
  @DisplayName("provider.temperature 缺省时为 null(不报错)")
  void temperatureOptional() {
    var parsed =
        parse(
            """
                    provider:
                      name: deepseek
                      model: deepseek-chat
                    """,
            "# body");

    Profile p = loader.deriveProfile("x", parsed);
    assertThat(p.getProvider().temperature()).isNull();
  }
}
