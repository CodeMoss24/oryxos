package com.oryxos.core.react;

/** LLM 通过 Function Calling 指明的 Tool 调用请求。 */
public record ToolCall(String name, String argumentsJson) {}
