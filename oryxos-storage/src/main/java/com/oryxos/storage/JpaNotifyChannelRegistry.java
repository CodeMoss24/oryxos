package com.oryxos.storage;

import com.oryxos.core.notify.NotifyChannelDef;
import com.oryxos.core.notify.NotifyChannelRegistry;
import com.oryxos.storage.entity.NotifyChannelEntity;
import com.oryxos.storage.repository.NotifyChannelRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link NotifyChannelRegistry} 的 SQLite/JPA 实现:notify_channels 表 ↔ {@link NotifyChannelDef} 互转,按
 * name 主键 upsert。
 */
@Component
public class JpaNotifyChannelRegistry implements NotifyChannelRegistry {

  private final NotifyChannelRepository repository;

  public JpaNotifyChannelRegistry(NotifyChannelRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<NotifyChannelDef> list() {
    return repository.findAll().stream().map(JpaNotifyChannelRegistry::toDef).toList();
  }

  @Override
  public Optional<NotifyChannelDef> find(String name) {
    return repository.findById(name).map(JpaNotifyChannelRegistry::toDef);
  }

  @Override
  public boolean exists(String name) {
    return repository.existsById(name);
  }

  @Override
  public NotifyChannelDef save(NotifyChannelDef channel) {
    NotifyChannelEntity entity =
        repository.findById(channel.name()).orElseGet(NotifyChannelEntity::new);
    entity.setName(channel.name());
    entity.setType(channel.type());
    entity.setUrl(channel.url());
    entity.setDescription(channel.description());
    return toDef(repository.save(entity));
  }

  @Override
  public void delete(String name) {
    repository.deleteById(name);
  }

  private static NotifyChannelDef toDef(NotifyChannelEntity e) {
    return new NotifyChannelDef(e.getName(), e.getType(), e.getUrl(), e.getDescription());
  }
}
