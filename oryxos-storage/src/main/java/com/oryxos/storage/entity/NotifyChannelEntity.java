package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * notify_channels 表:通知渠道注册表的持久化记录,管理台 CRUD 落库、Agent 按名引用。
 *
 * <p>name 为主键(Agent 按名引用,改名即删旧建新)。时间字段使用 String(ISO-8601),与 sessions/llm_calls/tool_invocations
 * 三表同口径。
 */
@Entity
@Table(name = "notify_channels")
public class NotifyChannelEntity {

  @Id
  @Column(name = "name")
  private String name;

  @Column(name = "type", nullable = false)
  private String type; // webhook / feishu / wecom / dingtalk

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "description")
  private String description;

  @Column(name = "created_at")
  private String createdAt;

  @Column(name = "updated_at")
  private String updatedAt;

  @PrePersist
  void onCreate() {
    String now = java.time.Instant.now().toString();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = java.time.Instant.now().toString();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
