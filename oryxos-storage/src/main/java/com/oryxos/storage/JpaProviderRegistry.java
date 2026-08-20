package com.oryxos.storage;

import com.oryxos.core.provider.ProviderRegistry;
import com.oryxos.storage.entity.ProviderConfigEntity;
import com.oryxos.storage.repository.ProviderConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ProviderRegistry 的 JPA 实现(依赖倒置:契约在 core,实现在 storage)。
 *
 * <p>save 按 name upsert(存在则更新 api_key/base_url/description 与 updated_at,新条目补 created_at)。
 */
@Component
public class JpaProviderRegistry implements ProviderRegistry {

  private final ProviderConfigRepository repository;

  public JpaProviderRegistry(ProviderConfigRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<ProviderDef> list() {
    return repository.findAll().stream().map(JpaProviderRegistry::toDef).toList();
  }

  @Override
  public Optional<ProviderDef> find(String name) {
    return repository.findById(name).map(JpaProviderRegistry::toDef);
  }

  @Override
  public boolean exists(String name) {
    return repository.existsById(name);
  }

  @Override
  public ProviderDef save(ProviderDef def) {
    String now = Instant.now().toString();
    ProviderConfigEntity entity =
        repository.findById(def.name()).orElseGet(ProviderConfigEntity::new);
    entity.setName(def.name());
    entity.setApiKey(def.apiKey());
    entity.setBaseUrl(def.baseUrl());
    entity.setDescription(def.description());
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);
    return toDef(repository.save(entity));
  }

  @Override
  public void delete(String name) {
    repository.deleteById(name);
  }

  private static ProviderDef toDef(ProviderConfigEntity e) {
    return new ProviderDef(e.getName(), e.getApiKey(), e.getBaseUrl(), e.getDescription());
  }
}
