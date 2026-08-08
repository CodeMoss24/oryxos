# Feature Specification: OryxOS Core Runtime

**Feature Branch**: `001-oryxos-core-runtime`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: OryxOS 核心阶段运行时内核——面向严监管企业的私有可审计 Agent OS

## User Scenarios & Testing

### User Story 1 - 运维人员部署并初始化 OryxOS (Priority: P1)

企业运维人员下载 OryxOS 后，通过命令行初始化工作区、配置 LLM Provider，启动系统。系统应能在单台服务器上快速部署并验证运行状态。

**Why this priority**: 所有后续能力依赖系统先跑起来。这是最基础的"开机"场景。

**Independent Test**: 运维人员在空服务器上执行 `init`、`status`、`provider list` 三个命令，能成功看到系统状态和已配置的 Provider 列表。

**Acceptance Scenarios**:

1. **Given** 一台干净的 Linux 服务器，**When** 运维人员执行初始化命令，**Then** 工作区目录创建完成，必要的配置模板生成
2. **Given** 已初始化的工作区，**When** 运维人员查看系统状态，**Then** 显示运行模式、已注册的 Provider、可用 Tool 数量

---

### User Story 2 - 业务人员通过 CLI 与 Agent 对话 (Priority: P1)

业务人员在终端中与 Agent 进行多轮对话，Agent 能理解任务、调用工具完成任务。例如：Agent 调用天气查询工具获取天气信息并给出建议。

**Why this priority**: ReAct 循环是 Agent OS 最核心的能力，必须第一个验证通。

**Independent Test**: 业务人员启动 `oryxos chat`，输入"今天北京的天气怎么样"，Agent 成功返回天气信息（通过 HTTP 工具获取），可进行至少 3 轮连续对话。

**Acceptance Scenarios**:

1. **Given** OryxOS 已启动，**When** 业务人员输入一条自然语言指令，**Then** Agent 理解并返回有意义的响应
2. **Given** 多轮对话进行中，**When** 业务人员引用前面讨论过的内容，**Then** Agent 能基于对话历史给出连贯的回答
3. **Given** Agent 需要外部信息，**When** 任务需要调用工具，**Then** Agent 自动触发工具调用并整合结果到回答中

---

### User Story 3 - Agent 记住用户偏好并在后续对话中应用 (Priority: P2)

业务人员在对话中表达个人偏好（如"我关注 AI 和芯片方向"），Agent 记住这些偏好。在后续对话中，Agent 自动参考这些偏好来调整回答的侧重点。

**Why this priority**: 长期记忆是 Agent OS 区别于普通聊天机器人的核心差异化能力，让 Agent 越用越懂用户。

**Independent Test**: 用户在对话中说"关注 AI 方向"，然后在新会话中问"今天有什么新闻"，Agent 的回答优先覆盖 AI 相关内容。

**Acceptance Scenarios**:

1. **Given** 用户表达了偏好，**When** 开启新的对话，**Then** Agent 在回答中体现已记录的偏好
2. **Given** 多个偏好已记录，**When** 用户要求回忆之前说过的内容，**Then** Agent 能准确提取相关的长期记忆

---

### User Story 4 - 开发者通过 REST API 集成 OryxOS (Priority: P2)

企业业务系统通过 HTTP API 调用 OryxOS：创建会话、发送消息、查询历史、获取 Agent 列表等。业务系统可以将 OryxOS 作为 Agent 服务集成到现有工作流中。

**Why this priority**: Web Service 是 OryxOS 区别于纯 CLI 工具的关键——没有它，企业系统无法集成。

**Independent Test**: 开发者通过 `curl` 调用 `POST /api/v1/agents/{name}/invoke` 实现无状态 Agent 调用，并获得 JSON 格式的响应。

**Acceptance Scenarios**:

1. **Given** OryxOS Web Service 运行中，**When** 业务系统 POST 消息到会话端点，**Then** Agent 响应并在 60 秒内返回
2. **Given** 已有会话历史，**When** 业务系统 GET 会话详情，**Then** 返回最近 100 条消息
3. **Given** 系统运行异常，**When** 业务系统检查健康状态，**Then** 返回准确的健康/故障信息

---

### User Story 5 - Agent 按定时计划自动运行并推送结果 (Priority: P3)

运维人员配置一个"每日天气"Agent，每天早上 8 点自动查询天气并推送到企业 IM 群。Agent 到点自动触发，全程无需人工参与。

**Why this priority**: 定时任务是"钟推"场景的基石——让 Agent 从"被动回答"升级为"主动服务"。

**Independent Test**: 配置一个每 5 分钟触发一次的 Agent，验证到点自动执行并在推送渠道收到结果。

**Acceptance Scenarios**:

1. **Given** Agent 配置了定时计划，**When** 到达 cron 触发时间，**Then** Agent 自动启动任务执行
2. **Given** 定时任务执行完成，**When** 任务有输出结果，**Then** 结果被推送到配置的通知渠道
3. **Given** 同一个 Agent 支持多种触发方式，**When** 管理员通过 CLI 手动触发，**Then** 执行链路与定时触发完全一致

---

### Edge Cases

- 同时配置了定时任务和手工触发的 Agent，两种触发方式是否共享同一 Session？
- LLM Provider 返回超时或不可用时，系统如何表现？
- Agent 在 Tool 调用循环中达到最大迭代次数时如何结束？
- 对话历史超出上下文窗口限制时如何处理？
- 企业服务器重启后，未完成的定时任务如何处理？
- 多个用户同时通过 Web Service 调用同一个 Agent，会话是否隔离？

## Requirements

### Functional Requirements

- **FR-001**: 系统必须支持通过命令行完成工作区初始化、配置查看和状态检查
- **FR-002**: 系统必须支持至少一种 LLM Provider 的对接（OpenAI 兼容协议）
- **FR-003**: 系统必须实现 ReAct（Reason + Act）循环作为 Agent 的核心推理引擎
- **FR-004**: Agent 必须能调用预定义的工具（如 HTTP 请求）来获取外部信息
- **FR-005**: 系统必须支持多轮对话，维护对话上下文
- **FR-006**: Agent 必须能记录长期记忆并在后续对话中引用
- **FR-007**: Agent 必须能通过关键词检索已存储的长期记忆
- **FR-008**: 系统必须提供 REST API，支持会话创建、消息发送、历史查询、Agent 调用
- **FR-009**: 系统必须支持 Agent 按 cron 表达式定时自动触发
- **FR-010**: 定时任务执行完成后必须能通过通知渠道推送结果
- **FR-011**: Agent 必须能执行文件读写操作（限定在安全范围内）
- **FR-012**: Agent 必须能执行 Shell 命令（限定在白名单内）
- **FR-013**: 系统必须记录每次 Tool 调用和 LLM 调用的执行记录，持久化到 SQLite 存储，不依赖日志文件
- **FR-014**: 系统必须支持通过 MCP 协议接入外部工具服务
- **FR-015**: 系统必须支持多个 Agent 在同一实例上同时运行，各自独立配置
- **FR-016**: Agent 必须能发送通知到外部系统（如企业 IM webhook）
- **FR-017**: 系统必须支持 HTTP 域名白名单、文件路径白名单、Shell 命令白名单三层安全沙箱

### Key Entities

- **Agent**: 一个业务能力的完整定义，包含身份描述、Provider 绑定、可用 Tool 列表、通知配置和定时计划。由 `.oryxos/agents/<name>/AGENT.md` 定义。
- **Session**: 一次对话的生命周期，包含完整的对话历史消息、关联的 Profile 和用户标识。支持通过 Web Service 查询。
- **Tool**: Agent 可以调用的外部能力单元。包括内置 Tool（文件、Shell、HTTP、记忆、通知）和 MCP Tool。
- **Profile**: Agent 的运行时配置，派生自 `AGENT.md` 的 frontmatter，定义 Agent 的身份、模型、可用工具、通知渠道、定时计划等。
- **Memory**: Agent 的长期记忆存储，支持核心记忆（永久保留）和归档记忆（可检索、可截断）两级。
- **Task**: Agent 在定时计划下的单次执行任务，有执行历史记录。

## Success Criteria

### Measurable Outcomes

- **SC-001**: 运维人员在 30 分钟内完成从下载到首次 Agent 对话的全流程
- **SC-002**: CLI 对话场景下，Agent 对简单查询（如天气）的端到端响应时间不超过 30 秒（含 LLM 推理时间）
- **SC-003**: Agent 在一次对话中至少完成 10 轮连续交互而不丢失上下文
- **SC-004**: Agent 记录的长期记忆在重启后的新会话中仍然可用
- **SC-005**: Web Service 支持至少 50 个并发请求而不报错
- **SC-006**: 定时任务在到达触发时间后 10 秒内开始执行
- **SC-007**: 所有审计记录（Tool 调用和 LLM 调用）在系统重启后完整可查
- **SC-008**: 所有三个验收 Demo（每日天气、每日科技日报、每日 GitHub 日报）在第四周末完整跑通

## Assumptions

- 目标部署环境为企业内网 Linux 服务器（Ubuntu 22.04+ / CentOS 8+ / Debian 11+ / Rocky Linux）
- 用户具备基本的命令行操作能力
- 企业已有或可申请 LLM API 访问权限（OpenAI 兼容协议）
- LLM Provider 的网络连通性由企业网络环境保障
- 核心阶段不支持用户认证和多租户——假设在内网环境中使用
- 核心阶段 Agent 的创建通过手动编写配置文件完成，不提供 Web UI
- 企业 IM 系统（企业微信/飞书/钉钉）均提供 Webhook 接入能力
- 系统为单实例部署，不支持集群或高可用
- 所有敏感配置（API Key 等）通过环境变量注入