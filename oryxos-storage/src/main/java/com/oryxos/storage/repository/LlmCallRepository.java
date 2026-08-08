package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {

  List<LlmCallEntity> findBySessionId(String sessionId);
}
