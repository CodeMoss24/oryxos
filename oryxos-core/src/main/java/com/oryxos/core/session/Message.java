package com.oryxos.core.session;

import com.oryxos.core.react.ToolCall;
import java.util.List;

/** 对话消息。role 取 system / user / assistant / tool。 */
public record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

  public Message(String role, String content) {
    this(role, content, null, null);
  }

  public static Message system(String content) {
    return new Message("system", content);
  }

  public static Message user(String content) {
    return new Message("user", content);
  }

  public static Message assistant(String content) {
    return new Message("assistant", content);
  }

  public static Message assistant(String content, List<ToolCall> toolCalls) {
    return new Message("assistant", content, toolCalls, null);
  }

  public static Message tool(String content, String toolCallId) {
    return new Message("tool", content, null, toolCallId);
  }
}
