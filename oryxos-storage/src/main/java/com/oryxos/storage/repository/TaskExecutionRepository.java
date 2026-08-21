package com.oryxos.storage.repository;

import com.oryxos.storage.entity.TaskExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** task_executions 表 Repository(第 28 节)。 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Long> {

  List<TaskExecutionEntity> findByTaskIdOrderByStartedAtDesc(String taskId);

  /** 删除某任务的全部执行历史(任务删除时级联清理,避免孤儿记录)。 */
  void deleteByTaskId(String taskId);
}
