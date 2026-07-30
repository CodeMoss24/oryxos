# Tool System

Unified `OryxTool` interface — all Tool types are wrapped as `OryxTool` and registered in `ToolRegistry`.

## Interface

```java
public interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();  // JSON Schema
    ToolResult execute(String jsonInput);
}
```

## Built-in Tools (9 tools, 5 groups)

| Group | Tool | Description |
|-------|------|-------------|
| FileTools | `read_file` / `write_file` / `list_dir` | Path whitelist check |
| ShellTools | `shell` | Command whitelist + timeout |
| HttpTools | `http_get` / `http_post` | Domain whitelist |
| MemoryTools | `save_memory` / `recall_memory` | Delegated to Memory module |
| NotifyTools | `notify` | Push to configured channels |

## Three Integration Tiers

| Method | Barrier | Recommendation | Use Case |
|--------|---------|---------------|----------|
| AGENT.md + existing MCP server | Zero code | ⭐⭐⭐ | Describe intent, LLM composes capabilities |
| Custom MCP server | Light code | ⭐⭐ | Connect enterprise systems |
| Java `@Tool` Bean | Heavy code | ⭐ | Deep integration, best performance |

## Sandbox

All Tool executions pass through `Sandbox.enforce()`:
- File operations: path whitelist
- Shell commands: command whitelist
- HTTP requests: domain whitelist

Interface-first: upgrade path from whitelist → container isolation → microVM, interface unchanged.
