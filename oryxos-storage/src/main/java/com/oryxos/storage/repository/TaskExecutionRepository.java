package com.oryxos.storage.repository;

import com.oryxos.storage.entity.TaskExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** task_executions 表 Repository(第 28 节)。 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Long> {

  List<TaskExecutionEntity> findByTaskIdOrderByStartedAtDesc(String taskId);
}
