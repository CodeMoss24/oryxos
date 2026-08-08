package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

  List<SessionEntity> findByProfileName(String profileName);

  List<SessionEntity> findByStatus(String status);
}
