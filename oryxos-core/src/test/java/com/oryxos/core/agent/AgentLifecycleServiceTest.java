package com.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.exception.ProfileValidationException;
import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.AgentLoader.ParsedAgentMd;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduleConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 第 30 节验收 harness:AgentLifecycleService。
 *
 * <p>守点(课件原样):create 按序执行(冲突一步拒、不写目录)、注册失败必须回滚已写的 Agent 目录不留半个 Agent(agentStore.delete + scheduler
 * 从未注册 + registry 不存在)、create 与 watcher 走同一段 register(agentDir)、delete 必须先停定时再动索引再归档目录 (InOrder 钉
 * unregisterProfile→remove→archive)、update schedules 变更先注销旧句柄再注册新的。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentLifecycleService — 动态管理 Agent 编排(第 30 节)")
class AgentLifecycleServiceTest {

  @Mock private AgentLoader agentLoader;
  @Mock private ProfileRegistry profileRegistry;
  @Mock private AgentScheduler agentScheduler;
  @Mock private AgentStore agentStore;
  @Mock private ProviderPort providerPort;

  private AgentLifecycleService lifecycle;

  @BeforeEach
  void setUp() {
    // 手动构造:author 配置经构造参数注入(与 Spring 的 @Value 注入同一条路径)
    lifecycle =
        new AgentLifecycleService(
            agentLoader,
            profileRegistry,
            agentScheduler,
            agentStore,
            providerPort,
            "deepseek",
            "deepseek-chat",
            "deepseek");
  }

  private static Profile profileNamed(String name, ScheduleConfig... schedules) {
    Profile profile = new Profile();
    profile.setName(name);
    if (schedules.length > 0) {
      profile.setSchedules(List.of(schedules));
    }
    return profile;
  }

  private static ScheduleConfig sc(String id) {
    return new ScheduleConfig(id, "0 0 9 * * *", "Asia/Shanghai", "到点了");
  }

  @Test
  @DisplayName("create_按序执行:冲突检查→脚手架→派生校验→注册")
  void create_runsExistsCheckScaffoldDeriveRegisterInOrder() throws Exception {
    Path agentDir = Path.of("/w/agents/weather-daily");
    when(profileRegistry.exists("weather-daily")).thenReturn(false);
    when(agentStore.scaffold("weather-daily", "每日天气")).thenReturn(agentDir);
    when(agentLoader.parseAgentMd(any(Path.class))).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile profile = profileNamed("weather-daily");
    when(agentLoader.deriveProfile(eq("weather-daily"), any())).thenReturn(profile);

    Profile result = lifecycle.create("weather-daily", "每日天气");

    assertThat(result).isSameAs(profile);
    InOrder inOrder = inOrder(profileRegistry, agentStore, agentLoader, agentScheduler);
    inOrder.verify(profileRegistry).exists("weather-daily");
    inOrder.verify(agentStore).scaffold("weather-daily", "每日天气");
    inOrder.verify(agentLoader).parseAgentMd(agentDir.resolve("AGENT.md"));
    inOrder.verify(agentLoader).deriveProfile(eq("weather-daily"), any());
    inOrder.verify(profileRegistry).register(profile);
    // 无 schedules → 不碰定时器
    verify(agentScheduler, never()).registerProfile(any());
  }

  @Test
  @DisplayName("create_有schedules_派生注册后注册定时")
  void create_withSchedules_registersScheduler() throws Exception {
    Path agentDir = Path.of("/w/agents/weather-daily");
    when(profileRegistry.exists("weather-daily")).thenReturn(false);
    when(agentStore.scaffold("weather-daily", "每日天气")).thenReturn(agentDir);
    when(agentLoader.parseAgentMd(any(Path.class))).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile profile = profileNamed("weather-daily", sc("weather-daily-morning"));
    when(agentLoader.deriveProfile(eq("weather-daily"), any())).thenReturn(profile);

    lifecycle.create("weather-daily", "每日天气");

    verify(agentScheduler).registerProfile(profile);
  }

  @Test
  @DisplayName("create_name冲突_第一步就拒_一个目录都不写")
  void create_nameConflictRejectedBeforeScaffold() {
    when(profileRegistry.exists("dupe")).thenReturn(true);

    assertThatThrownBy(() -> lifecycle.create("dupe", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dupe");

    verify(agentStore, never()).scaffold(anyString(), anyString());
  }

  @Test
  @DisplayName("注册失败_必须回滚已写的Agent目录_不留半个Agent")
  void create_registrationFailureRollsBackScaffoldedDir() throws Exception {
    Path agentDir = Path.of("/w/agents/rollback-me");
    when(profileRegistry.exists("rollback-me")).thenReturn(false);
    when(agentStore.scaffold("rollback-me", "x")).thenReturn(agentDir);
    when(agentLoader.parseAgentMd(any(Path.class))).thenReturn(new ParsedAgentMd(Map.of(), ""));
    doThrow(new ProfileValidationException("bad AGENT.md"))
        .when(agentLoader)
        .deriveProfile(eq("rollback-me"), any());

    assertThatThrownBy(() -> lifecycle.create("rollback-me", "x"))
        .isInstanceOf(ProfileValidationException.class);

    // 已写的目录必须删干净
    verify(agentStore).delete(agentDir);
    // 半成品绝不注册、绝不上定时
    verify(agentScheduler, never()).registerProfile(any());
    assertThat(profileRegistry.exists("rollback-me")).isFalse();
  }

  @Test
  @DisplayName("create与watcher走同一段register(agentDir):解析目录里AGENT.md→派生→注册")
  void register_agentDir_isTheSingleRegistrationPath() throws Exception {
    Path agentDir = Path.of("/w/agents/hand-dropped");
    when(agentLoader.parseAgentMd(agentDir.resolve("AGENT.md")))
        .thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile profile = profileNamed("hand-dropped");
    when(agentLoader.deriveProfile(eq("hand-dropped"), any())).thenReturn(profile);

    Profile result = lifecycle.register(agentDir);

    assertThat(result).isSameAs(profile);
    verify(profileRegistry).register(profile);
    verify(agentScheduler, never()).registerProfile(any());
  }

  @Test
  @DisplayName("register_坏目录(解析失败)_抛错不注册")
  void register_invalidAgentDir_throwsWithoutRegistering() throws Exception {
    Path agentDir = Path.of("/w/agents/broken");
    when(agentLoader.parseAgentMd(agentDir.resolve("AGENT.md")))
        .thenThrow(new java.io.IOException("no such file"));

    assertThatThrownBy(() -> lifecycle.register(agentDir)).isInstanceOf(RuntimeException.class);

    verify(profileRegistry, never()).register(any());
  }

  @Test
  @DisplayName("delete_必须先停定时_再移出索引_再归档目录")
  void delete_unregistersThenRemovesThenArchives() {
    Profile profile = profileNamed("to-delete", sc("to-delete-morning"));
    when(profileRegistry.find("to-delete")).thenReturn(Optional.of(profile));

    lifecycle.delete("to-delete");

    InOrder inOrder = inOrder(agentScheduler, profileRegistry, agentStore);
    inOrder.verify(agentScheduler).unregisterProfile(profile);
    inOrder.verify(profileRegistry).remove("to-delete");
    inOrder.verify(agentStore).archive("to-delete");
  }

  @Test
  @DisplayName("delete_未注册过的名字_跳过注销只归档")
  void delete_notRegistered_skipsSchedulerAndArchives() {
    when(profileRegistry.find("ghost")).thenReturn(Optional.empty());

    lifecycle.delete("ghost");

    verify(agentScheduler, never()).unregisterProfile(any());
    verify(profileRegistry).remove("ghost");
    verify(agentStore).archive("ghost");
  }

  @Test
  @DisplayName("update_schedules变更_先注销旧定时_再注册新定时")
  void update_schedulesChanged_unregistersOldThenRegistersNew() {
    Profile old = profileNamed("reconcile", sc("old-id"));
    when(profileRegistry.find("reconcile")).thenReturn(Optional.of(old));
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile fresh = profileNamed("reconcile", sc("new-id"));
    when(agentLoader.deriveProfile(eq("reconcile"), any())).thenReturn(fresh);

    lifecycle.update("reconcile", "---\n...");

    InOrder inOrder = inOrder(agentScheduler);
    inOrder.verify(agentScheduler).unregisterProfile(old);
    inOrder.verify(agentScheduler).registerProfile(fresh);
    verify(agentStore).writeAgentMd("reconcile", "---\n...");
    verify(profileRegistry).register(fresh);
  }

  @Test
  @DisplayName("update_schedules不变_不注销旧定时(registerProfile幂等跳过)")
  void update_schedulesUnchanged_keepsSchedulerUntouched() {
    Profile old = profileNamed("reconcile", sc("same-id"));
    when(profileRegistry.find("reconcile")).thenReturn(Optional.of(old));
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile fresh = profileNamed("reconcile", sc("same-id"));
    when(agentLoader.deriveProfile(eq("reconcile"), any())).thenReturn(fresh);

    lifecycle.update("reconcile", "---\n...");

    verify(agentScheduler, never()).unregisterProfile(any());
    verify(profileRegistry).register(fresh);
  }

  @Test
  @DisplayName("update_非法内容_先校验不落盘_目录不被写坏")
  void update_invalidContent_rejectedBeforeWriting() {
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    when(agentLoader.deriveProfile(eq("bad"), any()))
        .thenThrow(
            new IllegalArgumentException("Agent 'bad': missing required field 'provider.name'"));

    assertThatThrownBy(() -> lifecycle.update("bad", "---\n..."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider.name");

    verify(agentStore, never()).writeAgentMd(anyString(), anyString());
    verify(profileRegistry, never()).register(any());
    verify(agentScheduler, never()).registerProfile(any());
  }
}
