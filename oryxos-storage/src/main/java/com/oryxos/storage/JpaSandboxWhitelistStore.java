package com.oryxos.storage;

import com.oryxos.core.sandbox.SandboxWhitelistPort.Category;
import com.oryxos.core.sandbox.SandboxWhitelistStore;
import com.oryxos.storage.entity.SandboxWhitelistEntryEntity;
import com.oryxos.storage.repository.SandboxWhitelistEntryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link SandboxWhitelistStore} 的 SQLite/JPA 实现:sandbox_whitelist_entries 表 ↔ {@link Entry} 互转。 */
@Component
public class JpaSandboxWhitelistStore implements SandboxWhitelistStore {

  private final SandboxWhitelistEntryRepository repository;

  public JpaSandboxWhitelistStore(SandboxWhitelistEntryRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Entry> loadAll() {
    return repository.findAll().stream()
        .map(r -> new Entry(Category.valueOf(r.getCategory()), r.getEntryValue()))
        .toList();
  }

  @Override
  public boolean add(Category category, String value) {
    if (repository.existsByCategoryAndEntryValue(category.name(), value)) {
      return false;
    }
    SandboxWhitelistEntryEntity entity = new SandboxWhitelistEntryEntity();
    entity.setCategory(category.name());
    entity.setEntryValue(value);
    entity.setCreatedAt(Instant.now().toString());
    repository.save(entity);
    return true;
  }

  @Override
  public boolean remove(Category category, String value) {
    List<SandboxWhitelistEntryEntity> rows =
        repository.findByCategoryAndEntryValue(category.name(), value);
    if (rows.isEmpty()) {
      return false;
    }
    repository.deleteAll(rows);
    return true;
  }
}
