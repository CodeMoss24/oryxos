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

  /** 序列化为 JSON 数组字符串,包含可选字段 toolCallId / toolCalls */
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
          .append(escapeJson(m.content() != null ? m.content() : ""))
          .append("\"");
      if (m.toolCallId() != null) {
        sb.append(",\"toolCallId\":\"").append(escapeJson(m.toolCallId())).append("\"");
      }
      // 会话恢复保真:assistant 的 toolCalls 必须落库——丢它,含工具历史的会话下一条消息会被 Provider 拒单
      if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
        sb.append(",\"toolCalls\":[");
        for (int j = 0; j < m.toolCalls().size(); j++) {
          if (j > 0) sb.append(",");
          var tc = m.toolCalls().get(j);
          sb.append("{\"id\":\"")
              .append(escapeJson(tc.id() != null ? tc.id() : ""))
              .append("\",\"name\":\"")
              .append(escapeJson(tc.name() != null ? tc.name() : ""))
              .append("\",\"argumentsJson\":\"")
              .append(escapeJson(tc.argumentsJson() != null ? tc.argumentsJson() : ""))
              .append("\"}");
        }
        sb.append("]");
      }
      sb.append("}");
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
    String toolCallId = extractField(objectJson, "toolCallId");
    List<com.oryxos.core.react.ToolCall> toolCalls = extractToolCalls(objectJson);
    if (!toolCallId.isEmpty()) {
      return new Message(role, content, toolCalls, toolCallId);
    }
    if (!toolCalls.isEmpty()) {
      return new Message(role, content, toolCalls, null);
    }
    return new Message(role, content);
  }

  /** 解析 toolCalls 数组(可选字段;老数据没有则返回空列表,向后兼容)。 */
  private List<com.oryxos.core.react.ToolCall> extractToolCalls(String objectJson) {
    String key = "\"toolCalls\":[";
    int arrIdx = objectJson.indexOf(key);
    if (arrIdx < 0) {
      return List.of();
    }
    List<com.oryxos.core.react.ToolCall> result = new ArrayList<>();
    int i = arrIdx + key.length();
    int end = objectJson.indexOf(']', i);
    if (end < 0) {
      return result;
    }
    while (i < end) {
      int start = objectJson.indexOf('{', i);
      if (start < 0 || start > end) break;
      int close = findClosingBrace(objectJson, start);
      if (close < 0) break;
      String element = objectJson.substring(start, close + 1);
      result.add(
          new com.oryxos.core.react.ToolCall(
              extractField(element, "id"),
              extractField(element, "name"),
              extractField(element, "argumentsJson")));
      i = close + 1;
    }
    return result;
  }

  private String extractField(String objectJson, String field) {
    String key = "\"" + field + "\":\"";
    int keyIdx = objectJson.indexOf(key);
    if (keyIdx < 0) return "";
    int start = keyIdx + key.length();
    int end = findClosingQuote(objectJson, start);
    if (end < 0) return "";
    return unescapeJson(objectJson.substring(start, end));
  }

  /** 找字段值的闭合引号,值内转义引号(\\\")不算结束。 */
  private int findClosingQuote(String json, int start) {
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '"') {
        return i;
      }
    }
    return -1;
  }

  /** 找与 openIndex 配对的闭合花括号,按深度计数——嵌套对象(如 toolCalls 数组元素)不会提前截断。 */
  private int findClosingBrace(String json, int openIndex) {
    int depth = 0;
    for (int i = openIndex; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '"') {
        i = skipString(json, i);
      } else if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
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
