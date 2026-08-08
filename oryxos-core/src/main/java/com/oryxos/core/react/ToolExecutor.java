package com.oryxos.core.react;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 执行 LLM 返回的 Tool 调用请求。 从 ToolRegistry 找到对应 Tool,做 Sandbox 检查,执行 Tool,把结果包装成 ToolResult 返回给 ReAct
 * 循环, 并通过 ToolAuditPort 写入 tool_invocations 表。
 */
@Component
public class ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

  private final ToolRegistry toolRegistry;
  private final ToolAuditPort toolAuditPort;

  public ToolExecutor(ToolRegistry toolRegistry, ToolAuditPort toolAuditPort) {
    this.toolRegistry = toolRegistry;
    this.toolAuditPort = toolAuditPort;
  }

  public String execute(String sessionId, ToolCall call, Profile profile) {
    long startedAt = System.currentTimeMillis();
    Optional<OryxTool> tool = toolRegistry.find(call.name());
    if (tool.isEmpty()) {
      long durationMs = System.currentTimeMillis() - startedAt;
      toolAuditPort.record(
          sessionId,
          call.name(),
          call.argumentsJson(),
          null,
          false,
          "Tool not found: " + call.name(),
          durationMs);
      log.warn("Tool not found: {}", call.name());
      return "Tool '" + call.name() + "' not found";
    }
    try {
      ToolResult result = tool.get().execute(call.argumentsJson());
      long durationMs = System.currentTimeMillis() - startedAt;
      if (result.success()) {
        toolAuditPort.record(
            sessionId, call.name(), call.argumentsJson(), result.content(), true, null, durationMs);
        return result.content();
      } else {
        toolAuditPort.record(
            sessionId,
            call.name(),
            call.argumentsJson(),
            null,
            false,
            result.errorMessage(),
            durationMs);
        return "Tool failed: " + result.errorMessage();
      }
    } catch (Exception e) {
      long durationMs = System.currentTimeMillis() - startedAt;
      toolAuditPort.record(
          sessionId, call.name(), call.argumentsJson(), null, false, e.getMessage(), durationMs);
      log.error("Tool execution error: {}", call.name(), e);
      return "Tool error: " + e.getMessage();
    }
  }
}
