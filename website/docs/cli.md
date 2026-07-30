# CLI 命令

OryxOS 提供 12 个 CLI 命令，基于 Picocli 实现。

## 启动和状态

| 命令 | 说明 |
|------|------|
| `oryxos init` | 初始化工作区 |
| `oryxos status` | 查看配置和运行状态 |
| `oryxos chat [--profile <name>]` | 交互对话 |
| `oryxos serve` | 启动 HTTP API 服务（默认 8080） |
| `oryxos gateway` | 启动多渠道守护进程 |

## Profile 管理

| 命令 | 说明 |
|------|------|
| `oryxos profile list` | 列出所有 Profile |
| `oryxos profile create <name>` | 创建新 Profile（生成最小 AGENT.md 模板） |
| `oryxos profile show <name>` | 查看 Profile 详情 |
| `oryxos profile delete <name>` | 删除 Profile（整个目录） |

## 查询

| 命令 | 说明 |
|------|------|
| `oryxos provider list` | 列出已配置的 Provider |
| `oryxos tool list` | 列出已注册的 Tool |
| `oryxos session list` | 列出会话历史 |

## 启动模式

- 不需要 Spring 上下文的命令（`init`、`profile list`）直接走文件操作，启动快
- 需要 LLM 调用的命令（`chat`、`serve`、`gateway`）启动 Spring 上下文
