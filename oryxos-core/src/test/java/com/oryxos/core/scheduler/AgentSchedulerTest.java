package com.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import java.time.Instant;
import java.util.List;
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
 * 定时模块测试诀窍:别真等时间——runOnce 是独立方法,直接调它就能测全部行为逻辑; cron 触发本身是 Spring 的事,只验"注册参数传对了"。 一个类覆盖四个坑(时区 / 重叠 /
 * 失败隔离+锁释放 / 会话身份)。
 */
@DisplayName("AgentScheduler — 定时任务模块四个坑")
class AgentSchedulerTest {

  private static final String PROFILE_NAME = "weather";

  private final ProfileRegistry profileRegistry = Mockito.mock(ProfileRegistry.class);
  private final AgentService agentService = Mockito.mock(AgentService.class);
  private final SessionManager sessionManager = Mockito.mock(SessionManager.class);
  private final TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
  private final ScheduledTaskStore store = Mockito.mock(ScheduledTaskStore.class);

  private final AgentScheduler scheduler =
      new AgentScheduler(profileRegistry, agentService, sessionManager, taskScheduler, store);

  @BeforeEach
  void setUp() {
    // 默认启用,让既有测试不感知启停检查;需要测停用路径的测试自己覆盖
    when(store.isEnabled(anyString())).thenReturn(true);
    // 29 节起 registerProfile 捕获 taskScheduler.schedule(...) 的 ScheduledFuture 入句柄表;
    // mock 默认返回 null(ConcurrentHashMap 不接受 null),统一 stub 成一个非 null 句柄。
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
        .thenAnswer(inv -> Mockito.mock(java.util.concurrent.ScheduledFuture.class));
  }

  private Profile profileWith(ScheduleConfig... schedules) {
    Profile profile = new Profile();
    profile.setName(PROFILE_NAME);
    profile.setSchedules(List.of(schedules));
    return profile;
  }

  private ScheduleConfig scheduleConfig(String id) {
    return new ScheduleConfig(id, "0 0 9 * * *", "Asia/Shanghai", "到点了，干今天的活");
  }

  @Test
  @DisplayName("注册时 CronTrigger 带上了配置的 cron 和时区")
  void registerAll_registersCronTriggerWithCronAndZone() {
    Profile profile = profileWith(scheduleConfig("daily-weather"));
    when(profileRegistry.list()).thenReturn(List.of(profile));

    scheduler.registerAll();

    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    assertThat(trigger).isInstanceOf(CronTrigger.class);
    CronTrigger cronTrigger = (CronTrigger) trigger;
    // cron 字符串原样进触发参数
    assertThat(cronTrigger.getExpression()).isEqualTo("0 0 9 * * *");
    // 时区生效的行为验证:Asia/Shanghai 的 09:00 = UTC 01:00;
    // 若误用服务器系统时区(UTC)会算出 09:00Z,断言失败——不能靠 equals(CronTrigger 不比 zone)
    Instant base = Instant.parse("2026-08-12T00:00:00Z");
    Instant next = cronTrigger.nextExecution(new SimpleTriggerContext(base, base, base));
    assertThat(next).isEqualTo(Instant.parse("2026-08-12T01:00:00Z"));
  }

  @Test
  @DisplayName("会话三元组固定 (scheduler, scheduler, profileName)，两次触发拿到同一 Session")
  void runOnce_usesFixedSchedulerIdentity_sameSessionForBothTriggers() {
    Session session =
        new Session("scheduler+scheduler+" + PROFILE_NAME, PROFILE_NAME, "scheduler", "scheduler");
    when(sessionManager.getOrCreate("scheduler", "scheduler", PROFILE_NAME)).thenReturn(session);

    ScheduleConfig sc = scheduleConfig("daily-weather");
    scheduler.runOnce(profileWith(sc), sc);
    scheduler.runOnce(profileWith(sc), sc);

    // 两次触发都经同一三元组取会话,不用别的身份
    verify(sessionManager, times(2)).getOrCreate("scheduler", "scheduler", PROFILE_NAME);
    // 拿到的 Session 原样交给 AgentService,两次同一实例
    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    verify(agentService, times(2)).process(sessionCaptor.capture(), anyString());
    assertThat(sessionCaptor.getAllValues().get(0)).isSameAs(session);
    assertThat(sessionCaptor.getAllValues().get(1)).isSameAs(session);
  }

  @Test
  @DisplayName("上一次还没跑完，本次触发直接跳过")
  void skipsTrigger_whenPreviousRunStillHoldingLock() throws Exception {
    ScheduleConfig sc = scheduleConfig("task-1");
    Profile profile = profileWith(sc);
    when(sessionManager.getOrCreate(anyString(), anyString(), anyString()))
        .thenReturn(new Session());

    // 模拟"上一次还在跑":让另一个线程占着锁——ReentrantLock 可重入,同线程 lock 后
    // tryLock 会重入成功;真实场景里上一次跑在调度线程池上,新触发来自另一线程
    java.util.concurrent.CountDownLatch acquired = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              scheduler.lockFor("task-1").lock();
              acquired.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              scheduler.lockFor("task-1").unlock();
            });
    holder.start();
    acquired.await();

    try {
      scheduler.runOnce(profile, sc);
      verify(agentService, never()).process(any(), any()); // 没有叠加执行
    } finally {
      release.countDown();
      holder.join();
    }
  }

  @Test
  @DisplayName("任务抛异常，不外抛且锁必须被释放")
  void runOnceSurvivesException_andReleasesLock() {
    ScheduleConfig sc = scheduleConfig("task-1");
    Profile profile = profileWith(sc);
    Session session =
        new Session("scheduler+scheduler+" + PROFILE_NAME, PROFILE_NAME, "scheduler", "scheduler");
    when(sessionManager.getOrCreate("scheduler", "scheduler", PROFILE_NAME)).thenReturn(session);
    when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"));

    // 调度器不死:异常不外抛
    assertThatNoException().isThrownBy(() -> scheduler.runOnce(profile, sc));

    // 二进宫:再触发一次能进来——锁真的放了,没有永久卡死
    scheduler.runOnce(profile, sc);
    verify(agentService, times(2)).process(any(), any());
  }
}
