package com.oryxos.web.dto;

import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import java.util.List;

/**
 * 固定会话视图(第 30 节 5.2.2):sessionId + profileName + 最近 ≤100 条消息。
 *
 * <p>每个 Agent 恰好一条固定会话(channel=admin, user=console, profile=Agent 名),管理台据此看上下文累积。
 */
public record SessionView(String sessionId, String profileName, List<Message> messages) {

  private static final int MAX_MESSAGES = 100;

  public static SessionView from(Session session) {
    List<Message> recent =
        session.getMessages().stream()
            .skip(Math.max(0, session.getMessages().size() - MAX_MESSAGES))
            .toList();
    return new SessionView(session.getSessionId(), session.getProfileName(), recent);
  }
}
