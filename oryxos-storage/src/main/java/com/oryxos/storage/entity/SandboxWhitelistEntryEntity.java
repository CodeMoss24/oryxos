package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * sandbox_whitelist_entries 表:Sandbox 白名单的持久化记录,运行时增删写穿到库、重启恢复。
 *
 * <p>(category, entry_value) 唯一;entry_value 存"入内存的规范形"(FILE 为归一后的绝对路径),由 {@code WhitelistSandbox}
 * 写穿前算好,使管理台展示 / 删除与库内值对齐。时间字段使用 String(ISO-8601), 与 sessions/llm_calls/tool_invocations 三表同口径。
 */
@Entity
@Table(name = "sandbox_whitelist_entries")
public class SandboxWhitelistEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "category", nullable = false)
  private String category; // FILE / SHELL / HTTP

  @Column(name = "entry_value", nullable = false)
  private String entryValue;

  @Column(name = "created_at")
  private String createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getEntryValue() {
    return entryValue;
  }

  public void setEntryValue(String entryValue) {
    this.entryValue = entryValue;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
