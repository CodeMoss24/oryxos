package com.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 把 MCP server 暴露的 Tool 适配成 OryxTool。执行时经 MCP 协议(JSON-RPC over stdio/SSE)把参数原样转发给 对应 server 执行,结果包成
 * ToolResult。
 *
 * <p>inputSchema 从 tools/list 返回的 JsonSchema 手工提取组装——该类在 MCP SDK 中是包私有 record, 不能直接引用类型名,用 var + 普通
 * Map 中转。MCP 调用失败标记可重试(isError 与异常两路径都 retryable=true), 由 ReAct 循环决定是否再调。
 */
public class McpToolAdapter implements OryxTool {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final McpSyncClient client;
  private final McpSchema.Tool tool;
  private final String inputSchemaJson;

  public McpToolAdapter(McpSyncClient client, McpSchema.Tool tool) {
    this.client = client;
    this.tool = tool;
    this.inputSchemaJson = toInputSchemaJson(tool);
  }

  @Override
  public String getName() {
    return tool.name();
  }

  @Override
  public String getDescription() {
    // 部分 server 不返回 description,契约要求非空,兜底空串
    return tool.description() == null ? "" : tool.description();
  }

  @Override
  public String getInputSchema() {
    return inputSchemaJson;
  }

  @Override
  public ToolResult execute(String inputJson) {
    try {
      Map<String, Object> args = objectMapper.readValue(inputJson, Map.class);
      McpSchema.CallToolResult result =
          client.callTool(new McpSchema.CallToolRequest(tool.name(), args));
      String content = joinContent(result.content());
      if (Boolean.TRUE.equals(result.isError())) {
        return ToolResult.failure(content, true);
      }
      return ToolResult.success(content);
    } catch (Exception e) {
      return ToolResult.failure("MCP 调用失败: " + e.getMessage(), true);
    }
  }

  private static String joinContent(List<McpSchema.Content> content) {
    StringBuilder sb = new StringBuilder();
    if (content != null) {
      for (McpSchema.Content c : content) {
        if (c instanceof McpSchema.TextContent tc) {
          sb.append(tc.text());
        }
      }
    }
    return sb.toString();
  }

  private static String toInputSchemaJson(McpSchema.Tool tool) {
    try {
      // JsonSchema 是 SDK 包私有类型,不能直接引用(连 var 成员调用都会被 javac 拒绝)——把 Tool 序列化回 JSON,
      // 按字段名取 inputSchema,再原样序列化,全程只用公共类型。
      String toolJson = objectMapper.writeValueAsString(tool);
      Map<String, Object> toolMap = objectMapper.readValue(toolJson, Map.class);
      Object schema = toolMap.get("inputSchema");
      if (schema == null) {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
      }
      return objectMapper.writeValueAsString(schema);
    } catch (IOException e) {
      return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }
  }
}
