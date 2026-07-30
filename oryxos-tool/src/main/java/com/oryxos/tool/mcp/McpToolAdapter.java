package com.oryxos.tool.mcp;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;

/**
 * 把 MCP Tool 适配成 OryxTool 接口。Tool 调用时通过 MCP 协议(JSON-RPC over stdio 或 SSE)
 * 转发给对应 MCP server 执行,结果包装成 ToolResult 返回。
 *
 * <p>核心阶段骨架,实际 MCP 调用待 MCP Java SDK 接入后补全。
 */
public class McpToolAdapter implements OryxTool {

    private final String name;
    private final String description;
    private final String inputSchema;

    public McpToolAdapter(String name, String description, String inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public String getInputSchema() { return inputSchema; }

    @Override
    public ToolResult execute(String inputJson) {
        // TODO: 通过 MCP 协议转发 inputJson 给对应 MCP server 执行,结果包装成 ToolResult。
        return ToolResult.failure("MCP tool execution not yet implemented", false);
    }
}
