# Data Model: Plugin Agent Directory

**Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

本节**不新增数据库表**（`scheduled_tasks`/`task_executions` 28 节已建，`sessions`/`tool_invocations`/`llm_calls` 既有）。数据模型聚焦于内存值对象 + 文件系统目录契约。

## 内存值对象（既有，本节扩展）

### Profile（既有，不改字段）
值对象，由 `AgentLoader.deriveProfile` 从 frontmatter 派生。已有字段：name / description / identity / provider / tools / skills / mcpServers / channels / notifyChannels / schedules / bootstrap / settings。本节不增字段（R4 决定资源路径不进 Profile）。

### ProfileRegistry（既有，改并发结构）
| 字段 | 类型 | 说明 |
|---|---|---|
| profiles | `ConcurrentHashMap<String, Profile>` | 由 LinkedHashMap 改；key=Agent name |

方法（签名不变）：`register(Profile)` / `remove(String)` / `exists(String)` / `find(String)→Optional` / `list()→Collection`。

### AgentScheduler 新增内存结构
| 字段 | 类型 | 说明 |
|---|---|---|
| scheduledTasks | `Map<String, ScheduledFuture<?>>` | **新增**；key=schedule id（sc.id()），value=taskScheduler.schedule 返回的句柄；供下节注销/更新；ConcurrentHashMap；与既有 taskLocks/taskRefs/scheduledTaskIds 并存 |

方法：新增 `registerProfile(Profile)`（public，抽自 registerAll 循环体）；`registerAll` 改为遍历调 `registerProfile`。

## 文件系统目录契约（唯一真相源）

### Agent 目录布局
```
.oryxos/agents/<name>/
├── AGENT.md            # 必需：frontmatter(profile) + 正文(任务指令)
├── REFERENCE.md        # 可选：参考，read_file 按需读
├── skills/*.md         # 可选：子指令，read_file 按需读
└── scripts/*           # 可选：脚本，shell/python 按需跑（产出进上下文、代码不进）
```

### AGENT.md frontmatter 字段契约
| 字段 | 必填 | 映射到 Profile | 说明 |
|---|---|---|---|
| name | 否（取目录名） | name | frontmatter 可写但以目录名为准 |
| description | 否 | description | |
| identity.agent_name / identity.prompt | 否 | identity | |
| **provider.name** | **是** | provider.name | 缺则 `IllegalArgumentException` 点名 |
| provider.model | 否 | provider.model | |
| provider.temperature | 否 | provider.temperature | |
| tools | 否 | tools | 引用未注册能力 → 告警不阻断 |
| notify_channels | 否 | notifyChannels | `${ENV}` 占位解析 |
| schedules | 否 | schedules | 每条需 id，缺 id 跳过该条 |
| skills / mcp_servers / bootstrap | 否 | 同名 | 既有 |

### 校验规则（两层，不反转依赖方向）
- **core 层**（`AgentLoader.deriveProfile`，启动扫描与运行时注册同一段）：`provider.name` 非空，否则 `IllegalArgumentException("Agent '<name>': missing required field 'provider.name'")`。单 Agent 失败不阻断其余。
- **boot 层**（扫描后）：`provider.name` → 已注册 ChatModel 映射校验（复用 16 节 `ProviderService`），未映射 `log.warn` 不阻断。
- **core 层 tools 告警**（`AgentLoader.warnUnregisteredTools(profile, toolRegistry)`）：tools 中 name 不在 ToolRegistry → `log.warn` 不阻断。

## 状态/生命周期
- Agent 目录：新增（扫描注册）→ 运行（到点触发/人推）→ 移除（`remove`，下节 API）。核心阶段同名重复注册覆盖。
- 调度句柄：`registerProfile` 时入 `scheduledTasks`；下节注销时 `future.cancel()` + 移出。
