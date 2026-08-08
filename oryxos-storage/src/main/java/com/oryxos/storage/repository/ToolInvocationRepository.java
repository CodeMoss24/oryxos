package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Long> {

  List<ToolInvocationEntity> findBySessionId(String sessionId);
}
