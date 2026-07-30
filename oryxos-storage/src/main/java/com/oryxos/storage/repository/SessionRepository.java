package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    List<SessionEntity> findByProfileName(String profileName);

    List<SessionEntity> findByStatus(String status);
}
