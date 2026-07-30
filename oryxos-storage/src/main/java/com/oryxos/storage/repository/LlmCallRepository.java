package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {

    List<LlmCallEntity> findBySessionId(String sessionId);
}
