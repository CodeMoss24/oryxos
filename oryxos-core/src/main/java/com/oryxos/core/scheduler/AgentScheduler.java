package com.oryxos.core.scheduler;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 第三种触发源(钟推)。基于 Spring 的 TaskScheduler + CronTrigger 动态注册任务, 不用静态的 @Scheduled 注解(触发规则要按 Profile
 * 配置动态生成,编译期写死的注解做不到)。
 *
 * <p>到点触发时:拿锁、经 SessionManager 取会话、把消息交给 AgentService—— 与 CLI/Web 完全相同的入口,ReActLoop 不感知消息从哪个入口来。
 *
 * <p>并发控制:每个定时任务用一把进程内的 ReentrantLock(按任务 id 维度)防止同一任务重叠执行——上一次还没跑完,下一次触发点到了就跳过,
 * 不排队、不并行跑两份。核心阶段是单实例部署,这把锁只解决"同一进程内不重叠", 不是分布式锁(多实例协调放扩展阶段)。
 *
 * <p>失败处理:一次触发失败只记日志,不让调度器崩溃、不影响后续触发; 失败的调用依然走 agentService.process 内部完整的审计路径,跟人推触发没有区别。
 *
 * <p>会话身份:钟推也要落 Session,channel 和 user 都固定为 "scheduler"—— 同一个 Profile 的历次定时触发复用同一个 Session,历史靠
 * max_history_turns 截断兜底。 session_id 的拼接只发生在 SessionManager 内部,这里不手拼。
 */
@Component
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);
  private static final String SCHEDULER_CHANNEL = "scheduler";
  private static final String SCHEDULER_USER = "scheduler";

  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final TaskScheduler taskScheduler;

  private final Map<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

  public AgentScheduler(
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager,
      TaskScheduler taskScheduler) {
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.taskScheduler = taskScheduler;
  }

  /** 启动时扫一遍所有 Profile,把每条 schedules 配置都注册进 taskScheduler。 */
  @PostConstruct
  public void registerAll() {
    for (Profile profile : profileRegistry.list()) {
      for (ScheduleConfig sc : profile.getSchedules()) {
        try {
          // cron + 时区一起传,别让服务器时区替用户做主
          taskScheduler.schedule(
              () -> runOnce(profile, sc), new CronTrigger(sc.cron(), sc.zoneId()));
          log.info(
              "Registered schedule {} for agent {}: cron={} zone={}",
              sc.id(),
              profile.getName(),
              sc.cron(),
              sc.zone());
        } catch (IllegalArgumentException e) {
          // 非法 cron 只记错误不阻断启动,其余任务照常注册
          log.error(
              "Invalid cron for schedule {} of agent {}: {}",
              sc.id(),
              profile.getName(),
              sc.cron(),
              e);
        }
      }
    }
  }

  /** 到点触发一次:拿锁、取会话、交给 AgentService。包级可见供测试直接调用,不真等时间。 */
  void runOnce(Profile profile, ScheduleConfig sc) {
    ReentrantLock lock = taskLocks.computeIfAbsent(sc.id(), id -> new ReentrantLock());
    if (!lock.tryLock()) {
      log.info("Task {} still running, skip this trigger", sc.id());
      return;
    }
    try {
      Session session =
          sessionManager.getOrCreate(SCHEDULER_CHANNEL, SCHEDULER_USER, profile.getName());
      agentService.process(session, sc.message());
    } catch (Exception e) {
      log.error("Scheduled task {} failed", sc.id(), e);
    } finally {
      lock.unlock();
    }
  }

  /** 按任务 id 拿锁,包级可见供测试模拟"上一次还占着锁"。 */
  Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }
}
