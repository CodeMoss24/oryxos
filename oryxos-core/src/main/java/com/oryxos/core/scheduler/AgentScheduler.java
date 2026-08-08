package com.oryxos.core.scheduler;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.Profile.Schedule;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 第三种触发源(钟推)。基于 Spring 的 ThreadPoolTaskScheduler + CronTrigger 动态注册任务, 不用静态的 @Scheduled 注解(触发规则要按
 * Profile 配置动态生成)。
 *
 * <p>并发控制:每个定时任务用一把进程内的 ReentrantLock(按任务 id 维度)防止同一任务重叠执行。 核心阶段是单实例部署,这把锁只解决"同一进程内不重叠",不是分布式锁。
 *
 * <p>会话身份:钟推也要落 Session,channel 和 user 都固定为 "scheduler", 同一个 Profile 的历次定时触发复用同一个 Session。
 */
@Component
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);
  private static final String SCHEDULER_CHANNEL = "scheduler";
  private static final String SCHEDULER_USER = "scheduler";

  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final TaskScheduler taskScheduler;

  private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  public AgentScheduler(ProfileRegistry profileRegistry, AgentService agentService) {
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("oryxos-sched-");
    scheduler.initialize();
    this.taskScheduler = scheduler;
  }

  @PostConstruct
  public void registerAll() {
    for (Profile profile : profileRegistry.list()) {
      registerProfile(profile);
    }
  }

  public void registerProfile(Profile profile) {
    if (profile.getSchedules() == null) return;
    for (Schedule schedule : profile.getSchedules()) {
      registerOne(profile.getName(), schedule);
    }
  }

  public void unregisterProfile(String profileName) {
    ScheduledFuture<?> future = tasks.remove(profileName);
    if (future != null) {
      future.cancel(false);
    }
    locks.remove(profileName);
  }

  private void registerOne(String profileName, Schedule schedule) {
    ZoneId zone = schedule.zone() != null ? ZoneId.of(schedule.zone()) : ZoneId.systemDefault();
    CronTrigger trigger = new CronTrigger(schedule.cron(), zone);
    ReentrantLock lock = locks.computeIfAbsent(profileName, k -> new ReentrantLock());
    ScheduledFuture<?> future =
        taskScheduler.schedule(() -> runOnce(profileName, schedule.message(), lock), trigger);
    tasks.put(profileName, future);
    log.info("Registered schedule for {}: {}", profileName, schedule.cron());
  }

  private void runOnce(String profileName, String message, ReentrantLock lock) {
    if (!lock.tryLock()) {
      log.info("Skip overlapping schedule for {}", profileName);
      return;
    }
    try {
      String sessionId = SCHEDULER_CHANNEL + "+" + SCHEDULER_USER + "+" + profileName;
      Session session = new Session(sessionId, profileName, SCHEDULER_CHANNEL, SCHEDULER_USER);
      agentService.process(session, message);
    } catch (Exception e) {
      log.error("Scheduled task failed for {}: {}", profileName, e.getMessage(), e);
    } finally {
      lock.unlock();
    }
  }

  @PreDestroy
  public void shutdown() {
    for (ScheduledFuture<?> future : tasks.values()) {
      future.cancel(false);
    }
    tasks.clear();
    locks.clear();
  }
}
