package com.oryxos.core.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Session 是用户和 Agent 一次对话的上下文容器。 Session 标识由 channel + user + profile 联合生成。 */
public class Session {

  private String sessionId;
  private String profileName;
  private String channel;
  private String userId;
  private final List<Message> messages = new ArrayList<>();
  private String status = "active";
  private Instant createdAt;
  private Instant lastActiveAt;

  public Session() {}

  public Session(String sessionId, String profileName, String channel, String userId) {
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
    this.createdAt = Instant.now();
    this.lastActiveAt = this.createdAt;
  }

  public void append(Message message) {
    messages.add(message);
    this.lastActiveAt = Instant.now();
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public List<Message> getMessages() {
    return messages;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(Instant lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }
}
