package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** provider_configs 表 Repository(name 主键)。 */
@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfigEntity, String> {}
