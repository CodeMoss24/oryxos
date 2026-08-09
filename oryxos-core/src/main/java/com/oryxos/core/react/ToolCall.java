package com.oryxos.core.react;

/** LLM 通过 Function Calling 指明的 Tool 调用请求。包含调用 id(用于 tool_call_id 回传)。 */
public record ToolCall(String id, String name, String argumentsJson) {

  public ToolCall(String name, String argumentsJson) {
    this(null, name, argumentsJson);
  }
}
