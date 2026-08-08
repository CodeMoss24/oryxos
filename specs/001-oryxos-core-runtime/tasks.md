---
description: "OryxOS 核心运行时内核的任务列表"
---

# 任务清单：OryxOS 核心运行时

**输入**：`/specs/001-oryxos-core-runtime/` 下的设计文档

**前置条件**：plan.md（必需）、spec.md（必需）、research.md、data-model.md、contracts/

**说明**：任务按 User Story 组织，每个 User Story 可独立实现和测试。项目已有 69 个 Java 文件（~3048 行），任务重点是修通编译链路、补齐缺失实现、端到端验证。

## 格式约定：`[ID] [P?] [Story] 描述`

- **[P]**：可并行执行（不同文件，无依赖关系）
- **[Story]**：所属 User Story（如 US1、US2）
- 描述中**必须包含文件路径**

---

## 第一阶段：环境搭建（现有代码编译与基础设施）⚙️

**目标**：确认已有 69 个 Java 文件跨 9 个 Maven 模块能编译通过，补齐缺失的 Spring Boot 配置。

- [ ] T001 验证 Maven 多模块 POM 结构：父 POM 在 `pom.xml`，以及全部 9 个子模块的 `pom.xml`
- [ ] T002 [P] 验证根 `pom.xml` 中的依赖版本：Spring Boot 3.3.x、Spring AI 1.0.0-M4、Picocli 4.7.x、SnakeYAML 2.3、SQLite JDBC 3.46.x
- [ ] T003 [P] 确认 Spring Boot 启动入口在 `oryxos-boot/src/main/java/com/oryxos/boot/OryxosApplication.java`
- [ ] T004 [P] 配置 Logback 日志在 `oryxos-boot/src/main/resources/logback-spring.xml`（结构化日志，控制台 + 文件输出）
- [ ] T005 [P] 创建 `.oryxos/` 工作区目录结构：`agents/`、`skills/`、`output/`、`memory/MEMORY.md`、`sessions/`、`logs/`、`mcp_servers.yaml`
- [ ] T006 执行 `mvn clean compile -DskipTests` 修复所有 9 个模块的编译错误（重点：Spring DI 装配、Bean 配置缺失、import 解析）

**检查点**：项目编译通过，`.oryxos/` 工作区结构就绪。

---

## 第二阶段：基础架构（阻塞性前置条件）🏗️

**目标**：所有 User Story 的共同依赖——数据模型实体、配置加载、错误响应框架、核心接口定义。

- [ ] T007 创建统一 API 响应模型 `ApiResult<T>` 和错误码在 `oryxos-core/src/main/java/com/oryxos/core/common/ApiResult.java`
- [ ] T008 [P] 创建全局异常处理器 `GlobalExceptionHandler`（400/404/500/503/504）在 `oryxos-web/src/main/java/com/oryxos/web/handler/GlobalExceptionHandler.java`
- [ ] T009 [P] 实现 SnakeYAML 配置加载器 `ConfigLoader` 在 `oryxos-cli/src/main/java/com/oryxos/cli/config/ConfigLoader.java`（加载 `.oryxos/mcp_servers.yaml`，支持环境变量插值 `${VAR}`）
- [ ] T010 [P] 创建 `Profile` 模型在 `oryxos-core/src/main/java/com/oryxos/core/profile/Profile.java`（frontmatter 字段：name、provider、model、tools、notify_channels、schedules、bootstrap、settings）
- [ ] T011 [P] 创建 `Session` 模型在 `oryxos-core/src/main/java/com/oryxos/core/session/Session.java`（session_id、profile_name、channel、user_id、messages_json、status、created_at、last_active_at、archived_at）
- [ ] T012 [P] 创建 `OryxTool` 接口和 `ToolResult` 类在 `oryxos-core/src/main/java/com/oryxos/core/tool/OryxTool.java`（getName、getDescription、getInputSchema、execute）
- [ ] T013 [P] 创建 `ToolRegistry` 在 `oryxos-core/src/main/java/com/oryxos/core/tool/ToolRegistry.java`（注册 + 按名称查找）
- [ ] T014 [P] 通过 Spring Data JPA Entity 实现 SQLite 五张表，在 `oryxos-storage/src/main/java/com/oryxos/storage/entity/`：
  - `SessionEntity`（sessions 表）
  - `ToolInvocationEntity`（tool_invocations 表）
  - `LlmCallEntity`（llm_calls 表）
  - `ScheduledTaskEntity`（scheduled_tasks 表）
  - `TaskExecutionEntity`（task_executions 表）
- [ ] T015 [P] 创建对应的 JPA Repository 接口在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/`（SessionRepository、ToolInvocationRepository、LlmCallRepository、ScheduledTaskRepository、TaskExecutionRepository）
- [ ] T016 [P] 配置 SQLite 数据源和 JPA 在 `oryxos-boot/src/main/resources/application.yml`（driver、ddl-auto、连接池）
- [ ] T017 创建 `AgentLoader` 在 `oryxos-core/src/main/java/com/oryxos/core/loader/AgentLoader.java`（扫描 `.oryxos/agents/*/AGENT.md`，解析 frontmatter 推导 Profile）
- [ ] T018 [P] 创建 `ProfileContext` ThreadLocal 持有者在 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileContext.java`
- [ ] T019 [P] 创建 `Sandbox` 接口和 `SandboxAction` / `SandboxViolationException` 在 `oryxos-core/src/main/java/com/oryxos/core/sandbox/Sandbox.java`
- [ ] T020 [P] 实现 `WhitelistSandbox` 在 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/WhitelistSandbox.java`（路径/命令/域名模式白名单）
- [ ] T021 [P] 创建 `NotifyChannelAdapter` 接口和 `NotifyTarget` 在 `oryxos-core/src/main/java/com/oryxos/core/notify/NotifyChannelAdapter.java`
- [ ] T022 [P] 创建 `MemoryService` 接口在 `oryxos-core/src/main/java/com/oryxos/core/memory/MemoryService.java`（save、recall、getCore、getArchival）
- [ ] T023 [P] 创建 `McpClientService` 在 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClientService.java`（连接 MCP server，获取工具列表）
- [ ] T024 [P] 实现 `McpToolAdapter` 在 `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpToolAdapter.java`（从 McpClientService 获取 MCP Tool 列表，包装为 OryxTool 注册到 ToolRegistry）
- [ ] T025 执行 `mvn clean compile -DskipTests` 修复基础架构阶段引入的编译错误

**检查点**：基础架构就绪——所有核心接口、实体、Repository、Service 都已定义并编译通过。

---

## 第三阶段：User Story 1 — 运维人员部署并初始化 OryxOS（优先级：P1）🎯 MVP

**目标**：企业运维人员通过命令行初始化工作区、配置 LLM Provider、验证系统运行状态。

**独立验证**：在空服务器上执行 `init`、`status`、`provider list` 三个命令，能显示系统状态和已配置的 Provider 列表。

- [ ] T026 [P] [US1] 实现 `ProviderService` 在 `oryxos-provider/src/main/java/com/oryxos/provider/ProviderService.java`，维护 provider name → `ChatModel` 显式映射（通过 Spring AI 支持 OpenAI 兼容协议）
- [ ] T027 [P] [US1] 实现 `oryxos init` Picocli 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/InitCommand.java`（创建 `.oryxos/` 工作区，生成 AGENTS.md/SOUL.md/USER.md 模板，初始化 MEMORY.md）
- [ ] T028 [P] [US1] 实现 `oryxos status` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/StatusCommand.java`（显示运行模式、已注册 Provider、可用 Tool 数量）
- [ ] T029 [P] [US1] 实现 `oryxos provider list` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ProviderListCommand.java`
- [ ] T030 [US1] 创建默认 `AGENTS.md` 引导文件模板在 `.oryxos/AGENTS.md`（项目级 agent 行为说明）
- [ ] T031 [US1] 创建默认 `SOUL.md` 引导文件模板在 `.oryxos/SOUL.md`（默认 agent 人格设定）
- [ ] T032 [US1] 创建默认 `USER.md` 引导文件模板在 `.oryxos/USER.md`（用户偏好占位符）
- [ ] T033 [US1] 配置 Provider 从环境变量加载 API key 在 `application.yml`（`${DEEPSEEK_API_KEY}` 模式）
- [ ] T034 [US1] 端到端验证：`init` → `status` → `provider list` 流程完整跑通

**检查点**：US-1 完成——运维人员可初始化工作区并验证系统状态。

---

## 第四阶段：User Story 2 — 业务人员通过 CLI 与 Agent 对话（优先级：P1）

**目标**：业务人员在终端中与 Agent 进行多轮对话，Agent 能理解任务、调用工具（如 HTTP 天气查询）并返回有意义的结果。

**独立验证**：启动 `oryxos chat`，输入"今天北京的天气怎么样"，Agent 通过工具调用返回天气信息，至少 3 轮连续对话。

- [ ] T035 [P] [US2] 实现 `ReActLoop` 在 `oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java`（Reason-Act 循环：组装 Prompt → 调 LLM → 检查 Tool 调用 → 执行 Tool → 重复，最多 10 次迭代，**不得启用 Spring AI 自动 tool 执行**）
- [ ] T036 [P] [US2] 实现 `PromptBuilder` 在 `oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java`（四段拼接：system prompt + 日期 + Bootstrap 文件 + Memory + 对话历史截断至 `maxHistoryTurns` + 可用 Tool 列表）
- [ ] T037 [P] [US2] 实现 `ToolExecutor` 在 `oryxos-core/src/main/java/com/oryxos/core/react/ToolExecutor.java`（从 ToolRegistry 找到 Tool → 执行 Sandbox.enforce → 执行 → 写入 `tool_invocations` 审计表）
- [ ] T038 [P] [US2] 实现 `http_get` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`（HTTP GET，通过 Sandbox 做域名白名单检查）
- [ ] T039 [P] [US2] 实现 `http_post` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`（HTTP POST，通过 Sandbox 做域名白名单检查）
- [ ] T040 [P] [US2] 实现 `read_file` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`（读取文件内容，通过 Sandbox 做路径白名单检查）
- [ ] T041 [P] [US2] 实现 `write_file` 工具在 `FileTools.java`（写入文件内容，通过 Sandbox 做路径白名单检查）
- [ ] T042 [P] [US2] 实现 `list_dir` 工具在 `FileTools.java`（列出目录内容，通过 Sandbox 做路径白名单检查）
- [ ] T043 [P] [US2] 实现 `shell` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`（执行 bash 命令，带超时和命令白名单检查）
- [ ] T044 [P] [US2] 实现 `AgentService` 在 `oryxos-core/src/main/java/com/oryxos/core/agent/AgentService.java`（process(Session, String)：设置 ProfileContext → ReActLoop.run → 持久化 Session → finally 清理 ProfileContext）
- [ ] T045 [P] [US2] 实现 `CliChannel` 在 `oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java`（读取-执行-输出循环，多轮对话，消息传给 AgentService）
- [ ] T046 [P] [US2] 实现 `oryxos chat --profile <name>` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ChatCommand.java`（启动 Spring 上下文，装配 CliChannel）
- [ ] T047 [P] [US2] 创建天气 Agent 配置文件 `.oryxos/agents/weather-agent/AGENT.md`（配置 `http_get` 工具和天气 API Provider）
- [ ] T048 [US2] 在 ReActLoop 中接入 LLM 调用审计——每次 LLM 调用写入 `llm_calls` 表（provider、model、tokens、duration_ms）
- [ ] T049 [US2] 在 ToolExecutor 中接入 Tool 调用审计——每次 Tool 执行写入 `tool_invocations` 表（tool_name、input、result、success、duration_ms）
- [ ] T050 [US2] 在 PromptBuilder 中实现对话历史截断（保留最近 N 轮，N 由 Profile `settings.max_history_turns` 配置，默认 20 轮）
- [ ] T051 [US2] 端到端验证：`oryxos chat --profile weather-agent` → 多轮对话 → Agent 调 http_get 查天气 → SQLite 中可查到审计记录

**检查点**：US-2 完成——Agent 可多轮对话、调用 HTTP/文件/Shell 工具、审计记录持久化。

---

## 第五阶段：User Story 3 — Agent 记住用户偏好并在后续对话中应用（优先级：P2）

**目标**：用户在对话中表达偏好（如"我关注 AI 和芯片方向"），Agent 记住并在后续新会话中自动参考。

**独立验证**：对话中说"关注 AI 方向"，退出后开新会话问"今天有什么新闻"，Agent 回答优先覆盖 AI 相关内容。

- [ ] T052 [P] [US3] 实现 `MarkdownMemoryStore` 在 `oryxos-memory/src/main/java/com/oryxos/memory/store/MarkdownMemoryStore.java`（读写 `.oryxos/memory/MEMORY.md`，强制 `## 核心记忆` / `## 归档记忆` 分区）
- [ ] T053 [P] [US3] 实现 `MemoryService` 默认实现在 `oryxos-memory/src/main/java/com/oryxos/memory/MemoryServiceImpl.java`（saveMemory、recallMemory、getCoreMemory、getArchivalMemory 委托给后端存储）
- [ ] T054 [P] [US3] 实现 `save_memory` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/MemoryTools.java`（scope=CORE 存核心区，scope=ARCHIVAL 存档区）
- [ ] T055 [P] [US3] 实现 `recall_memory` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/MemoryTools.java`（关键词检索归档记忆）
- [ ] T056 [US3] 在 PromptBuilder 中集成记忆注入——核心记忆（永不截断、全量注入），归档记忆按关键词匹配注入
- [ ] T057 [US3] 创建记忆测试 Agent 配置文件 `.oryxos/agents/memory-test-agent/AGENT.md`（配置 `save_memory`、`recall_memory` 工具）
- [ ] T058 [US3] 端到端验证：保存偏好 → 退出 → 新会话 → 召回偏好 → Agent 在回答中应用偏好

**检查点**：US-3 完成——Agent 跨会话持久化和检索长期记忆。

---

## 第六阶段：User Story 4 — 开发者通过 REST API 集成 OryxOS（优先级：P2）

**目标**：企业业务系统通过 HTTP API 调用 OryxOS：创建会话、发送消息、查询历史、获取 Agent/Tool 列表、健康检查。

**独立验证**：`curl POST /api/v1/agents/{name}/invoke` 返回 JSON 格式的 Agent 输出。

- [ ] T059 [P] [US4] 实现 `POST /api/v1/sessions` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/SessionController.java`（创建会话，返回 session_id）
- [ ] T060 [P] [US4] 实现 `POST /api/v1/sessions/{id}/messages` 在 SessionController（发送消息，触发 AgentService.process，返回响应，60 秒超时返回 504）
- [ ] T061 [P] [US4] 实现 `GET /api/v1/sessions/{id}` 在 SessionController（返回会话历史，最多 100 条）
- [ ] T062 [P] [US4] 实现 `DELETE /api/v1/sessions/{id}` 在 SessionController（归档会话，设 status=archived）
- [ ] T063 [P] [US4] 实现 `POST /api/v1/agents/{name}/invoke` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/AgentController.java`（无状态 Agent 调用，60 秒超时返回 504）
- [ ] T064 [P] [US4] 实现 `GET /api/v1/profiles` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/ProfileController.java`（从 AgentLoader 列出所有 Profile）
- [ ] T065 [P] [US4] 实现 `GET /api/v1/memory` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/MemoryController.java`（返回核心 + 归档记忆）
- [ ] T066 [P] [US4] 实现 `GET /api/v1/tools` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/ToolController.java`（从 ToolRegistry 列出所有已注册工具）
- [ ] T067 [P] [US4] 实现 `GET /api/v1/health` 在 `oryxos-web/src/main/java/com/oryxos/web/controller/SystemController.java`（健康检查，包含 Provider 状态）
- [ ] T068 [P] [US4] 实现 `GET /api/v1/info` 在 SystemController（版本号、Profile 数量、Tool 数量、运行时长）
- [ ] T069 [US4] 配置 CORS 在 `oryxos-web/src/main/java/com/oryxos/web/config/WebConfig.java`（核心阶段全开 `*`）
- [ ] T070 [US4] 实现 `oryxos serve` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ServeCommand.java`（以 Web 服务器模式启动 Spring Boot，默认 8080 端口）
- [ ] T071 [US4] 实现单条消息最大 32KB 校验在 `oryxos-web` 请求处理层
- [ ] T072 [US4] 端到端验证：启动服务 → curl 全部 10 个端点 → 确认 JSON 响应格式匹配 API 契约

**检查点**：US-4 完成——全部 10 个 REST 端点功能正常，响应格式符合契约。

---

## 第七阶段：User Story 5 — Agent 按定时计划自动运行并推送结果（优先级：P3）

**目标**：运维人员配置"每日天气"Agent，每天早上 8 点自动查询天气推送到企业 IM 群。支持"钟推"（定时自动触发）和"人推"（手动补跑）两种模式。

**独立验证**：配置 5 分钟 cron 的 Agent，验证到点自动执行并在推送渠道收到结果。

- [ ] T073 [P] [US5] 实现 `AgentScheduler` 在 `oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java`（扫描带 schedules 的 Profile，注册 cron 任务，创建 scheduler-channel Session，调用 AgentService.process）
- [ ] T074 [P] [US5] 实现 `WebhookNotifyAdapter` 在 `oryxos-tool/src/main/java/com/oryxos/tool/notify/WebhookNotifyAdapter.java`（POST 到 webhook URL，复用 HTTP 域名白名单通过 Sandbox.enforce）
- [ ] T075 [P] [US5] 实现 `notify` 工具在 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/NotifyTools.java`（通过 NotifyChannelAdapter 发送到 Profile 配置的 notify_channels）
- [ ] T076 [P] [US5] 在 AgentScheduler 中接入 TaskExecution 持久化（每次执行写入 task_executions 表：session、success/failure、duration）
- [ ] T077 [US5] 实现 `oryxos gateway` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/GatewayCommand.java`（启动 Spring 上下文 + Scheduler 守护进程）
- [ ] T078 [US5] 创建完整功能的每日天气 Agent 配置文件 `.oryxos/agents/daily-weather/AGENT.md`（配置 schedule、notify_channels、http_get 工具）
- [ ] T079 [US5] 端到端验证：运行 `oryxos gateway` → 等待 cron 触发 → 验证天气结果通过 webhook 推送 → 手动 curl 调用也能正常工作

**检查点**：US-5 完成——定时 Agent 自动执行并推送结果，"钟推"和"人推"双模式验证通过。

---

## 第八阶段：收尾与跨面关注点 🔧

**目标**：CLI 命令完整度、三个验收 Demo 部署、审计验证、Quickstart 全流程跑通。

- [ ] T080 [P] 实现 `oryxos profile list` 命令在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ProfileListCommand.java`
- [ ] T081 [P] 实现 `oryxos profile create <name>` 命令（生成最小 AGENT.md 模板）
- [ ] T082 [P] 实现 `oryxos profile show <name>` 命令
- [ ] T083 [P] 实现 `oryxos profile delete <name>` 命令（删除 Agent 目录）
- [ ] T084 [P] 实现 `oryxos tool list` 命令
- [ ] T085 [P] 实现 `oryxos session list` 命令
- [ ] T086 [P] 创建每日科技日报演示 Agent 在 `.oryxos/agents/daily-tech-digest/AGENT.md`，含 `skills/` 子指令和 MCP server 引用
- [ ] T087 [P] 创建每日 GitHub 日报演示 Agent 在 `.oryxos/agents/github-daily/AGENT.md`，含 `scripts/` 目录和 shell 工具
- [ ] T088 [P] 配置 `mcp_servers.yaml`，添加示例 MCP server 条目（如新闻聚合服务）
- [ ] T089 验证审计追踪：Agent 运行后检查 `tool_invocations` 和 `llm_calls` 表中有记录
- [ ] T090 [P] 验证会话跨重启持久化：运行对话 → 停止 JVM → 重启 → 通过 API 查询会话历史
- [ ] T091 执行 quickstart.md 端到端验证——三个演示 Agent 在"钟推"和"人推"两种模式下均产出预期结果
- [ ] T092 [P] 最终代码质量审查：确认未启用 Spring AI 自动 tool 执行（宪法原则 IV 合规检查）
- [ ] T093 [P] 最终代码质量审查：确认 Sandbox 接口签名中无任何实现细节术语（宪法原则 VIII 合规检查）
- [ ] T094 执行 `mvn clean package -DskipTests`，确认 BUILD SUCCESS，fat JAR 在 `oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar`

**检查点**：三个演示 Agent 全部可用。Quickstart 全流程通过。Fat JAR 编译通过。

---

## 依赖关系与执行顺序

### 阶段依赖

- **第一阶段（环境搭建）**：无依赖，可立即开始
- **第二阶段（基础架构）**：依赖第一阶段完成——阻塞所有 User Story
- **US-1（第三阶段）**：依赖第二阶段——阻塞后续所有 Story
- **US-2（第四阶段）**：依赖 US-1（需要 Provider + Profile + CLI 框架）——阻塞 US-3
- **US-3（第五阶段）**：依赖 US-2（记忆注入依赖 PromptBuilder + ReActLoop）
- **US-4（第六阶段）**：依赖 US-1 + US-2（需要 AgentService + Session），可与 US-3 部分并行
- **US-5（第七阶段）**：依赖 US-2 + US-3 + US-4（需要 ReAct + Notify + Session），阻塞 Demo 验证
- **第八阶段（收尾）**：依赖所有 User Story 完成

### User Story 依赖关系图

```
US-1 (Provider+Init) ─→ US-2 (ReAct+CLI) ─→┐
                                             ├→ US-5 (Scheduler) ─→ 收尾
                    US-3 (Memory) ──────────→┘
                    US-4 (Web API) ─────────→┘
```

- **US-3（Memory）和 US-4（Web Service）** 在 US-2 完成后可并行推进
- **所有 [P] 标记的任务** 在同一阶段内无文件冲突，可并行

---

## 并行执行示例：User Story 2（ReAct + CLI）

```bash
# 核心引擎组件可并行开发：
Task: T035 实现 ReActLoop
Task: T036 实现 PromptBuilder
Task: T037 实现 ToolExecutor

# 工具实现可并行（HTTP + 文件 + Shell）：
Task: T038 实现 http_get
Task: T039 实现 http_post
Task: T040 实现 read_file
Task: T041 实现 write_file
Task: T042 实现 list_dir
Task: T043 实现 shell

# Agent 基础设施可并行：
Task: T044 实现 AgentService
Task: T045 实现 CliChannel
Task: T046 实现 chat 命令
```

---

## 实施策略

### MVP 优先（US-1 + US-2）

1. 完成第一阶段：环境搭建（编译修复）
2. 完成第二阶段：基础架构（实体、接口、Repository）
3. 完成第三阶段：US-1（Provider + init + status）
4. 完成第四阶段：US-2（ReAct + CLI 对话）
5. **停下来验证**：`oryxos chat --profile weather-agent` 端到端跑通
6. 这是**第一周交付物**：多轮对话带 Tool 调用

### 增量交付

| 周期 | 内容 | 可演示成果 |
|------|------|-----------|
| **第一周** | 环境搭建 + 基础架构 + US-1 + US-2 | `oryxos chat` 多轮对话 + 天气查询 |
| **第二周** | US-3（Memory）+ US-4（Web Service） | 记忆跨会话 + 10 个 REST 端点 |
| **第三周** | US-5（Scheduler + Notify） | 定时自动触发 + Webhook 推送 |
| **第四周** | 收尾 + 三个演示 Agent + Quickstart | 三个验收 Demo 全跑通 |

### 多人并行策略

1. Phase 1-2：单人完成基础设施搭建
2. US-2 内部：引擎组（T035-T037）+ 工具组（T038-T043）+ CLI 组（T044-T046）
3. US-3（Memory）和 US-4（Web Service）可分配两人并行开发
4. US-5 依赖前序完成，单人负责
5. 收尾阶段各命令和 Demo Agent 配置均可并行

---

## 注意事项

- **[P] 标记** = 不同文件、无依赖关系，可并行实现
- **[US1]–[US5]** 标记将任务映射到特定 User Story，便于追踪
- 每个 User Story 可独立完成并独立验证
- **审计追踪 Day One**：T048-T049 在 US-2 阶段就接入，不推迟
- **宪法原则 IV 检查**：在 US-2 完成后必须确认没有启用 Spring AI 自动 tool 执行
- 建议每个任务或逻辑组完成后提交一次代码
- 每个检查点可停下独立验证当前 Story 是否完成