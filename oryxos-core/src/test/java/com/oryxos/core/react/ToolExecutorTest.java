package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

  @Mock private ToolRegistry toolRegistry;
  @Mock private ToolAuditPort toolAuditPort;
  @Mock private OryxTool mockTool;

  private ToolExecutor executor;
  private Profile profile;

  @BeforeEach
  void setUp() {
    executor = new ToolExecutor(toolRegistry, toolAuditPort);
    profile = new Profile();
    profile.setName("test");
  }

  @Test
  @DisplayName("成功写审计 success=true")
  void recordsSuccessAuditWhenToolSucceeds() {
    ToolCall call = new ToolCall("http_get", "{\"url\":\"http://example.com\"}");
    when(toolRegistry.find("http_get")).thenReturn(java.util.Optional.of(mockTool));
    when(mockTool.execute(call.argumentsJson())).thenReturn(ToolResult.success("OK"));

    String result = executor.execute("s-1", call, profile);

    assertEquals("OK", result);
    verify(toolAuditPort)
        .record(
            eq("s-1"),
            eq("http_get"),
            eq(call.argumentsJson()),
            eq("OK"),
            eq(true),
            isNull(),
            anyLong());
  }

  @Test
  @DisplayName("失败写审计 success=false 带原因,异常不吞")
  void recordsFailureAuditWhenToolFails() {
    ToolCall call = new ToolCall("bad_tool", "{}");
    when(toolRegistry.find("bad_tool")).thenReturn(java.util.Optional.of(mockTool));
    when(mockTool.execute(call.argumentsJson()))
        .thenThrow(new RuntimeException("connection refused"));

    String result = executor.execute("s-1", call, profile);

    assertTrue(result.contains("Tool error"));
    assertTrue(result.contains("connection refused"));
    verify(toolAuditPort)
        .record(
            eq("s-1"),
            eq("bad_tool"),
            eq("{}"),
            isNull(),
            eq(false),
            eq("connection refused"),
            anyLong());
  }

  @Test
  @DisplayName("工具返回 failure 时审计 success=false")
  void recordsFailureAuditWhenToolReturnsError() {
    ToolCall call = new ToolCall("http_get", "{}");
    when(toolRegistry.find("http_get")).thenReturn(java.util.Optional.of(mockTool));
    when(mockTool.execute(call.argumentsJson())).thenReturn(ToolResult.failure("timeout", false));

    String result = executor.execute("s-1", call, profile);

    assertTrue(result.contains("Tool failed"));
    assertTrue(result.contains("timeout"));
    verify(toolAuditPort)
        .record(eq("s-1"), eq("http_get"), eq("{}"), isNull(), eq(false), eq("timeout"), anyLong());
  }

  @Test
  @DisplayName("工具未找到时审计 success=false")
  void recordsAuditWhenToolNotFound() {
    ToolCall call = new ToolCall("nonexistent", "{}");
    when(toolRegistry.find("nonexistent")).thenReturn(java.util.Optional.empty());

    String result = executor.execute("s-1", call, profile);

    assertTrue(result.contains("not found"));
    verify(toolAuditPort)
        .record(
            eq("s-1"), eq("nonexistent"), eq("{}"), isNull(), eq(false), anyString(), anyLong());
  }
}
