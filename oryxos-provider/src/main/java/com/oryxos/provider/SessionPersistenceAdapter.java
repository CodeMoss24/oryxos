package com.oryxos.provider;

import com.oryxos.core.SessionPersistencePort;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 17 节既有写路径,统一委托 SessionManager(唯一落库出口),序列化与 upsert 不再各写一份。 */
@Component
public class SessionPersistenceAdapter implements SessionPersistencePort {

  private static final Logger log = LoggerFactory.getLogger(SessionPersistenceAdapter.class);

  private final SessionManager sessionManager;

  public SessionPersistenceAdapter(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  public void save(Session session) {
    try {
      sessionManager.save(session);
    } catch (Exception e) {
      log.error("Failed to persist session {}", session.getSessionId(), e);
    }
  }
}
