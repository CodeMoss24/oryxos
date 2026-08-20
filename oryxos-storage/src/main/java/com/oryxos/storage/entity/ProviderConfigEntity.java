package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * provider_configs 表:Provider 动态注册表(name 主键)。
 *
 * <p>时间字段使用 String(ISO-8601),与既有表同口径。启动时 application.yaml 播种缺失项,之后注册表是唯一事实源。
 */
@Entity
@Table(name = "provider_configs")
public class ProviderConfigEntity {

  @Id
  @Column(name = "name")
  private String name;

  @Column(name = "api_key")
  private String apiKey;

  @Column(name = "base_url")
  private String baseUrl;

  @Column(name = "description")
  private String description;

  @Column(name = "created_at")
  private String createdAt;

  @Column(name = "updated_at")
  private String updatedAt;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
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

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
