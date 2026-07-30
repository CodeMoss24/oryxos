package com.oryxos.core.react;

import java.util.List;

/**
 * LLM 响应。包含文本内容和可能的 Tool 调用列表。
 */
public record LlmResponse(String content, List<ToolCall> toolCalls) {

    public static LlmResponse text(String content) {
        return new LlmResponse(content, List.of());
    }
}
