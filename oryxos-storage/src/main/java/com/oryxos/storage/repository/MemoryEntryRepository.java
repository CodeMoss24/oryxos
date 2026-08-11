package com.oryxos.storage.repository;

import com.oryxos.storage.entity.MemoryEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** memory_entries 表仓库:核心区全量取 / 归档区 LIMIT / LIKE 检索(契约二、四的 SQL 形态)。 */
@Repository
public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {

  /** 核心区全量取(契约二:核心记忆永不被截断)。 */
  List<MemoryEntryEntity> findByScopeOrderByIdDesc(String scope);

  /**
   * 归档区只带最近 100 条(截断变成 SQL 的 LIMIT,核心区不受影响——契约二)。原生 SQL 写 LIMIT: Hibernate 派生查询的 fetch first 分页
   * SQLite 方言不支持。
   */
  @Query(
      value = "SELECT * FROM memory_entries WHERE scope = ?1 ORDER BY id DESC LIMIT 100",
      nativeQuery = true)
  List<MemoryEntryEntity> findRecentArchival(String scope);

  /** 归档区关键词检索(契约四:LIKE)。SQLite LIKE 默认大小写不敏感,符合"简单包含匹配"。 */
  @Query(
      "SELECT e FROM MemoryEntryEntity e WHERE e.scope = 'ARCHIVAL' AND e.content LIKE :pattern"
          + " ORDER BY e.id DESC")
  List<MemoryEntryEntity> searchArchival(@Param("pattern") String pattern);
}
