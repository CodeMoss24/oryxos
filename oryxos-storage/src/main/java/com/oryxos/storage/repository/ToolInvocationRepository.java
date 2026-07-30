package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Long> {

    List<ToolInvocationEntity> findBySessionId(String sessionId);
}
