package com.oryxos.storage.repository;

import com.oryxos.storage.entity.AgentExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** agent_executions 表 Repository。 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, Long> {

  List<AgentExecutionEntity> findTop50ByAgentNameOrderByIdDesc(String agentName);
}
