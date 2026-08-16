package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ScheduledTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** scheduled_tasks 表 Repository(第 28 节)。 */
@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTaskEntity, String> {}
