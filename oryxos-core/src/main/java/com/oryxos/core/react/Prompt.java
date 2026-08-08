package com.oryxos.core.react;

import com.oryxos.core.session.Message;
import com.oryxos.core.tool.OryxTool;
import java.util.List;

/** 一次 LLM 调用的输入:消息列表 + 本次可用的 Tool 列表。 */
public record Prompt(List<Message> messages, List<OryxTool> availableTools) {

  public Prompt(List<Message> messages) {
    this(messages, List.of());
  }
}
