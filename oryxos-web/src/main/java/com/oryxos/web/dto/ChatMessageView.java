package com.oryxos.web.dto;

import com.oryxos.core.react.ToolCall;
import com.oryxos.core.session.Message;
import java.util.List;

/**
 * 管理台会话聚合消息:role/content/toolCalls/toolCallId 与 core {@link Message} 同构, 外加 source 标注消息来自哪个会话
 * (管理台固定会话 / invoke 手动调用 / scheduler 定时)。前端据此分组渲染并给消息加来源徽标。
 */
public record ChatMessageView(
    String role, String content, List<ToolCall> toolCalls, String toolCallId, String source) {

  public static ChatMessageView from(Message m, String source) {
    return new ChatMessageView(m.role(), m.content(), m.toolCalls(), m.toolCallId(), source);
  }
}
