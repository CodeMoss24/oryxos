# Tool Contracts: OryxOS Core Runtime

## OryxTool 接口

所有 Tool 都包装为 `OryxTool` 注册到 `ToolRegistry`，ReAct 循环不感知 Tool 来源。

```java
interface OryxTool {
    String getName();
    String getDescription();
    JsonSchema getInputSchema();   // JSON Schema
    ToolResult execute(JsonNode input);
}

class ToolResult {
    boolean success;
    String content;                // 结果内容
    String errorMessage;           // 失败信息
    boolean retryable;             // 是否可重试
}
```

## 内置 Tool 清单

| 组 | Tool | 输入 | Sandbox 检查 |
|----|------|------|-------------|
| FileTools | `read_file` | `{path: string}` | FILE_READ |
| FileTools | `write_file` | `{path: string, content: string}` | FILE_WRITE |
| FileTools | `list_dir` | `{path: string}` | FILE_READ |
| ShellTools | `shell` | `{command: string, timeout?: int}` | SHELL_COMMAND |
| HttpTools | `http_get` | `{url: string, headers?: object}` | HTTP_REQUEST |
| HttpTools | `http_post` | `{url: string, body: string, headers?: object}` | HTTP_REQUEST |
| MemoryTools | `save_memory` | `{content: string, scope: "CORE"|"ARCHIVAL"}` | 无 |
| MemoryTools | `recall_memory` | `{keyword: string}` | 无 |
| NotifyTools | `notify` | `{content: string, channel?: string}` | 无（发送端过 HTTP_REQUEST） |

## Sandbox 接口

```java
interface Sandbox {
    void enforce(SandboxAction action) throws SandboxViolationException;
}

class SandboxAction {
    ActionType type;    // FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
    String target;      // 文件路径 / 命令 / URL
}

class SandboxViolationException extends RuntimeException {
    ActionType actionType;
    String target;       // 被拒绝的目标
}
```

### WhitelistSandbox 配置

```yaml
sandbox:
  file:
    allowed_paths:
      - /home/user/.oryxos
      - /tmp/oryxos
  shell:
    allowed_commands:
      - python3
      - python
      - bash
      - ls
      - cat
  http:
    allowed_domains:
      - "api.openweathermap.org"
      - "*.github.com"
      - "qyapi.weixin.qq.com"
```

## NotifyChannelAdapter 接口

```java
interface NotifyChannelAdapter {
    void send(NotifyTarget target, String content) throws NotifyException;
}

class NotifyTarget {
    String channelType;           // "webhook"
    Map<String, String> config;   // e.g., {"url": "https://qyapi.weixin.qq.com/..."}
}
```

## MCP Tool 适配

MCP Tool 通过 `McpToolAdapter` 包装为 `OryxTool`：

```
MCP Server (tools/list) → McpToolAdapter (包装为 OryxTool) → ToolRegistry
```

MCP server 配置在 `.oryxos/mcp_servers.yaml`：

```yaml
mcp_servers:
  - name: news-aggregator
    transport: stdio
    command: python3
    args: ["-m", "news_mcp_server"]
    env:
      API_KEY: "${NEWS_API_KEY}"
```