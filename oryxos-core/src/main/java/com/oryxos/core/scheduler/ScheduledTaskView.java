package com.oryxos.core.scheduler;

import java.time.Instant;

/**
 * 定时任务状态视图(给管理台展示,与 scheduled_tasks 表字段一一对应)。
 *
 * <p>字段含义见 TechnicalSolution §9.2 scheduled_tasks 实体表。
 */
public record ScheduledTaskView(
    String taskId,
    String profileName,
    String cron,
    String zone,
    String message,
    boolean enabled,
    Instant nextRunAt,
    Instant lastRunAt,
    String lastStatus,
    long runCount,
    Instant updatedAt) {}
