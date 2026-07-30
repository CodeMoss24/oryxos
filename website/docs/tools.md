# Tool 体系

统一的 `OryxTool` 接口，所有 Tool 类型都被包装成 `OryxTool` 注册到 `ToolRegistry`。

## 接口定义

```java
public interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();  // JSON Schema
    ToolResult execute(String jsonInput);
}
```

## 内置 Tool（9 个，5 组）

| 组 | Tool | 说明 |
|----|------|------|
| FileTools | `read_file` / `write_file` / `list_dir` | 路径白名单检查 |
| ShellTools | `shell` | 命令白名单 + 超时 |
| HttpTools | `http_get` / `http_post` | 域名白名单 |
| MemoryTools | `save_memory` / `recall_memory` | 归 Memory 模块 |
| NotifyTools | `notify` | 推送到配置的通知渠道 |

## 三档接入方式

| 方式 | 门槛 | 推荐度 | 场景 |
|------|------|--------|------|
| AGENT.md + 复用 MCP server | 零代码 | ⭐⭐⭐ | 描述意图，LLM 组合现有能力 |
| 自写 MCP server | 轻代码 | ⭐⭐ | 接入企业自有系统 |
| Java `@Tool` Bean | 重代码 | ⭐ | 深度集成，性能最好 |

## Sandbox

所有 Tool 执行前经过 `Sandbox.enforce()` 检查：
- 文件操作：路径白名单
- Shell 命令：命令白名单
- HTTP 请求：域名白名单

接口先行，升级路径：白名单 → 容器隔离 → microVM，接口不变。
