package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SandboxWhitelistEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sandbox_whitelist_entries 表的 Spring Data 仓库。 */
public interface SandboxWhitelistEntryRepository
    extends JpaRepository<SandboxWhitelistEntryEntity, Long> {

  boolean existsByCategoryAndEntryValue(String category, String entryValue);

  List<SandboxWhitelistEntryEntity> findByCategoryAndEntryValue(String category, String entryValue);
}
