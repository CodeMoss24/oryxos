package com.oryxos.core.react;

/** Tool 执行审计端口。由 oryxos-provider 实现,用 ToolInvocationRepository 写入 SQLite。 */
public interface ToolAuditPort {

  void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs);
}
