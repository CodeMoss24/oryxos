package com.oryxos.storage;

import com.oryxos.core.scheduler.ScheduleConfig;
import com.oryxos.core.scheduler.ScheduledTaskStore;
import com.oryxos.core.scheduler.ScheduledTaskView;
import com.oryxos.core.scheduler.TaskExecutionView;
import com.oryxos.storage.entity.ScheduledTaskEntity;
import com.oryxos.storage.entity.TaskExecutionEntity;
import com.oryxos.storage.repository.ScheduledTaskRepository;
import com.oryxos.storage.repository.TaskExecutionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ScheduledTaskStore 的 JPA 实现(第 28 节,依赖倒置:契约在 core,实现在 storage)。
 *
 * <p>时间字段存储为 String(ISO-8601 文本),与 sessions/llm_calls/tool_invocations 三表同口径, 避免 SQLite JDBC 对
 * Instant 序列化的日期解析错误。register 做 upsert:存在则更新 cron/zone/message,不存在则 INSERT 且 enabled 默认 true。
 */
@Component
public class JpaScheduledTaskStore implements ScheduledTaskStore {

  private final ScheduledTaskRepository taskRepo;
  private final TaskExecutionRepository executionRepo;

  public JpaScheduledTaskStore(
      ScheduledTaskRepository taskRepo, TaskExecutionRepository executionRepo) {
    this.taskRepo = taskRepo;
    this.executionRepo = executionRepo;
  }

  @Override
  public void register(ScheduleConfig config, String profileName, Instant nextRunAt) {
    Optional<ScheduledTaskEntity> existing = taskRepo.findById(config.id());
    ScheduledTaskEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      entity.setCron(config.cron());
      entity.setZone(config.zone());
      entity.setMessage(config.message());
    } else {
      entity = new ScheduledTaskEntity();
      entity.setTaskId(config.id());
      entity.setProfileName(profileName);
      entity.setCron(config.cron());
      entity.setZone(config.zone());
      entity.setMessage(config.message());
      entity.setEnabled(true);
      entity.setRunCount(0L);
    }
    entity.setNextRunAt(nextRunAt != null ? nextRunAt.toString() : null);
    entity.setUpdatedAt(Instant.now().toString());
    taskRepo.save(entity);
  }

  @Override
  public void recordExecution(
      String taskId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs,
      Instant nextRunAt) {
    // ① INSERT task_executions(成功失败都记)
    TaskExecutionEntity exec = new TaskExecutionEntity();
    exec.setTaskId(taskId);
    exec.setSessionId(sessionId);
    exec.setStartedAt(startedAt != null ? startedAt.toString() : null);
    exec.setSuccess(success);
    exec.setErrorMessage(errorMessage);
    exec.setDurationMs(durationMs);
    executionRepo.save(exec);

    // ② UPDATE scheduled_tasks 运行状态
    taskRepo
        .findById(taskId)
        .ifPresent(
            entity -> {
              entity.setLastRunAt(startedAt != null ? startedAt.toString() : null);
              entity.setLastStatus(success ? "success" : "failed");
              entity.setRunCount(entity.getRunCount() + 1);
              entity.setNextRunAt(nextRunAt != null ? nextRunAt.toString() : null);
              entity.setUpdatedAt(Instant.now().toString());
              taskRepo.save(entity);
            });
  }

  @Override
  public void setEnabled(String taskId, boolean enabled) {
    taskRepo
        .findById(taskId)
        .ifPresent(
            entity -> {
              entity.setEnabled(enabled);
              entity.setUpdatedAt(Instant.now().toString());
              taskRepo.save(entity);
            });
  }

  @Override
  public boolean isEnabled(String taskId) {
    return taskRepo.findById(taskId).map(ScheduledTaskEntity::getEnabled).orElse(true);
  }

  @Override
  public List<ScheduledTaskView> listAll() {
    return taskRepo.findAll().stream().map(JpaScheduledTaskStore::toView).toList();
  }

  @Override
  public List<TaskExecutionView> executions(String taskId) {
    return executionRepo.findByTaskIdOrderByStartedAtDesc(taskId).stream()
        .map(JpaScheduledTaskStore::toView)
        .toList();
  }

  private static ScheduledTaskView toView(ScheduledTaskEntity e) {
    return new ScheduledTaskView(
        e.getTaskId(),
        e.getProfileName(),
        e.getCron(),
        e.getZone(),
        e.getMessage(),
        Boolean.TRUE.equals(e.getEnabled()),
        parseInstant(e.getNextRunAt()),
        parseInstant(e.getLastRunAt()),
        e.getLastStatus(),
        e.getRunCount() != null ? e.getRunCount() : 0L,
        parseInstant(e.getUpdatedAt()));
  }

  private static TaskExecutionView toView(TaskExecutionEntity e) {
    return new TaskExecutionView(
        e.getId() != null ? e.getId() : 0L,
        e.getTaskId(),
        e.getSessionId(),
        parseInstant(e.getStartedAt()),
        Boolean.TRUE.equals(e.getSuccess()),
        e.getErrorMessage(),
        e.getDurationMs() != null ? e.getDurationMs() : 0L);
  }

  private static Instant parseInstant(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(s);
    } catch (Exception ex) {
      return null;
    }
  }
}
