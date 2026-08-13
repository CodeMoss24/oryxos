<!--
  Sync Impact Report:
  - Version: 1.0.0 → 1.1.0 (minor)
  - Amendment 记录(人类维护者授意,2026-08-13,Lesson 26):
    - 修改内容:"核心阶段 Web API 边界"与"实施节奏"中"10 个 REST 端点"的表述改为"核心阶段 10 个
      REST 端点(会话列表为只读扩展,共 11 个)"。
    - 理由:第 26 节管理台"会话列表"页需要集合数据,课件管理台提示词要求 GET /api/v1/sessions;
      该端点为只读查询,不违反"只查询不做创建"的边界精神。
    - 影响范围:Web API 端点计数从 10 增至 11;TechnicalSolution §7.2、CLAUDE.md 同步修正。
  - Modified principles: 核心阶段 Web API 边界(计数澄清)、实施节奏(第三周表述)
  - Added: 无
  - Removed: 无
  - Deferred TODOs: 无
-->

# OryxOS Constitution

## Core Principles

### I. JDK 21 + Spring Boot 3.x 单体架构
Java 21 和 Spring Boot 3.x 是整个项目的技术根基，不可替代。项目为 Maven 多模块（9个），编译目标为单二进制可部署 JAR，不支持拆分为微服务或多进程部署。扩展阶段可引入 GraalVM Native Image 压缩启动时间，但单体应用形态不变。

### II. 五大核心能力优先
核心阶段只交付五大能力：对接 LLM、ReAct 循环、Memory 记忆、Plugin Tool、Web Service。企业级治理层（多租户、SSO、完整审计、Tool Policy、RBAC、流式 SSE、WebSocket、限流）全部放扩展阶段。任何 AI agent 或开发者不得在核心阶段提前引入治理层能力。核心阶段是地基，不是终局——不得包装成完整企业级 Agent OS。

### III. 自实现 ReAct Loop
Agent 核心循环引擎必须自实现，不得依赖 Spring AI 的 Agent 抽象或任何外部框架的 Agent 运行时。自实现保证完全可控，保留未来定制循环行为（如并行 Tool 调用、Agent 间委托）的空间。核心循环逻辑应精简（约数十行 Java），使实现者完整掌握 Agent 工作机制。

### IV. Spring AI 使用边界（最容易被违反的原则）
Spring AI 在 OryxOS 中只能用作协议适配器和 schema 生成器，不得用作循环引擎：
- ✅ 只能用：Provider 抽象 + 协议转换 + `@Tool` 注解的 JSON Schema 生成
- ❌ 禁用：Spring AI 的自动 tool 执行机制（否则 tool 会被调两次）
- Tool 的实际调度和执行完全由 `ReActLoop` + `ToolExecutor` 控制
- 实施前必查，代码审查时必须核实这一点

### V. Plugin Tool 三档接入
Plugin Tool 接入优先选择门槛最低的方式：
- **方式一（主推）**：零代码，写 `AGENT.md` 目录 + 复用 MCP server
- **方式二**：轻代码，自己写 MCP server（任何语言）
- **方式三**：重代码，写 Java `@Tool` Bean（深度集成）
  
能用方式一就不用方式二，能用方式二就不用方式三。

### VI. SQLite + MEMORY.md 文件存储
核心阶段持久化方案为 SQLite（结构化数据）和 `MEMORY.md` 文件（长期记忆）。SQLite 用于 Session、审计表、定时任务状态；`MEMORY.md` 按 `## 核心记忆` / `## 归档记忆` 分区。向量检索、pgvector、LanceDB 等全部放扩展阶段。SQLite 的 `ALTER TABLE` 能力有限，`hibernate.ddl-auto=update` 不得用于表结构演进，需手动维护建表脚本或引入 Flyway/Liquibase。

### VII. 审计 Day One 落库
`tool_invocations` 和 `llm_calls` 两张审计表在核心阶段就必须写入 SQLite，不得只写日志。这是 OryxOS 差异化卖点"可审计"的数据地基，必须 day one 就立起来，避免后期从日志反解析返工。核心阶段不做审计查询接口，但写入必须从第一天就有。

### VIII. 接口先行
Sandbox、NotifyChannelAdapter、LongTermMemoryStore 等关键抽象必须先定接口再挂实现。接口签名不得携带任何实现细节（如"白名单""容器镜像""VM 配置"等字眼），确保未来换重隔离方案（白名单 → 容器隔离 → microVM）时只新增实现类，不改接口和调用方。

### IX. 每个 User Story 完成后有可演示 Demo
每个 user story 交付时必须有可演示的端到端 Demo，优先跑通而非追求完美。不满足"可演示"条件的 user story 不算完成。三个验收 Demo（每日天气、每日科技日报、每日 GitHub 日报）必须全部支持"钟推"（定时自动触发）和"人推"（手动补跑）两种触发方式。

## 技术约束与架构决策

### 技术栈
- **JDK 21+** + **Spring Boot 3.x**（virtual thread 撑高并发）
- **Spring AI** + **Spring AI Alibaba**（仅用于 Provider 抽象和协议转换）
- **Spring MVC**（HTTP API 服务层）
- **Picocli**（命令行工具）
- **SnakeYAML**（配置解析）
- **SQLite** + **Spring Data JPA**（持久化）
- **MCP Java SDK**（MCP Client 集成）
- **Logback** + **SLF4J**（结构化日志）
- **Micrometer** + **Prometheus**（扩展阶段）

### 模块结构（9个Maven模块）
| 模块 | 职责 |
|------|------|
| `oryxos-core` | 核心抽象：ReActLoop、PromptBuilder、ToolExecutor、AgentService、Session、Profile、AgentScheduler、ToolRegistry |
| `oryxos-provider` | ProviderService、Function Calling 适配、provider name → ChatModel 显式映射 |
| `oryxos-memory` | MemoryService 统一门面、LongTermMemory（三档后端）、MemoryTools |
| `oryxos-tool` | 三合一模块：内置 Tool、MCP Client、Sandbox、NotifyChannelAdapter（不拆成多个模块） |
| `oryxos-channel-cli` | CliChannel、oryxos chat |
| `oryxos-web` | WebServer、6个 ApiController、GlobalExceptionHandler |
| `oryxos-storage` | SQLite、各 Repository |
| `oryxos-cli` | Picocli 主入口、12个子命令、ConfigLoader |
| `oryxos-boot` | Spring Boot 启动模块 |

### Provider 显式映射
多 Provider 并存时，必须维护 provider name → `ChatModel` 的显式映射，不得靠类型扫描区分（Bean 类型相同会有歧义）。Profile 通过 provider name 引用对应模型。

### Sandbox 安全策略
- 核心阶段唯一实现 `WhitelistSandbox`：路径/命令/域名白名单
- 不得使用 `SecurityManager`（JDK 17 废弃、JDK 21 不可用）
- 脚本绕过白名单问题：装带脚本的 Agent = 信任该 Agent 作者，核心阶段对脚本只做"解释器 + 脚本目录"两道白名单

### 一个目录 = 一个 Agent
- `.oryxos/agents/<name>/AGENT.md` 定义 Agent（frontmatter = profile，正文 = 任务指令）
- `AGENT.md` 不是可执行 Tool，不进 `ToolRegistry`，加载归 `ContextLoader`
- Agent 子资源按渐进式披露原则，经 `read_file`/`shell` 按需取用

### 核心阶段 Web API 边界
核心阶段只做 10 个 REST 端点的查询和调用（会话列表为只读扩展，共 11 个端点），不做创建。核心阶段不做：认证、流式 SSE、WebSocket、RBAC、限流、Agent 目录上传、Scheduler 运行时增删、Memory append/clear/search。以上全部放扩展阶段。

### 运行环境约束
- 操作系统：Linux 主流发行版（Ubuntu 22.04+ / CentOS 8+ / Debian 11+ / Alibaba Cloud Linux 3 / Rocky Linux）
- LLM 协议：OpenAI 兼容协议是事实标准
- 配置加载：敏感配置（API key 等）通过环境变量注入，不得明文写在配置文件中

## 开发工作流与质量要求

### Spec-Kit 工作流
主体开发使用 Spec-Kit 工作流：
1. `constitution.md` / `spec.md` / `plan.md` 一次性准备好
2. 5 个 user story 按 `/speckit.tasks` 拆任务
3. 每个 user story 完成后必跑 `/speckit.analyze` 做 spec 一致性检查
4. 增量阶段切手动提示词 + Claude Code

### 可演示原则
每个 user story 完成后必须能端到端 Demo，不可演示的 user story 不算完成。优先跑通完整链路而非追求完美代码。

### 实施节奏（4周 × 3小时 = 12小时）
1. **第一周**：对接 LLM + ReAct 循环 → `oryxos chat` 多轮对话，Agent 调 HTTP Tool 完成天气查询
2. **第二周**：Memory + Tool 体系 → Agent 记住偏好、调文件/MCP server
3. **第三周**：Web Service → 核心阶段端点（含会话列表只读扩展）完整可用
4. **第四周**：多 Agent 演示 + 定时任务 + 工程化收尾 → 三个 Demo 全跑通

### 常见陷阱清单
开发时必须规避以下已知陷阱：
- 启用 Spring AI 自动 tool 执行（❌）→ 禁用（✅）
- Provider 用类型扫描区分（❌）→ 显式映射（✅）
- Tool 拆成多模块（❌）→ 合并为 `oryxos-tool` 一个模块（✅）
- 审计表只写日志不落库（❌）→ day one 写入 SQLite（✅）
- 用 `SecurityManager` 做沙箱（❌）→ 用 `WhitelistSandbox`（✅）
- 给核心阶段加治理层（❌）→ 放扩展阶段（✅）
- 改了非协商原则（❌）→ 停下重新讨论，AI agent 不得自行修改 constitution（✅）

## Governance

本宪法是 OryxOS 项目的最高指导文件，所有开发决策、代码审查、架构评审均须以本宪法为准。具体治理规则如下：

1. **不可修改性**：Core Principles 章节的原则（I-IX）不可由 AI agent 自行修改。任何修改提议必须由人类维护者提出，经项目讨论后通过 Amendment 流程执行。
2. **Amendment 流程**：修改宪法需提交书面变更说明，注明修改内容、理由、影响范围，经项目维护者批准后更新版本号并记录在案。
3. **版本策略**：遵循语义化版本（MAJOR.MINOR.PATCH）。MAJOR = 原则删除或重新定义；MINOR = 新增原则或实质性扩展；PATCH = 澄清、措辞修正、非语义优化。
4. **合规审查**：代码审查时必须验证变更不违反本宪法的任何原则。对有疑义的变更，停下讨论，不得擅自推进。
5. **冲突解决**：本宪法与其它项目文档（包括 CLAUDE.md 和 TechnicalSolution.md）冲突时，以本宪法为准。
6. **`AGENT.md` 与 Agent 目录**：Agent 目录的 `AGENT.md` 正文由 `ContextLoader` 注入 system prompt，不进 `ToolRegistry`。Agent 不是 Tool，Tool 不是 Agent，此边界不可模糊。
7. **执行监督**：所有 AI agent 在本仓库工作前必须阅读本宪法。发现违反宪法的代码或行为，必须立即指出并阻止。

**Version**: 1.1.0 | **Ratified**: 2026-08-01 | **Last Amended**: 2026-08-13