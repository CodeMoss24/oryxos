# Data Model: 动态管理 Agent

> Phase 1 输出。数据以**文件系统**为主(SQLite 不新增表);本文件描述实体、字段与请求/响应形状。契约细节见 [contracts/rest-api.md](contracts/rest-api.md)。

## 实体

### Agent(一个目录 = 一个 Agent)

`.oryxos/agents/<name>/` 目录。真相源只有文件系统,API 与手工丢目录殊途同归。

| 字段 | 来源 | 说明 |
|---|---|---|
| `name` | 目录名 + frontmatter `name` | 唯一标识;创建/生成校验必填 |
| `description` | frontmatter `description` | 一句话定位(创建入参,脚手架写入) |
| `provider` | frontmatter `provider: {name, model, temperature}` | `name` 必填(deriveProfile 校验);model 可空 |
| `identity` | frontmatter `identity: {agent_name, prompt}` | 可选 |
| `tools` | frontmatter `tools: [...]` | 可选;未注册工具告警不阻断 |
| `skills` | frontmatter `skills: [...]` | 可选 |
| `mcp_servers` | frontmatter `mcp_servers: [...]` | 可选 |
| `bootstrap` | frontmatter `bootstrap: [...]` | 可选,默认 AGENTS.md 等 |
| `notify_channels` | frontmatter `notify_channels: [{type, url, ...}]` | 可选;`${ENV}` 占位解析 |
| `schedules` | frontmatter `schedules: [{id, cron, zone, message}]` | 可选;缺 `id` 条目跳过记日志;变更需先注销旧句柄 |
| 正文 | `AGENT.md` `---` 之后 | 任务指令,进 system prompt(渐进式披露,子资源按需取) |
| 资源 | `scripts/` `skills/` `REFERENCE.md` `MEMORY.md` | 可选;`MEMORY.md` 为 per-agent 专属记忆 |

**生命周期状态机**:`不存在 → created(目录已写,未注册,失败回滚)/ registered(派生+注册)/ archived(目录移入 .oryxos/archive/,不物理删)`。更新不换状态,覆写后重注册。

### 固定会话(每 Agent 一条)

| 字段 | 值 |
|---|---|
| `channel` | 固定 `admin`(5.2.2 课件定值) |
| `user` | 固定 `console` |
| `profileName` | Agent 名 |
| `session_id` | 由 `SessionManager.getOrCreate` 幂等拼接(只在 SessionManager 内拼,H4 ④) |

`GET /agents/{name}/session` → 会话 + 最近 ≤100 条消息;`POST /agents/{name}/session/messages` → 发消息触发 ReAct,上下文跨消息累积。

### per-agent 记忆

- 文件:`agents/<name>/MEMORY.md`,与 AGENT.md 同目录(合原则四)
- 写入:Agent 在 ReAct 中调 `save_memory`/`recall_memory` → `ToolExecutionContext.agentName`(ThreadLocal,`ToolExecutor` 置入/清除) → `MarkdownMemoryStore` 打开该 Agent 文件
- 读取(不经 ToolExecutor):`MemoryServiceImpl.buildContext`(取 `session.profileName()`)/`readAll`(取入参 Agent 名)委托 store 前后临时置入
- 无 agentName(CLI 手动上下文等)→ 回退全局 `.oryxos/memory/MEMORY.md`
- `LongTermMemoryStore` SPI 与三档后端(Markdown/SQLite/Mem0)契约测试**一行不改**

## 请求/响应 DTO(web 层)

| DTO | 字段 | 端点 |
|---|---|---|
| `CreateAgentRequest` | `name`(必填)、`description`(必填) | `POST /agents` |
| `UpdateAgentRequest` | `description`、`provider`(name/model/temperature)、`tools`、`schedules`、`notify_channels`、正文 `body`(任一出现即覆写该项) | `PUT /agents/{name}` |
| `GenerateFilesRequest` | `description`(一句话描述) | `POST /agents/{name}/generate-files` |
| `SaveFilesRequest` | `files`: `Map<相对路径, 内容>`(必含 `AGENT.md`) | `POST /agents/{name}/files` |
| `FileContentRequest` | `path`(相对 .oryxos)、`content` | `POST /workspace/file` |
| `AgentView` | `name`、`description`、`provider`(name/model/temperature)、`tools`、`schedules`、`notifyChannels`、`body`、`resources`(scripts/skills/reference 存在性) | `GET /agents`、`GET /agents/{name}`、`PUT` 响应 |
| `FileNode` | `name`、`type`(DIR/FILE)、`children?` | `GET /workspace/tree` |
| `SessionView` | `sessionId`、`profileName`、`messages`(≤100 条) | `GET /agents/{name}/session` |

## 生成草稿(不落盘)

`POST /agents/{name}/generate-files` 响应:`Map<String, String>`(相对路径 → 内容),至少含 `AGENT.md`;`AGENT.md` 须经 `AgentLoader.parseAgentMd` 校验可解析(缺 name/provider.name → 400);多余 ` ``` ` 代码围栏剥掉;输出 provider 沿用该 Agent 既有 provider;该 Agent 不存在 → 404。

## 目录树

`GET /workspace/tree` → `{agents: [FileNode...], archive: [FileNode...]}`;FileNode 叶子为文件、内部为目录(递归,含 AGENT.md/scripts/skills/REFERENCE.md/MEMORY.md 等)。

## 防目录穿越(唯一安全要点)

`path` 入参:`Path.of(path).normalize()` 后必须 `startsWith(oryxosRoot.normalize())`(oryxosRoot = `OryxOsRuntime.workspaceRoot()` 绝对化),否则 400。读/写同一套校验。
