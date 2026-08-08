# Data Model: OryxOS Core Runtime

## Overview

OryxOS 核心阶段有两类持久化：**SQLite**（结构化数据：会话、审计、定时任务）和 **文件系统**（Agent 定义、长期记忆、配置）。

---

## SQLite 实体

### Session

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | String (PK) | channel + user + profile 联合生成 |
| profile_name | String | 关联的 Profile 名称 |
| channel | String | 接入 Channel（cli / web / scheduler） |
| user_id | String | 用户标识 |
| messages_json | Text (JSON) | JSON 序列化的对话历史 |
| status | Enum: active / archived | 会话状态 |
| created_at | DateTime | 创建时间 |
| last_active_at | DateTime | 最后活跃时间 |
| archived_at | DateTime (nullable) | 归档时间 |

**约束**:
- `session_id` 唯一主键
- `status` 默认 `active`

### ToolInvocation

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 自增主键 |
| session_id | String (FK → Session) | 关联会话 |
| tool_name | String | 调用的 Tool 名称 |
| input_json | Text (JSON) | Tool 输入参数 |
| result_json | Text (JSON) | Tool 执行结果 |
| success | Boolean | 执行是否成功 |
| error_message | Text (nullable) | 失败时的错误信息 |
| duration_ms | Long | 执行耗时（毫秒） |
| created_at | DateTime | 记录创建时间 |

**约束**:
- 核心阶段只写入，不提供查询接口
- `success=false` 时 `error_message` 必填

### LlmCall

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 自增主键 |
| session_id | String (FK → Session) | 关联会话 |
| provider | String | Provider 名称 |
| model | String | 模型名称 |
| prompt_tokens | Integer | 输入 token 数 |
| completion_tokens | Integer | 输出 token 数 |
| total_tokens | Integer | 总 token 数 |
| duration_ms | Long | 调用耗时（毫秒） |
| created_at | DateTime | 记录创建时间 |

### ScheduledTask

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | String (PK) | 任务唯一标识 |
| profile_name | String | 归属 Profile |
| cron | String | cron 表达式 |
| zone | String | 时区 |
| message | Text | 到点发给 Agent 的消息 |
| enabled | Boolean | 是否启用 |
| next_run_at | DateTime | 下次触发时刻 |
| last_run_at | DateTime (nullable) | 上次触发时刻 |
| last_status | Enum: success / failed / null | 上次执行结果 |
| run_count | Integer | 累计触发次数 |
| updated_at | DateTime | 状态更新时间 |

### TaskExecution

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 自增主键 |
| task_id | String (FK → ScheduledTask) | 关联任务 |
| session_id | String (FK → Session) | 本次触发的钟推 Session |
| started_at | DateTime | 开始时间 |
| success | Boolean | 是否成功 |
| error_message | Text (nullable) | 失败信息 |
| duration_ms | Long | 执行耗时 |

---

## 文件系统实体

### Agent 目录 (`.oryxos/agents/<name>/`)

```
AGENT.md             # frontmatter (profile) + 正文 (任务指令)
skills/*.md          # (可选) 子指令，按需 read_file 读取
scripts/*            # (可选) 脚本，按需 shell 执行
REFERENCE.md         # (可选) 参考文档
```

**AGENT.md frontmatter 字段**:
| 字段 | 说明 |
|------|------|
| name | Agent 名称 |
| description | 描述 |
| identity / agent_name | Agent 身份标识 |
| identity / prompt | 系统提示词 |
| provider / name | 使用的 Provider |
| provider / model | 使用的模型 |
| provider / temperature | 温度参数 |
| tools | 可用 Tool 列表 |
| mcp_servers | 引用的 MCP server |
| channels | 接入的 Channel |
| notify_channels | 通知渠道配置 |
| schedules | 定时计划 |
| bootstrap | Bootstrap 文件列表 |
| settings / max_iterations | 最大迭代次数 |
| settings / max_history_turns | 最大历史轮数 |

### Memory (`.oryxos/memory/MEMORY.md`)

```markdown
## 核心记忆
- **2026-08-01**: 用户偏好：关注 AI 和芯片方向

## 归档记忆
- **2026-08-01**: 用户询问了天气穿搭建议
```

### Bootstrap (`.oryxos/`)

| 文件 | 说明 |
|------|------|
| AGENTS.md | 项目级 agent 行为说明 |
| SOUL.md | 默认 agent 人格定义 |
| USER.md | 用户偏好 |

### 配置 (`.oryxos/`)

| 文件 | 说明 |
|------|------|
| mcp_servers.yaml | MCP server 配置列表 |

---

## 关键关系

```
Session (1) ──→ (N) ToolInvocation   # 一次会话多次 Tool 调用
Session (1) ──→ (N) LlmCall          # 一次会话多次 LLM 调用
Profile (1) ──→ (N) Session          # 一个 Profile 多个会话
ScheduledTask (1) ──→ (N) TaskExecution  # 一个定时任务多次执行
Session (1) ──→ (1) TaskExecution    # 一次执行关联一个钟推 Session
```