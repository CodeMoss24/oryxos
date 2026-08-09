package com.oryxos.tool.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MCP 工具适配器 harness(mock McpSyncClient):execute 原样转发 + 结果包成 ToolResult; isError/异常 →
 * failure(可重试);getInputSchema 来自 tools/list 的 schema 序列化。
 */
class McpToolAdapterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static McpSchema.Tool toolFromJson(String json) throws Exception {
    return MAPPER.readValue(json, McpSchema.Tool.class);
  }

  @Test
  @DisplayName("execute 转发参数原样、结果包成 ToolResult")
  void executeForwardsArguments() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class);
    McpSchema.Tool tool =
        toolFromJson(
            "{\"name\":\"echo\",\"description\":\"回显\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},"
                + "\"required\":[\"text\"]}}");
    when(client.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(
            new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("第一段"), new McpSchema.TextContent("第二段")),
                false));

    McpToolAdapter adapter = new McpToolAdapter(client, tool);
    ToolResult result = adapter.execute("{\"text\":\"hi\"}");

    assertTrue(result.success(), () -> "expected success but got: " + result.errorMessage());
    assertEquals("第一段第二段", result.content(), "多块 TextContent 按序拼接");

    var captor = org.mockito.ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
    verify(client).callTool(captor.capture());
    assertEquals("echo", captor.getValue().name(), "转发时工具名原样");
    assertEquals(Map.of("text", "hi"), captor.getValue().arguments(), "转发时参数原样");
  }

  @Test
  @DisplayName("isError=true → failure(可重试)")
  void isErrorMapsToRetryableFailure() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class);
    McpSchema.Tool tool = toolFromJson("{\"name\":\"bad\",\"inputSchema\":{\"type\":\"object\"}}");
    when(client.callTool(any(McpSchema.CallToolRequest.class)))
        .thenReturn(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("boom")), true));

    ToolResult result = new McpToolAdapter(client, tool).execute("{}");

    assertFalse(result.success());
    assertTrue(result.retryable());
    assertTrue(result.errorMessage().contains("boom"));
  }

  @Test
  @DisplayName("callTool 抛异常 → failure(可重试,不静默)")
  void exceptionMapsToRetryableFailure() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class);
    McpSchema.Tool tool = toolFromJson("{\"name\":\"bad\",\"inputSchema\":{\"type\":\"object\"}}");
    when(client.callTool(any(McpSchema.CallToolRequest.class)))
        .thenThrow(new RuntimeException("server went away"));

    ToolResult result = new McpToolAdapter(client, tool).execute("{}");

    assertFalse(result.success());
    assertTrue(result.retryable());
    assertTrue(result.errorMessage().contains("MCP 调用失败"));
  }

  @Test
  @DisplayName("getInputSchema 返回 tools/list 的 schema 序列化(非空)")
  void inputSchemaFromToolMeta() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class);
    McpSchema.Tool tool =
        toolFromJson(
            "{\"name\":\"add\",\"description\":\"加法\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"}},"
                + "\"required\":[\"a\"]}}");

    McpToolAdapter adapter = new McpToolAdapter(client, tool);

    assertEquals("add", adapter.getName());
    assertEquals("加法", adapter.getDescription());
    assertNotNull(adapter.getInputSchema());
    assertTrue(
        adapter.getInputSchema().contains("\"required\":[\"a\"]"), "schema 应含 server 声明的必填字段");
  }
}
