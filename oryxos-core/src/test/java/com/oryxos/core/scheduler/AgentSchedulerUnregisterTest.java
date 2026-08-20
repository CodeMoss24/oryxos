package com.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * 第 30 节 harness:AgentScheduler.unregisterProfile。
 *
 * <p>注销后句柄表/防重索引清空(cancel(false) 不中断执行中的触发)、幂等(重复注销无害)、 注销后再 registerProfile 能重新调度(update 的
 * schedules 变更前提:scheduledTaskIds 必须同步清)。
 */
@DisplayName("AgentScheduler — unregisterProfile(第 30 节)")
class AgentSchedulerUnregisterTest {

  private final ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
  private final AgentService agentService = mock(AgentService.class);
  private final SessionManager sessionManager = mock(SessionManager.class);
  private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
  private final ScheduledTaskStore store = mock(ScheduledTaskStore.class);

  @SuppressWarnings("unchecked")
  private final java.util.concurrent.ScheduledFuture<?> future =
      mock(java.util.concurrent.ScheduledFuture.class);

  private final AgentScheduler scheduler =
      new AgentScheduler(profileRegistry, agentService, sessionManager, taskScheduler, store);

  @BeforeEach
  void setUp() {
    when(store.isEnabled(any())).thenReturn(true);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(inv -> future);
  }

  private Profile profileWith(ScheduleConfig... schedules) {
    Profile profile = new Profile();
    profile.setName("reconcile");
    profile.setSchedules(List.of(schedules));
    return profile;
  }

  private ScheduleConfig sc(String id) {
    return new ScheduleConfig(id, "0 0 9 * * *", "Asia/Shanghai", "到点了");
  }

  @Test
  @DisplayName("unregisterProfile 后句柄表清空,句柄被 cancel(false) 取消")
  void unregisterProfile_removesHandleAndCancelsFuture() {
    Profile profile = profileWith(sc("reconcile-morning"));
    scheduler.registerProfile(profile);

    scheduler.unregisterProfile(profile);

    assertThat((Object) scheduler.scheduledFutureFor("reconcile-morning")).isNull();
    verify(future).cancel(false); // 不中断正在跑的那次触发
  }

  @Test
  @DisplayName("注销后再 registerProfile 能重新调度(update schedules 变更的前提)")
  void unregister_thenRegister_reschedules() {
    Profile profile = profileWith(sc("reconcile-morning"));
    scheduler.registerProfile(profile);
    scheduler.unregisterProfile(profile);

    scheduler.registerProfile(profile);

    // 幂等防重索引已清,重新注册不会被打在"已调度"上跳过
    Object handle = scheduler.scheduledFutureFor("reconcile-morning");
    assertThat(handle).isNotNull();
    assertThat(handle).isSameAs(future);
  }

  @Test
  @DisplayName("未注册过的任务注销静默跳过,不抛异常(幂等)")
  void unregisterProfile_idempotentOnUnknownTask() {
    Profile profile = profileWith(sc("never-registered"));

    scheduler.unregisterProfile(profile); // 不抛

    assertThat((Object) scheduler.scheduledFutureFor("never-registered")).isNull();
    verify(future, never()).cancel(anyBoolean());
  }

  @Test
  @DisplayName("无 schedules 的 Profile 注销为空操作")
  void unregisterProfile_emptySchedulesNoOp() {
    Profile profile = profileWith();

    scheduler.unregisterProfile(profile); // 不抛

    verify(future, never()).cancel(anyBoolean());
  }

  @Test
  @DisplayName("多个 schedule 的 Profile 全部注销")
  void unregisterProfile_unregistersAllRegistered() {
    Profile profile = profileWith(sc("a"), sc("b"));
    scheduler.registerProfile(profile);

    scheduler.unregisterProfile(profile);

    assertThat((Object) scheduler.scheduledFutureFor("a")).isNull();
    assertThat((Object) scheduler.scheduledFutureFor("b")).isNull();
    verify(future, org.mockito.Mockito.times(2)).cancel(false);
  }
}
