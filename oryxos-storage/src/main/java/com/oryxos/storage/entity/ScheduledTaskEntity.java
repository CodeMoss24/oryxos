package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * scheduled_tasks 表:定时任务登记与状态。第 28 节补齐。
 *
 * <p>时间字段使用 String(ISO-8601),与 sessions/llm_calls/tool_invocations 三表同口径, 避免 SQLite JDBC 对 Instant
 * 序列化的日期解析错误。
 */
@Entity
@Table(name = "scheduled_tasks")
public class ScheduledTaskEntity {

  @Id
  @Column(name = "task_id")
  private String taskId;

  @Column(name = "profile_name")
  private String profileName;

  @Column(name = "cron")
  private String cron;

  @Column(name = "zone")
  private String zone;

  @Column(name = "message")
  private String message;

  @Column(name = "enabled")
  private Boolean enabled;

  @Column(name = "next_run_at")
  private String nextRunAt;

  @Column(name = "last_run_at")
  private String lastRunAt;

  @Column(name = "last_status")
  private String lastStatus;

  @Column(name = "run_count")
  private Long runCount;

  @Column(name = "updated_at")
  private String updatedAt;

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(String nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public String getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(String lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public String getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(String lastStatus) {
    this.lastStatus = lastStatus;
  }

  public Long getRunCount() {
    return runCount;
  }

  public void setRunCount(Long runCount) {
    this.runCount = runCount;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
