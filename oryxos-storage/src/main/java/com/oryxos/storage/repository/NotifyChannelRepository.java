package com.oryxos.storage.repository;

import com.oryxos.storage.entity.NotifyChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** notify_channels 读写:主键是 name(String);CRUD 直接用 JpaRepository 内置方法。 */
public interface NotifyChannelRepository extends JpaRepository<NotifyChannelEntity, String> {}
