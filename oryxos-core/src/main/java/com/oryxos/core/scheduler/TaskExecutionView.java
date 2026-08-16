package com.oryxos.core.scheduler;

import java.time.Instant;

/**
 * 定时任务执行记录视图(管理台查询某任务的执行历史,与 task_executions 表字段一一对应)。
 *
 * <p>字段含义见 TechnicalSolution §9.2 task_executions 实体表。
 */
public record TaskExecutionView(
    long id,
    String taskId,
    String sessionId,
    Instant startedAt,
    boolean success,
    String errorMessage,
    long durationMs) {}
