package com.oryxos.storage.session;

import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.storage.entity.SessionEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Session 与 SessionEntity 的双向转换 + messages_json 序列化/反序列化。全模块唯一的 JSON 编解码实现, 避免多处手写 JSON 的 escape
 * 不一致。
 */
@Component
public class SessionCodec {

  public SessionEntity toEntity(Session session) {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(session.getSessionId());
    entity.setProfileName(session.getProfileName());
    entity.setChannel(session.getChannel());
    entity.setUserId(session.getUserId());
    entity.setMessagesJson(serializeMessages(session.getMessages()));
    entity.setStatus(session.getStatus());
    entity.setCreatedAt(session.getCreatedAt());
    entity.setLastActiveAt(session.getLastActiveAt());
    return entity;
  }

  public Session fromEntity(SessionEntity entity) {
    Session session =
        new Session(
            entity.getSessionId(),
            entity.getProfileName(),
            entity.getChannel(),
            entity.getUserId());
    session.setStatus(entity.getStatus());
    session.setCreatedAt(entity.getCreatedAt());
    for (Message message : deserializeMessages(entity.getMessagesJson())) {
      session.append(message);
    }
    // append 会刷新 lastActiveAt,这里覆盖为持久化的值
    session.setLastActiveAt(entity.getLastActiveAt());
    return session;
  }

  /** 序列化为 JSON 数组字符串,如 [{"role":"user","content":"hi"}] */
  public String serializeMessages(List<Message> messages) {
    if (messages == null || messages.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < messages.size(); i++) {
      if (i > 0) sb.append(",");
      Message m = messages.get(i);
      sb.append("{\"role\":\"")
          .append(escapeJson(m.role()))
          .append("\",\"content\":\"")
          .append(escapeJson(m.content()))
          .append("\"}");
    }
    return sb.append("]").toString();
  }

  /** 反序列化 JSON 数组字符串;空串/坏 JSON 返回空列表,不抛异常(容忍脏数据) */
  public List<Message> deserializeMessages(String json) {
    List<Message> result = new ArrayList<>();
    if (json == null || json.isBlank() || !json.trim().startsWith("[")) {
      return result;
    }
    String trimmed = json.trim();
    int i = 1;
    while (i < trimmed.length() - 1) {
      int start = trimmed.indexOf('{', i);
      if (start < 0) break;
      int end = findClosingBrace(trimmed, start);
      if (end < 0) break;
      result.add(parseMessage(trimmed.substring(start, end + 1)));
      i = end + 1;
    }
    return result;
  }

  private Message parseMessage(String objectJson) {
    String role = extractField(objectJson, "role");
    String content = extractField(objectJson, "content");
    return new Message(role, content);
  }

  private String extractField(String objectJson, String field) {
    String key = "\"" + field + "\":\"";
    int keyIdx = objectJson.indexOf(key);
    if (keyIdx < 0) return "";
    int start = keyIdx + key.length();
    int end = objectJson.indexOf('"', start);
    if (end < 0) return "";
    return unescapeJson(objectJson.substring(start, end));
  }

  private int findClosingBrace(String json, int openIndex) {
    for (int i = openIndex + 1; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '"') {
        i = skipString(json, i);
      } else if (c == '}') {
        return i;
      }
    }
    return -1;
  }

  private int skipString(String json, int quoteIndex) {
    for (int i = quoteIndex + 1; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '"') {
        return i;
      }
    }
    return json.length() - 1;
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private String unescapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\t", "\t")
        .replace("\\r", "\r")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\");
  }
}
