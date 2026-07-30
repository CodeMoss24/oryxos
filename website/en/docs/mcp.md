# MCP Integration

OryxOS integrates MCP Clients via the MCP Java SDK, supporting zero-code connection to enterprise systems.

## Configuration

Configure in `.oryxos/mcp_servers.yaml`:

```yaml
mcp_servers:
  - name: enterprise-erp
    command: npx @your-company/erp-mcp-server
    args: ["--port", "3000"]
  - name: internal-api
    command: python
    args: ["-m", "internal_api_mcp"]
```

## How It Works

1. At startup, OryxOS reads `mcp_servers.yaml`
2. Spawns an MCP Server subprocess for each configuration
3. Discovers Tools exposed by the Server via standard MCP protocol
4. Each MCP Tool is wrapped as `OryxTool` and registered in `ToolRegistry`
5. Within the ReAct loop, MCP Tools and built-in Tools are used identically

## McpToolAdapter

`McpToolAdapter` converts MCP Tool schemas and invocation protocols to the `OryxTool` interface. The ReAct loop is unaware of Tool origin.
