package com.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * 第 29 节 harness:AgentScheduler.registerProfile + 句柄表。 registerProfile 后 scheduledTasks 句柄表有该
 * schedule id 的 ScheduledFuture;cron/时区来自 Profile.schedules;幂等(重复注册不重复调度)。
 */
@DisplayName("AgentScheduler — registerProfile + 句柄表")
class AgentSchedulerRegisterTest {

  private final ProfileRegistry profileRegistry = Mockito.mock(ProfileRegistry.class);
  private final AgentService agentService = Mockito.mock(AgentService.class);
  private final SessionManager sessionManager = Mockito.mock(SessionManager.class);
  private final TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
  private final ScheduledTaskStore store = Mockito.mock(ScheduledTaskStore.class);

  @SuppressWarnings("unchecked")
  private final ScheduledFuture<?> future = Mockito.mock(ScheduledFuture.class);

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
  @DisplayName("registerProfile 后句柄表有该 schedule id 的 ScheduledFuture")
  void registerProfile_leavesHandleInScheduledTasks() {
    Profile profile = profileWith(sc("reconcile-morning"));

    scheduler.registerProfile(profile);

    // ScheduledFuture 也是 Future,直接 assertThat 会与 assertThat(Future) 歧义;断言非空并强转 Object 比对
    Object handle = scheduler.scheduledFutureFor("reconcile-morning");
    assertThat(handle).isNotNull();
    assertThat(handle).isSameAs(future);
  }

  @Test
  @DisplayName("registerProfile 用 Profile.schedules 的 cron 与时区注册 CronTrigger")
  void registerProfile_usesCronAndZoneFromProfileSchedules() {
    Profile profile = profileWith(sc("reconcile-morning"));

    scheduler.registerProfile(profile);

    ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(CronTrigger.class);
    CronTrigger ct = (CronTrigger) captor.getValue();
    assertThat(ct.getExpression()).isEqualTo("0 0 9 * * *");
    // Asia/Shanghai 09:00 = UTC 01:00(误用系统时区会算成 09:00Z,断言失败)
    java.time.Instant base = java.time.Instant.parse("2026-08-12T00:00:00Z");
    java.time.Instant next = ct.nextExecution(new SimpleTriggerContext(base, base, base));
    assertThat(next).isEqualTo(java.time.Instant.parse("2026-08-12T01:00:00Z"));
  }

  @Test
  @DisplayName("registerProfile 幂等:重复注册同一 schedule id 不重复调度")
  void registerProfile_isIdempotent() {
    Profile profile = profileWith(sc("reconcile-morning"));

    scheduler.registerProfile(profile);
    scheduler.registerProfile(profile);

    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
  }

  @Test
  @DisplayName("registerProfile 多条 schedules 全部留句柄")
  void registerProfile_multipleSchedulesAllHaveHandles() {
    Profile profile = profileWith(sc("morning"), sc("evening"));

    scheduler.registerProfile(profile);

    Object morning = scheduler.scheduledFutureFor("morning");
    Object evening = scheduler.scheduledFutureFor("evening");
    assertThat(morning).isSameAs(future);
    assertThat(evening).isSameAs(future);
  }

  @Test
  @DisplayName("registerAll 经 registerProfile 注册(同一段代码)")
  void registerAll_delegatesToRegisterProfile() {
    Profile profile = profileWith(sc("reconcile-morning"));
    when(profileRegistry.list()).thenReturn(List.of(profile));

    scheduler.registerAll();

    Object handle = scheduler.scheduledFutureFor("reconcile-morning");
    assertThat(handle).isNotNull();
    assertThat(handle).isSameAs(future);
  }
}
