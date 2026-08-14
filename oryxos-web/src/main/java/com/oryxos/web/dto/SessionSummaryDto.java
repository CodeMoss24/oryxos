package com.oryxos.web.dto;

import com.oryxos.core.session.Session;
import java.time.Instant;

/** 会话摘要(列表端点专用,第 27 节)。只带列表页要展示的字段,不带完整对话正文, 避免列表过大。 */
public record SessionSummaryDto(
    String sessionId,
    String profileName,
    String channel,
    String userId,
    String status,
    Instant createdAt,
    Instant lastActiveAt,
    int messageCount) {

  public static SessionSummaryDto from(Session session) {
    return new SessionSummaryDto(
        session.getSessionId(),
        session.getProfileName(),
        session.getChannel(),
        session.getUserId(),
        session.getStatus(),
        session.getCreatedAt(),
        session.getLastActiveAt(),
        session.getMessages().size());
  }
}
