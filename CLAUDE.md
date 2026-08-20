# CLAUDE.md

本文档是 OryxOS 项目的长期工作指引,所有 AI agent 在本仓库工作前必读。完整背景见 `docs/` 下四份文档(冲突时以 `TechnicalSolution.md` 为准):

- `docs/IndustryResearch.md` — Why:业界格局与定位
- `docs/DemandAnalysis.md` — What:需求与功能边界
- `docs/TechnicalSolution.md` — How:技术方案(权威)
- `docs/AiProgrammingGuide.md` — 落地:Spec-Kit + 手动提示词

---

## 一、项目指南

OryxOS 是 **Java 原生、面向严监管企业、私有可审计的 Agent OS**。装在企业自己的 K8s/服务器上,作为统一底座跑多个业务 Agent,共享 Channel / Provider / Tool / Memory / Sandbox,数据不出企业。

**核心阶段交付运行时内核**(对齐业界开源 Agent OS 基础层),企业级治理层(多租户、SSO、完整审计、Tool Policy)放扩展阶段。核心阶段是地基,不是终局——不要包装成完整企业级 Agent OS。

## 二、技术栈

- **JDK 21** + **Spring Boot 3.x**(virtual thread 撑高并发)
- **Spring AI** + **Spring AI Alibaba**(LLM Provider 抽象,复用现成 connector)
- 自实现 **ReAct loop**(不依赖 Spring AI Agent 抽象)
- **Spring MVC**(HTTP API 服务层)
- **Picocli**(命令行工具)
- **SnakeYAML**(Profile YAML 解析)
- **SQLite** + **Spring Data JPA**(Session、审计、元数据持久化)
- **MCP Java SDK**(MCP Client 集成)
- **Logback** + **SLF4J**(结构化日志)
- **Micrometer** + **Prometheus**(指标采集,扩展阶段)

## 三、模块结构(9 个 Maven 模块)

| 模块 | 职责 |
|------|------|
| `oryxos-core` | 核心抽象:`OryxTool` 接口、`Session`、`Profile`、`ContextLoader`、`AgentLoader`(扫 `.oryxos/agents/`、`deriveProfile`)、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler`、`AgentLifecycleService`、`AgentStore`、`WorkspaceWatcher`、`ToolExecutionContext` |
| `oryxos-provider` | `ProviderService`、Function Calling 适配、provider name → `ChatModel` **显式映射** |
| `oryxos-memory` | `MemoryService` 统一门面、`LongTermMemory`(三档后端)、`MemoryTools` |
| `oryxos-tool` | 内置 Tool(`FileTools`/`ShellTools`/`HttpTools`/`MemoryTools`/`NotifyTools`)、`McpClientService`、`McpToolAdapter`、`ToolRegistry`、`Sandbox` + `WhitelistSandbox`、`NotifyChannelAdapter` + `WebhookNotifyAdapter`(**三合一模块,不拆**) |
| `oryxos-channel-cli` | `CliChannel`、`oryxos chat` |
| `oryxos-web` | `WebServer`、6 个 `ApiController`(+`AgentApiController` 管理端点、`WorkspaceApiController`)、`GlobalExceptionHandler`、OpenAPI |
| `oryxos-storage` | SQLite、各 Repository |
| `oryxos-cli` | Picocli 主入口、12 个子命令、`ConfigLoader` |
| `oryxos-boot` | Spring Boot 启动模块 |

## 四、不可违背原则(constitution)

以下原则不可由 AI agent 自行修改,冲突时停下讨论:

1. **JDK 21 + Spring Boot 3.x 单体应用**,Maven 多模块(9 个),单二进制部署
2. **五大核心能力优先**:对接 LLM、ReAct、Memory、Tool、Web Service;治理层放扩展
3. **自实现 ReAct loop**,不依赖 Spring AI 的 Agent 抽象
4. **Spring AI 只用一半**:
   - ✅ 只用 Provider 抽象 + 协议转换 + `@Tool` schema 生成
   - ❌ **禁用 Spring AI 的自动 tool 执行**——否则 tool 会被调两次
   - Tool 调度完全由 `ReActLoop` + `ToolExecutor` 控制
   - **这是最容易被写错的一条,实施前必查**
5. **Plugin Tool 三档接入**,主推零代码 `AGENT.md` + MCP
6. **SQLite + `MEMORY.md` 文件存储**,向量检索放扩展
7. **审计 day one 落库**:`tool_invocations`、`llm_calls` 核心阶段就写入 SQLite(不做查询接口),不能只放日志
8. **接口先行**:Sandbox、NotifyChannelAdapter、LongTermMemoryStore 先定抽象接口再挂实现,未来换重隔离方案不改调用方
9. **每个 user story 完成后有可演示 Demo**,优先跑通而非完美

## 五、数据模型

### SQLite 五张表

1. **`sessions`** — Session 元数据 + `messages_json`(JSON 序列化的对话历史)
   - 主键 `session_id` = channel + user + profile 联合生成
   - 字段:`profile_name`、`channel`、`user_id`、`messages_json`、`status`(`active`/`archived`)、`created_at`、`last_active_at`、`archived_at`
2. **`tool_invocations`** — **day one 写入**(审计地基)
   - 字段:`session_id`、`tool_name`、`input_json`、`result_json`、`success`、`error_message`、`duration_ms`、`created_at`
3. **`llm_calls`** — **day one 写入**(审计地基)
   - 字段:`session_id`、`provider`、`model`、`prompt_tokens`、`completion_tokens`、`total_tokens`、`duration_ms`、`created_at`
4. **`scheduled_tasks`** — 定时任务登记与状态
   - 字段:`task_id`、`profile_name`、`cron`、`zone`、`message`、`enabled`、`next_run_at`、`last_run_at`、`last_status`、`run_count`、`updated_at`
5. **`task_executions`** — 定时任务执行历史(成功失败都记)
   - 字段:`id`、`task_id`、`session_id`、`started_at`、`success`、`error_message`、`duration_ms`

> SQLite 的 `ALTER TABLE` 能力有限,`hibernate.ddl-auto=update` 不要用于表结构演进,需手动维护建表脚本或引入 Flyway/Liquibase。

### 文件系统数据

`.oryxos/` 下放文件系统不放数据库的数据:

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent(AGENT.md + 可选 skills/ scripts/ REFERENCE.md)
├── skills/            # 全局 Skill 库(SKILL.md),Agent 按名引用
├── output/            # Agent 产出物
├── memory/
│   └── MEMORY.md      # 长期记忆(## 核心记忆 / ## 归档记忆 两个 header)
├── sessions/          # Session 数据
├── logs/              # 结构化日志
├── mcp_servers.yaml   # MCP 配置
├── AGENTS.md          # Bootstrap:项目级 agent 行为说明
├── SOUL.md            # Bootstrap:默认 agent 人格定义
├── USER.md            # Bootstrap:用户偏好
└── oryxos.db          # SQLite
```

### MEMORY.md 结构(默认 `MarkdownMemoryStore` 后端)

- `## 核心记忆`:全量注入 system prompt,**永不截断、不参与检索**
- `## 归档记忆`:截断 + 关键词检索
- `save_memory(content, scope)` 的 `scope` 由 Agent 显式指定(`CORE` / `ARCHIVAL`),系统不猜
- 三档后端靠 `memory.backend` 切换:`MarkdownMemoryStore`(默认)/ `SqliteMemoryStore`/ `Mem0MemoryStore`,上层代码不动
- 第 30 节起记忆 **per-agent**:`ToolExecutionContext` 有 Agent 名时读写 `agents/<name>/MEMORY.md`,无上下文回退全局 `memory/MEMORY.md`(`LongTermMemoryStore` SPI 与三档后端契约不变)

## 六、ReAct Loop 机制

ReAct(Reason + Act)是 OryxOS 最核心的一段代码。输入一条用户消息,输出 Agent 的最终响应,中间可能调用若干次 LLM 和若干次 Tool。

### 算法步骤

```
1. 接到用户消息追加到 Session 对话历史
2. 组装 Prompt(system prompt + Bootstrap + Skill + Memory + 对话历史 + 可用 Tool 列表)
3. 调用 LLM Provider 获取响应
4. 如果响应没有 Tool 调用 → 返回最终响应
5. 如果有 Tool 调用 → OryxOS 执行 Tool 并把结果作为 tool 消息追加到对话历史
6. 回到步骤 2 继续循环
7. 达到最大迭代次数(默认 10 次,可在 Profile 覆盖)强制结束
```

### 核心模块

- **`ReActLoop`** — 核心循环引擎,约数十行 Java,不依赖 Spring AI Agent 抽象
- **`PromptBuilder`** — 按四部分顺序拼接 Prompt:① system prompt(`AGENT.md` 正文 + Bootstrap,末尾附当前日期时间)② Memory 注入(会话历史 + 长期记忆)③ 对话历史(按 `maxHistoryTurns` 截断)④ 可用 Tool 列表
- **`ToolExecutor`** — 执行 Tool 调用,从 `ToolRegistry` 找 Tool,做 Sandbox 检查,执行后写入 `tool_invocations`
- **`AgentService`** — 三种触发源(CLI / Web Service / `AgentScheduler`)的统一入口,`process(Session, String)` 依次:放 `ProfileContext`(ThreadLocal)→ 调 `ReActLoop.run` → 持久化 Session → `finally` 清 `ProfileContext`

### 三种触发源汇入同一个 AgentService

- CLI(`oryxos chat`)、Web Service(`POST /agents/{name}/invoke`)是"人推"
- `AgentScheduler`(Profile `schedules` 字段驱动 cron)是"钟推"
- 三者都调 `AgentService.process`,`ReActLoop` 不感知触发来源
- 钟推 Session 的 channel 和 user 都固定为 `scheduler`,沿用既有 `session_id` 公式

### 上下文长度管理

核心阶段策略简单:保留 system prompt 和最近 N 轮对话(N 由 Profile 配置默认 20 轮),超出部分丢弃。扩展阶段引入总结压缩。

## 七、Tool 体系

### OryxTool 抽象

统一的 Tool 接口,内置 Tool、`@Tool` 注解的 Plugin Tool、MCP Tool 都被包装成 `OryxTool` 注册到 `ToolRegistry`。约定四个方法:`getName`、`getDescription`、`getInputSchema`(JSON Schema)、`execute`(JSON 输入 → `ToolResult`)。ReAct 循环不感知 Tool 来源。

### 内置 Tool(9 个,分 5 组)

| 组 | Tool | 说明 |
|----|------|------|
| `FileTools` | `read_file` / `write_file` / `list_dir` | 执行前调 `Sandbox.enforce` 做路径白名单检查 |
| `ShellTools` | `shell` | 执行 bash 命令,带超时和命令白名单 |
| `HttpTools` | `http_get` / `http_post` | 带域名白名单 |
| `MemoryTools` | `save_memory` / `recall_memory` | 归 Memory 模块,作为内置 Tool 注册 |
| `NotifyTools` | `notify` | 推送到 Profile `notify_channels` 配置的目标 |

### Plugin Tool 三档接入

| 方式 | 门槛 | 推荐度 | 场景 |
|------|------|--------|------|
| **方式一**:写 Agent 目录(`AGENT.md`)+ 复用 MCP server | 零代码 | ⭐⭐⭐ 主推 | 描述意图,LLM 自己组合现成能力 |
| **方式二**:自己写 MCP server | 轻代码 | ⭐⭐ | 接入企业自有系统,任何语言皆可 |
| **方式三**:写 Java `@Tool` Bean | 重代码 | ⭐ | 深度集成,性能最好 |

> 选择原则:能用方式一就不用方式二,能用方式二就不用方式三。

### Sandbox(接口先行)

```
Sandbox.enforce(SandboxAction action)
SandboxAction = { type: ActionType, target: String }
ActionType     = FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
```

- 核心阶段唯一实现 `WhitelistSandbox`:路径/命令/域名白名单,**不用 `SecurityManager`**(JDK 17 废弃、JDK 21 不可用)
- 接口不携带任何实现细节(不出现"白名单""容器镜像""VM 配置"字样),用最重的 microVM 实现反向套这个签名也应能干净套入
- 升级路径:白名单 → 容器隔离 → microVM,接口不变只新增实现类
- Sandbox 校验失败抛 `SandboxViolationException`,复用 `ToolExecutor` 既有失败审计路径(`success=false`)

### NotifyChannelAdapter(对称补出站)

- 入站有 Channel Adapter,出站用 `NotifyChannelAdapter.send(target, content)`
- 核心阶段只实现 `WebhookNotifyAdapter`,复用 `Sandbox.enforce(HTTP_REQUEST, ...)` 共享 `http.allowed_domains`
- 跟入站 Channel 是不同抽象(语义方向相反),不合并

### 一个目录 = 一个 Agent(不是 Tool)

- `.oryxos/agents/<name>/AGENT.md`:frontmatter = 这个 Agent 自己的 profile;正文 = 任务指令
- `AgentLoader.deriveProfile()` 把 frontmatter 派生成 `Profile`,**不再另写 Profile YAML**(`.oryxos/profiles/` 取消)
- Agent 目录里的子指令/脚本/参考**不预载**,由正文指引经 `read_file`/`shell` 按需取(渐进式披露)
- **`AGENT.md` 不是可执行 Tool**——加载归 `oryxos-core` 的 `ContextLoader`,不进 `ToolRegistry`

## 八、API(Web Service)

核心阶段 10 个 REST 端点(会话列表为只读扩展,共 11 个),**只做查询和调用,不做创建**:

| 类别 | 端点 | 说明 |
|------|------|------|
| 会话管理 | `POST /api/v1/sessions` | 创建会话 |
| 会话管理 | `POST /api/v1/sessions/{id}/messages` | 发消息 |
| 会话管理 | `GET /api/v1/sessions/{id}` | 查历史 |
| 会话管理 | `DELETE /api/v1/sessions/{id}` | 归档会话 |
| Agent 调用 | `POST /api/v1/agents/{name}/invoke` | 无状态调用 |
| Profile 信息 | `GET /api/v1/profiles` | 列 Profile |
| 会话管理 | `GET /api/v1/sessions` | 会话列表(只读扩展) |
| Agent 管理 | `POST /api/v1/agents`、`GET /api/v1/agents`、`GET/PUT/DELETE /api/v1/agents/{name}` | 创建(脚手架+派生注册)/列表/详情/覆写/归档 |
| Agent 管理 | `POST /api/v1/agents/{name}/generate-files`、`/files` | 一句话生成草稿(不落盘)/保存一组文件并生效 |
| Agent 管理 | `GET /api/v1/agents/{name}/memory` | 该 Agent 的长期记忆全文(不截断;per-agent,取代原全局 `/api/v1/memory`) |
| Agent 管理 | `GET /api/v1/agents/{name}/session`、`POST /{name}/session/messages` | 固定会话(channel=admin, user=console,上下文累积) |
| 工作区 | `GET /api/v1/workspace/tree`、`GET/POST /api/v1/workspace/file?path=` | 目录树 / 读写文件(防目录穿越,AGENT.md 走 lifecycle.update) |
| Tool 信息 | `GET /api/v1/tools` | 列可用 Tool |
| 系统状态 | `GET /api/v1/health` | 健康检查 |
| 系统状态 | `GET /api/v1/info` | 运行信息 |

**关键设计点**:错误码规范(400/404/500/503)、CORS 核心阶段全开方便调试、单条消息最大 32KB、Session 历史返回最多最近 100 条、Agent 调用最长 60 秒超时返回 504。

**核心阶段不做**:认证、流式 SSE、WebSocket、RBAC、限流。Agent 目录上传接口、`AgentScheduler` 运行时增删、Memory 的 append/clear/search、Tool describe、LLM call 历史查询、Webhook 触发、Prometheus metrics、OpenAPI spec——放扩展阶段。

## 九、CLI(Picocli 12 个命令)

| 类别 | 命令 | 说明 |
|------|------|------|
| 启动和状态 | `oryxos init` | 初始化工作区 |
| 启动和状态 | `oryxos status` | 查看配置和运行状态 |
| 启动和状态 | `oryxos chat [--profile <name>]` | 交互对话 |
| 启动和状态 | `oryxos serve` | 启动 HTTP API 服务(默认 8080) |
| 启动和状态 | `oryxos gateway` | 启动多渠道守护进程 |
| Profile 管理 | `oryxos profile list` | 列出所有 Profile |
| Profile 管理 | `oryxos profile create <name>` | 创建新 Profile(生成最小 AGENT.md 模板) |
| Profile 管理 | `oryxos profile show <name>` | 查看 Profile 详情 |
| Profile 管理 | `oryxos profile delete <name>` | 删除 Profile(整个目录) |
| 查询 | `oryxos provider list` | 列出已配置的 Provider |
| 查询 | `oryxos tool list` | 列出已注册的 Tool |
| 查询 | `oryxos session list` | 列出会话历史 |

不需要 Spring 上下文的命令(`init`、`profile list`)直接走文件操作启动快;需要 LLM 调用的命令(`chat`、`serve`、`gateway`)启动 Spring 上下文。

## 十、四周节奏

4 周 × 3 小时 = 12 小时,按 user story 依赖推进:

```
US-1 (Provider) → US-2 (ReAct) → ┌─ US-3 (Memory) ─┐ → US-5 (Web Service)
                                  └─ US-4 (Tool)    ─┘
```

| 周次 | 能力主线 | 可演示成果 |
|------|---------|-----------|
| 第一周 | 对接 LLM + ReAct 循环 | `oryxos chat` 多轮对话,Agent 通过 ReAct 调 HTTP Tool 完成天气查询 |
| 第二周 | Memory + Tool 体系 | Agent 记住用户偏好并后续对话用到,能调本地文件和外部 MCP server |
| 第三周 | Web Service | 外部系统通过 10 个 REST 端点完整调用 OryxOS |
| 第四周 | 多 Agent 演示 + 工程化收尾 | 多 Agent 并存,CLI 完整,Session 跨重启恢复,定时任务到点自动触发,主页可访问 |

### 三个验收 Demo(第四周末)

| Demo | Agent 目录形态 | 验证能力 |
|------|--------------|---------|
| **每日天气** | 光杆 `AGENT.md` | LLM + ReAct + 内置 HTTP Tool + NotifyTools + 定时 |
| **每日科技日报** | `AGENT.md` + `skills/` 子指令 | Memory + MCP 方式二 + `read_file` 按需加载 |
| **每日 GitHub 日报** | `AGENT.md` + `scripts/` 脚本 | `shell` 跑脚本 + 沙箱信任边界 |

三个 Demo 都是"钟推",但都要支持"人推"手动补跑验证同一链路。

### 主体开发用 Spec-Kit

`constitution.md` / `spec.md` / `plan.md` 一次性准备好,5 个 user story 按 `/speckit.tasks` 拆任务。**每个 user story 完成后必跑 `/speckit.analyze`** 做 spec 一致性检查,不能省。增量阶段切手动提示词 + Claude Code。

## 十一、常见陷阱

| 陷阱 | 正确做法 |
|------|---------|
| 启用 Spring AI 自动 tool 执行 | **禁用**,见原则四——否则 tool 被调两次 |
| Provider 用类型扫描区分 | 必须维护 provider name → `ChatModel` 显式映射(Bean 类型相同会歧义) |
| Tool 拆成 builtin/skill/mcp 多模块 | 合并为 `oryxos-tool` 一个模块 |
| `AGENT.md` 当成 Tool | 归 `oryxos-core` 的 `ContextLoader`,正文注入 system prompt,不进 `ToolRegistry` |
| 审计表只写日志不落库 | `tool_invocations`、`llm_calls` day one 写入 SQLite |
| Memory 简化成跟 Session 合并 | `MemoryService` 三层统一门面,对 ReAct 暴露一个接口 |
| 用 `SecurityManager` 做沙箱 | JDK 17 废弃、JDK 21 不可用,用 `WhitelistSandbox` |
| `hibernate.ddl-auto=update` 做表结构演进 | SQLite ALTER 能力弱,用手动建表脚本或 Flyway/Liquibase |
| 给核心阶段加治理层(多租户/SSO/Tool Policy/流式) | 放扩展阶段,看到 AI agent 主动加时叫停 |
| Web Service 核心阶段做创建端点(Agent 目录上传等) | 只做查询和调用,创建放扩展阶段一起补纯 API 闭环 |
| `MEMORY.md` 核心区参与检索/被截断 | 核心区全量注入、永不截断、不参与检索;截断和检索只作用在归档区 |
| 脚本经 `python` 子进程发网络请求绕过域名白名单 | 装带脚本的 Agent = 信任该 Agent 作者,核心阶段沙箱对脚本只做"解释器 + 脚本目录"两道白名单,容器/网络隔离放扩展 |
| 改了非协商原则 | 停下重新讨论,**不允许 AI agent 自己修改 constitution** |

## 环境约束

- **JDK 21+**(Spring Boot 3.x 要求)
- **操作系统**:Linux 主流发行版(Ubuntu 22.04+ / CentOS 8+ / Debian 11+ / Alibaba Cloud Linux 3 / Rocky Linux)
- **LLM 协议**:OpenAI 兼容协议是事实标准
- **网络**:本机 WSL2 环境下 HTTPS 443 被防火墙拦截,仅 SSH 可访问 GitHub,`gh` CLI 不可用——git 操作走 SSH
