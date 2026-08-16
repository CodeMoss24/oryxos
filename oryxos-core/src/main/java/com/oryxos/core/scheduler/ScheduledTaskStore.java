package com.oryxos.core.scheduler;

import java.time.Instant;

/**
 * 定时任务持久化契约(第 28 节,依赖倒置:core 定义契约,storage 做 JPA 实现)。
 *
 * <p>方法语义:
 *
 * <ol>
 *   <li>{@code register}:任务注册(upsert——存在则更新定义字段,不存在则插入,enabled 默认 true)
 *   <li>{@code recordExecution}:执行落库——INSERT task_executions + UPDATE 任务运行状态四字段
 *   <li>{@code setEnabled / isEnabled}:管理台启用/停用开关
 *   <li>{@code listAll / executions}:只读查询
 * </ol>
 *
 * <p>注意:定义来源仍在 Skill/Profile 的 schedules 字段——本接口只存"状态+历史",不作为定义源。
 */
public interface ScheduledTaskStore {

  /**
   * 注册/更新任务定义(id 为键,存在则覆盖 cron/zone/message)。
   *
   * @param config 来自 Profile schedules 的原始配置
   * @param profileName 归属 Agent
   * @param nextRunAt 根据 cron 算出的下一次触发时刻
   */
  void register(ScheduleConfig config, String profileName, Instant nextRunAt);

  /**
   * 记录一次执行完成(成功或失败)。两件事一起做:
   *
   * <ol>
   *   <li>INSERT 一条 task_executions
   *   <li>UPDATE scheduled_tasks 的 last_run_at / last_status / run_count / next_run_at
   * </ol>
   */
  void recordExecution(
      String taskId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs,
      Instant nextRunAt);

  /** 启用/停用任务。 */
  void setEnabled(String taskId, boolean enabled);

  /** 是否启用(tryLock 前先看这里,停用则跳过)。 */
  boolean isEnabled(String taskId);

  /** 列出全部任务状态(管理台列表端点用)。 */
  java.util.List<ScheduledTaskView> listAll();

  /** 列出某任务的执行历史(按 startedAt 倒序)。 */
  java.util.List<TaskExecutionView> executions(String taskId);
}
