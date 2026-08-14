package com.oryxos.storage.session;

import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** SessionManager 的 JPA 实现。session_id 的拼接唯一发生在本类内部。 */
@Component
public class JpaSessionManager implements SessionManager {

  private final SessionRepository sessionRepository;
  private final SessionCodec sessionCodec;

  public JpaSessionManager(SessionRepository sessionRepository, SessionCodec sessionCodec) {
    this.sessionRepository = sessionRepository;
    this.sessionCodec = sessionCodec;
  }

  @Override
  public Session getOrCreate(String channel, String user, String profileName) {
    String sessionId = buildSessionId(channel, user, profileName);
    return get(sessionId)
        .orElseGet(
            () -> {
              Session session = new Session(sessionId, profileName, channel, user);
              save(session);
              return session;
            });
  }

  @Override
  public Optional<Session> get(String sessionId) {
    return sessionRepository.findById(sessionId).map(sessionCodec::fromEntity);
  }

  @Override
  public void save(Session session) {
    SessionEntity entity = sessionCodec.toEntity(session);
    sessionRepository.save(entity);
  }

  @Override
  public List<Session> listAll() {
    return sessionRepository.findAll().stream()
        .map(sessionCodec::fromEntity)
        .sorted(
            Comparator.comparing(
                Session::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  @Override
  public List<Session> listRecent(int limit) {
    return sessionRepository.findAll().stream()
        .map(sessionCodec::fromEntity)
        .sorted(
            Comparator.comparing(
                Session::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limit)
        .toList();
  }

  private String buildSessionId(String channel, String user, String profileName) {
    return channel + ":" + user + ":" + profileName;
  }
}
