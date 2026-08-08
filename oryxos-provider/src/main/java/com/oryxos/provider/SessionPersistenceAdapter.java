package com.oryxos.provider;

import com.oryxos.core.SessionPersistencePort;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SessionPersistenceAdapter implements SessionPersistencePort {

  private static final Logger log = LoggerFactory.getLogger(SessionPersistenceAdapter.class);

  private final SessionRepository sessionRepository;

  public SessionPersistenceAdapter(SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public void save(Session session) {
    try {
      SessionEntity entity = new SessionEntity();
      entity.setSessionId(session.getSessionId());
      entity.setProfileName(session.getProfileName());
      entity.setChannel(session.getChannel());
      entity.setUserId(session.getUserId());
      entity.setMessagesJson(serializeMessages(session.getMessages()));
      entity.setStatus(session.getStatus());
      entity.setCreatedAt(session.getCreatedAt());
      entity.setLastActiveAt(session.getLastActiveAt());
      sessionRepository.save(entity);
    } catch (Exception e) {
      log.error("Failed to persist session {}", session.getSessionId(), e);
    }
  }

  private String serializeMessages(List<Message> messages) {
    List<Map<String, String>> list = new ArrayList<>();
    for (Message m : messages) {
      Map<String, String> map = new LinkedHashMap<>();
      map.put("role", m.role());
      map.put("content", m.content());
      list.add(map);
    }
    return list.stream()
        .map(
            m ->
                "{\"role\":\""
                    + escapeJson(m.get("role"))
                    + "\",\"content\":\""
                    + escapeJson(m.get("content"))
                    + "\"}")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
