package com.oryxos.core.provider;

import java.util.List;
import java.util.Optional;

/**
 * Provider 动态注册表(core 端口,实现在 oryxos-storage):管理台 CRUD 管、运行时按名动态建 ChatModel。
 *
 * <p>与 {@code ScheduledTaskStore} 同模式:core 只认这个契约,存储实现(JPA)放 storage 模块。 启动时 application.yaml 的
 * oryxos.providers 播种缺失项(seedMissing),之后注册表是唯一事实源。
 */
public interface ProviderRegistry {

  List<ProviderDef> list();

  Optional<ProviderDef> find(String name);

  boolean exists(String name);

  /** 按 name upsert;返回保存后的定义。 */
  ProviderDef save(ProviderDef def);

  void delete(String name);

  /** Provider 定义值对象(name 唯一,apiKey 不掩码——掩码是 web 展示层的事)。 */
  record ProviderDef(String name, String apiKey, String baseUrl, String description) {}
}
