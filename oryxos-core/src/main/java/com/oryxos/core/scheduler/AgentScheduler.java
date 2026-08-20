package com.oryxos.core.scheduler;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 第三种触发源(钟推)。基于 Spring 的 TaskScheduler + CronTrigger 动态注册任务, 不用静态的 @Scheduled 注解(触发规则要按 Profile
 * 配置动态生成,编译期写死的注解做不到)。
 *
 * <p>第 28 节升级:持久化接 ScheduledTaskStore(状态+历史落 SQLite,重启不丢)、执行入口加启用检查、成功失败都记 task_executions、新增
 * runNow(管理台"立即执行")。
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
  private final ScheduledTaskStore store;

  private final Map<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

  /** taskId → (Profile, ScheduleConfig) 映射,供 runNow 按 id 反查。 */
  private final Map<String, ProfileTaskRef> taskRefs = new ConcurrentHashMap<>();

  /** 已注册到 taskScheduler 的 taskId 集合,防重复调度。 */
  private final java.util.Set<String> scheduledTaskIds = ConcurrentHashMap.newKeySet();

  /**
   * 第 29 节:taskId → 调度句柄表,捕获 taskScheduler.schedule(...) 返回的 ScheduledFuture。 与 taskLocks/taskRefs
   * 并存, 供下节(30)注销/更新定时任务时 future.cancel() + 移出索引,免重启。 启动扫描与运行时注册都经 registerProfile 填这张表(同一段代码)。
   */
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  public AgentScheduler(
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager,
      TaskScheduler taskScheduler,
      ScheduledTaskStore store) {
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.taskScheduler = taskScheduler;
    this.store = store;
  }

  /** 启动时扫一遍所有 Profile,逐个调 registerProfile 注册(同一段代码,运行时新增也走它)。 */
  public void registerAll() {
    for (Profile profile : profileRegistry.list()) {
      registerProfile(profile);
    }
  }

  /**
   * 注册单个 Profile 的全部 schedules:幂等检查 → CronTrigger schedule → 落 store → 填 taskRefs + scheduledTasks
   * 句柄表。
   *
   * <p>第 29 节从 registerAll 循环体抽出,让"启动扫描"和"运行时新增 Agent"走同一段注册代码(下节 API 建完 Agent 目录后直接调它,免重启)。 句柄存入
   * {@link #scheduledTasks},供下节注销/更新。
   */
  public void registerProfile(Profile profile) {
    for (ScheduleConfig sc : profile.getSchedules()) {
      try {
        // 幂等:已调度过的任务不重复注册(防 @PostConstruct + 后续重新扫描重复调度)
        if (!scheduledTaskIds.add(sc.id())) {
          continue;
        }
        ZoneId zoneId = sc.zoneId();
        CronExpression cronExpr = CronExpression.parse(sc.cron());
        Instant nextRunAt = cronExpr.next(java.time.ZonedDateTime.now(zoneId)).toInstant();
        // cron + 时区一起传,别让服务器时区替用户做主;捕获句柄入表(下节注销用)
        ScheduledFuture<?> future =
            taskScheduler.schedule(() -> runOnce(profile, sc), new CronTrigger(sc.cron(), zoneId));
        scheduledTasks.put(sc.id(), future);
        store.register(sc, profile.getName(), nextRunAt);
        taskRefs.put(sc.id(), new ProfileTaskRef(profile, sc));
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

  /** 到点触发一次:启停检查 → 拿锁 → 取会话 → 交给 AgentService → 记录执行。包级可见供测试直接调用。 */
  void runOnce(Profile profile, ScheduleConfig sc) {
    if (!store.isEnabled(sc.id())) {
      log.info("Task {} is disabled, skip this trigger", sc.id());
      return;
    }
    ReentrantLock lock = taskLocks.computeIfAbsent(sc.id(), id -> new ReentrantLock());
    if (!lock.tryLock()) {
      log.info("Task {} still running, skip this trigger", sc.id());
      return;
    }
    Instant startedAt = Instant.now();
    String sessionId = null;
    boolean success = false;
    String errorMessage = null;
    try {
      Session session =
          sessionManager.getOrCreate(SCHEDULER_CHANNEL, SCHEDULER_USER, profile.getName());
      sessionId = session.getSessionId();
      agentService.process(session, sc.message());
      success = true;
    } catch (Exception e) {
      errorMessage = e.getMessage();
      log.error("Scheduled task {} failed", sc.id(), e);
    } finally {
      lock.unlock();
      // 审计:成功失败都落 task_executions(与宪法 V 审计同理)
      long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
      Instant nextRunAt = computeNext(sc);
      store.recordExecution(
          sc.id(), sessionId, startedAt, success, errorMessage, durationMs, nextRunAt);
    }
  }

  /** 按 taskId 立即执行一次(管理台"手动触发"),不等 cron、无视启用状态。 */
  public void runNow(String taskId) {
    ProfileTaskRef ref = taskRefs.get(taskId);
    if (ref == null) {
      throw new IllegalArgumentException("No such scheduled task: " + taskId);
    }
    log.info("Manual trigger: taskId={} profile={}", taskId, ref.profile.getName());
    runOnce(ref.profile, ref.config);
  }

  /** 启用/停用任务(管理台开关)。 */
  public void setEnabled(String taskId, boolean enabled) {
    store.setEnabled(taskId, enabled);
    log.info("Task {} enabled={}", taskId, enabled);
  }

  /** 列出全部任务状态(管理台列表)。 */
  public java.util.List<ScheduledTaskView> listAll() {
    return store.listAll();
  }

  /** 列出某任务的执行历史(按时间倒序)。 */
  public java.util.List<TaskExecutionView> executions(String taskId) {
    return store.executions(taskId);
  }

  /** 按任务 id 拿锁,包级可见供测试模拟"上一次还占着锁"。 */
  Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }

  /** 按任务 id 取调度句柄,供测试断言句柄表(第 29 节 registerProfile 后应有句柄)。 */
  public ScheduledFuture<?> scheduledFutureFor(String taskId) {
    return scheduledTasks.get(taskId);
  }

  /**
   * 注销一个 Profile 的全部定时任务(第 30 节:删除/更新/归档 Agent 的前置动作)。
   *
   * <p>对每条 schedule:从 {@link #scheduledTasks} 取句柄调 cancel(false)(不中断正在跑的那次触发), 并从
   * scheduledTasks/taskRefs/scheduledTaskIds 三个索引移除——scheduledTaskIds 必须同步清,否则 update 后重新
   * registerProfile 会被幂等检查挡住、新 cron 不生效。不动 taskLocks(课件口径:锁条目可复用,防重叠语义不变)。
   *
   * <p>schedules 为空或任务未注册时静默跳过(幂等,重复注销无害)。
   */
  public void unregisterProfile(Profile profile) {
    for (ScheduleConfig sc : profile.getSchedules()) {
      ScheduledFuture<?> future = scheduledTasks.get(sc.id());
      if (future == null) {
        continue; // 未注册过(如非法 cron 注册失败的),无需注销
      }
      future.cancel(false);
      scheduledTasks.remove(sc.id());
      taskRefs.remove(sc.id());
      scheduledTaskIds.remove(sc.id());
      log.info("Unregistered schedule {} for agent {}", sc.id(), profile.getName());
    }
  }

  /** 按 cron 表达式算下次触发时刻。 */
  private static Instant computeNext(ScheduleConfig sc) {
    try {
      return CronExpression.parse(sc.cron())
          .next(java.time.ZonedDateTime.now(sc.zoneId()))
          .toInstant();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record ProfileTaskRef(Profile profile, ScheduleConfig config) {}
}
