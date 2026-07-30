package com.oryxos.core.tool;

/**
 * Tool 执行结果。包含成功标识、结果内容、错误信息、是否可重试。
 */
public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, false);
    }

    public static ToolResult failure(String errorMessage, boolean retryable) {
        return new ToolResult(false, null, errorMessage, retryable);
    }
}
