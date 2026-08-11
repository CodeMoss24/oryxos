package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.storage.entity.MemoryEntryEntity;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SQLite 档:记忆按条入库到 memory_entries 表(手工建表脚本,与审计表同口径)。记忆量上千、要按 scope/时间结构化查询时的升级档,仍零外部依赖(复用已有
 * SQLite)。
 *
 * <p>契约落实:每次直插直查(契约一天然满足);核心区 WHERE scope='CORE' 全量取,LIMIT 只加在归档 查询上(契约二靠 SQL 结构保证);归档检索走
 * LIKE(契约四)。
 */
@Component
@ConditionalOnProperty(name = "oryxos.memory.backend", havingValue = "sqlite")
public class SqliteMemoryStore implements LongTermMemoryStore {

  private static final int MAX_ARCHIVE_ROWS = 100; // 归档区只带最近 N 条

  private final MemoryEntryRepository repository;

  public SqliteMemoryStore(MemoryEntryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    MemoryEntryEntity entry = new MemoryEntryEntity();
    entry.setScope(scope.name());
    entry.setContent(content);
    entry.setCreatedAt(Instant.now());
    repository.save(entry); // 每次直插——契约一
  }

  @Override
  public String load() {
    String core = render(repository.findByScopeOrderByIdDesc("CORE")); // 核心区:全量——契约二
    String archive = render(repository.findRecentArchival("ARCHIVAL")); // 归档区:LIMIT N——契约二
    String result = (core + "\n" + archive).trim();
    return result.isEmpty() ? "" : result;
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    return repository.searchArchival("%" + keyword + "%").stream()
        .map(MemoryEntryEntity::getContent)
        .toList(); // SQL LIKE——契约四
  }

  private String render(List<MemoryEntryEntity> entries) {
    return entries.stream()
        .map(e -> "- [" + e.getCreatedAt() + "] " + e.getContent())
        .collect(java.util.stream.Collectors.joining("\n"));
  }
}
