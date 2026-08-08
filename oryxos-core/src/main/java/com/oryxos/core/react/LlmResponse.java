package com.oryxos.core.react;

import java.util.List;

/** LLM 响应。包含文本内容、可能的 Tool 调用列表、token 用量。 */
public record LlmResponse(String content, List<ToolCall> toolCalls, Usage usage) {

  public static LlmResponse text(String content) {
    return new LlmResponse(content, List.of(), Usage.EMPTY);
  }

  public static LlmResponse text(String content, Usage usage) {
    return new LlmResponse(content, List.of(), usage);
  }
}
