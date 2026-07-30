# MCP 集成

OryxOS 通过 MCP Java SDK 实现 MCP Client 集成，支持零代码接入企业系统。

## 配置

在 `.oryxos/mcp_servers.yaml` 中配置：

```yaml
mcp_servers:
  - name: enterprise-erp
    command: npx @your-company/erp-mcp-server
    args: ["--port", "3000"]
  - name: internal-api
    command: python
    args: ["-m", "internal_api_mcp"]
```

## 工作原理

1. OryxOS 启动时读取 `mcp_servers.yaml`
2. 为每个配置项启动 MCP Server 子进程
3. 通过标准 MCP 协议发现 Server 暴露的 Tool
4. 每个 MCP Tool 被包装成 `OryxTool`，注册到 `ToolRegistry`
5. ReAct 循环中，MCP Tool 和内置 Tool 使用方式完全一致

## McpToolAdapter

`McpToolAdapter` 负责将 MCP Tool 的 schema 和调用协议转换为 `OryxTool` 接口。ReAct 循环不感知 Tool 来源。
