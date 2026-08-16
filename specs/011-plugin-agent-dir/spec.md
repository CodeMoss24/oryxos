# Feature Specification: Plugin Agent Directory (一个目录定义一个会自己跑的 Agent)

**Feature Branch**: `029-lesson29-plugin-agent`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User description: "第29节需求：插件化 Agent——往 agents 目录丢一个目录就得到一个会自己跑的 Agent，零 Java、不动底座、到点自动触发。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 一个目录上线一个会自己跑的 Agent (Priority: P1)

业务方/运维想在系统上加一个业务 Agent（以"每日订单对账 Agent"为贯穿示例：每天早上核对交易库与清算库昨日订单的条数与金额，有差异按规范生成分级报告推送到运维群）。全程只写一个目录：主文件写编排正文、scripts 目录放确定性对账脚本、skills 目录放报告规范、REFERENCE.md 放字段字典。系统启动扫描到这个目录后，到它自己声明的时间点自动跑完"想 → 调系统能力 → 答 → 推送"、审计留账。全程不写一行 Java、不动底座一行代码。

**Why this priority**: 这是本节的核心命题——"一个目录定义一个会自己跑的 Agent"成立与否，是 OryxOS 能否被称作 Agent OS 的判定点。一个自足目录（正文常驻 + 子指令按需 + 脚本确定性 + 参考兜底）把这套模型的四样都用上，是活教材，必须先跑通。

**Independent Test**: 往 agents 目录放一个 daily-reconcile 目录 → 它出现在 Agent 列表里 → 到它 frontmatter 声明的时间点自动触发、webhook 收到推送、审计表有账。手动用对话或调用入口补跑一次，验证"人推"与"钟推"走同一条链路。

**Acceptance Scenarios**:

1. **Given** agents 目录下放了一个 daily-reconcile 目录（主文件 + scripts + skills + REFERENCE.md 四部分俱全），**When** 系统启动扫描，**Then** 该 Agent 出现在 Profile 列表，主文件正文进 system prompt，其声明的 schedules 已注册定时。
2. **Given** 该 Agent 已注册且到点触发，**When** 调度器触发，**Then** Agent 按正文编排：跑脚本拿确定性数据、读到需用报告规范才读子指令、拿不准才读参考、有差异分级推送并留审计账。
3. **Given** 该 Agent 已注册，**When** 改其目录里的正文，**Then** 下一次触发即用新说明，无需重启进程。

---

### User Story 2 - 渐进式披露：目录里的资源按需进上下文 (Priority: P2)

Agent 主文件正文在触发时进 system prompt（常驻），而目录里的子指令、参考、脚本不预先全塞进上下文，按正文指引用底座既有的能力按需取用：读子指令/参考用读文件能力、跑脚本用执行命令能力，脚本产出进上下文、代码不进。不新造工具、不做跨 Agent 的共享能力库、没有全局索引。

**Why this priority**: 这是"借 Anthropic Agent Skills 的形态"里真正拿来用的机制——渐进式披露。它决定了 Agent 目录里东西多起来时上下文不被一次性塞爆，且确定性操作不烧 token。是 US-1 能扩展到复杂 Agent 的支撑层。

**Independent Test**: 给一个 Agent 目录配较长的子指令/参考/脚本，触发后断言：正文进 system prompt；子指令/参考/脚本不预载；只有正文指引到那一步时，读文件/执行命令工具结果才出现在对话历史里，且脚本输出进上下文、脚本代码不进。

**Acceptance Scenarios**:

1. **Given** Agent 目录含较长的 skills 子指令与 REFERENCE.md，**When** 触发并组装 system prompt，**Then** 只有主文件正文进 system prompt，子指令与参考不预载。
2. **Given** 正文指引"运行脚本"或"读子指令"，**When** Agent 执行到该步骤，**Then** 通过底座读文件/执行命令能力按需取回，工具结果进对话历史；脚本产出进上下文、脚本代码不进。

---

### User Story 3 - 运行时注册：新 Agent 立即可见、定时留句柄 (Priority: P3)

把 Agent 索引改成可变并发结构，新增运行时注册/移除/查存在方法，让"启动扫描"和"运行时新增"走同一段代码、同一套校验（同一异常类型 + 同一消息）。调度器把启动批量注册的循环体抽成单个 Agent 的注册方法，并新增一张"任务 id → 调度句柄"映射表，供下节注销/更新用，与既有的任务锁表并存。

**Why this priority**: 这条去掉"新增 Agent 必须重启"这条尾巴，是给下一节（API 管理）铺路的地基。它本身不直接对业务方可见，但决定了下节能不能干净地做注销/更新。优先级低于前两个用户可见故事，但必须在核心阶段立好。

**Independent Test**: 启动后运行时调用注册方法新增一个 Agent → 立即 get 可见 → 其 schedules 在调度器里留下句柄。非法配置走与启动扫描完全相同的异常类型与消息。

**Acceptance Scenarios**:

1. **Given** 系统已启动，**When** 运行时注册一个合法 Agent，**Then** 注册后立即在索引中可见，且其 schedules 已在调度器注册并留有句柄。
2. **Given** 一个非法配置（缺必填项），**When** 走运行时注册，**Then** 抛出与启动扫描完全相同的异常类型与消息（两条来源同一套校验）。

---

### Edge Cases

- Agent 目录存在但主文件缺失 → 该目录被跳过，不阻断其余 Agent 加载（既有行为，回归保留）。
- 主文件缺必填项（名字/provider name）→ 索引层（core，不依赖 provider 模块）加载报错并点名是哪个 Agent、哪个字段；非法 Agent 不阻断其余加载。provider name 是否真映射到已注册 ChatModel 由装配层（boot）扫描后校验、不阻断其余加载（复用 16 节显式映射）。
- 主文件 tools 里引用底座未注册的能力 → 加载告警（不阻断），Agent 仍可加载。
- schedules 条目缺 id → 该条被跳过、其余条目照常解析（既有行为，回归保留）。
- Agent 目录下没有 scripts/skills/REFERENCE.md 等可选资源 → 正常加载，只是没有按需资源。
- 运行时重复注册同名 Agent → 覆盖既有条目（核心阶段不做同名冲突策略，放扩展）。
- 带脚本的 Agent：脚本能自己发网络请求、绕过内置 http 工具的域名白名单 → 沙箱对脚本只做"解释器 + 脚本目录"两道白名单，明确信任边界（容器/网络隔离放扩展）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST treat one `.oryxos/agents/<name>/` directory as one self-contained Agent: at minimum a main file whose frontmatter is this Agent's own profile (identity/provider/tools/notify_channels/schedules) and whose body is the task instruction. No separate Profile YAML is written.
- **FR-002**: System MUST, on startup, scan the agents directory; for each subdirectory it MUST parse the main file, derive a Profile value object the base already understands (identity/provider/tools/notify_channels/schedules mapped one-to-one), and register it; Agents declaring schedules MUST have those schedules handed to the scheduler.
- **FR-003**: System MUST apply progressive disclosure: the Agent body enters the system prompt on trigger (resident, fetched fresh each time without caching so edits take effect immediately); sub-instructions / references / scripts in the directory MUST NOT be preloaded — they are retrieved on demand via the base's existing read-file and run-command capabilities (script output enters context, script code does not). No new tools, no cross-Agent shared capability library, no global index.
- **FR-004**: System MUST source scheduling from the Agent itself: schedules written in frontmatter and derived into the Profile; the scheduler MUST continue to register cron from Profile.schedules unchanged. An Agent directory declaring schedules is automatically triggered at its declared time once scanned.
- **FR-005**: System MUST make the Agent index a mutable concurrent structure with runtime register(Profile)/remove(name)/exists(name) so that startup scan and runtime addition run the same code path and the same validation (same exception type + same message). The scheduler MUST extract the startup bulk-registration loop body into a per-Agent register method and add a task-id → schedule-handle map (for the next lesson's unregister/update), coexisting with the existing task-lock map.
- **FR-006**: System MUST validate in two layers without reversing the core→provider dependency direction: (a) the index layer (core, no dependency on the provider module) MUST check that the main file's required fields — name and provider name — are present and non-blank, failing load with an error that names which Agent and which field; this single core check is the code path shared by startup scan and runtime registration (same exception type + same message); (b) the provider-name → registered ChatModel mapping check is performed at the assembly layer (boot) after scanning, reusing the lesson-16 explicit mapping, non-blocking for the remaining Agents; tools referencing unregistered base capabilities MUST produce a load warning (non-blocking) — the ToolRegistry lives in core and is queried directly, no cross-module dependency.
- **FR-007**: System MUST confine Agent scripts via the base run-command capability: the sandbox whitelist allows the interpreter plus scripts restricted to that Agent's own scripts directory. System MUST make the trust boundary explicit: installing a scripted Agent equals trusting its author (scripts can issue network requests, bypassing the built-in http tool's domain whitelist); container/network isolation is deferred to the extension phase.

### Key Entities *(include if feature involves data)*

- **Agent Directory (`.oryxos/agents/<name>/`)**: one self-contained Agent. Contains a main file (frontmatter = profile, body = task instruction) plus optional `skills/*.md` (sub-instructions), `scripts/` (deterministic scripts), `REFERENCE.md` (reference). The directory name is the Agent's unique identity.
- **Derived Profile**: a value object the base already understands, derived from the Agent directory's frontmatter (identity/provider/tools/notify_channels/schedules), that lets the Agent directory reuse the entire base with zero changes.
- **Runtime Registration Handle (task-id → schedule handle)**: the scheduler's per-task handle map, retained so the next lesson can unregister/update a scheduled Agent without restart; coexists with the existing task-lock map.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dropping a single Agent directory into the agents folder makes it appear in the Agent list (`oryxos profile list` / `GET /api/v1/profiles`) with zero lines of Java written and zero lines of base code changed.
- **SC-002**: An Agent directory that declares schedules is automatically triggered at its declared time, with a webhook receiving the push and audit records (tool_invocations / task_executions) present — the full "think → call base capability → answer → push" chain runs without human initiation.
- **SC-003**: For a multi-file Agent directory, only the main-file body enters the system prompt; sub-instructions, references, and scripts are loaded on demand (measured by their absence from the initial prompt and presence as tool results only when the body guides that step), with script output entering context and script code never entering.
- **SC-004**: Editing an Agent directory's body takes effect on the next trigger without a process restart.
- **SC-005**: A directory-derived Agent and a hand-written Profile pass the identical validation (same exception type and message for the same misconfiguration) — the two sources are provably the same rule.
- **SC-006**: A runtime-registered Agent is immediately visible and leaves a schedule handle; `mvn clean verify` is fully green (no regression).

## Clarifications

### Session 2026-08-16

- Q: 当校验"provider 存在"时，索引（在 core 模块）看不到 provider 模块里的 ProviderService，这条 provider 校验该怎么落地？ → A: 两层校验、不反转 core→provider 依赖方向。索引层（core）只校验 provider name 必填非空（启动扫描与运行时注册走同一段、同一异常类型+同一消息）；provider name→ChatModel 映射校验放装配层（boot）扫描后做、不阻断其余加载、复用 16 节显式映射。ToolRegistry 本在 core，tools 未注册能力告警直接查、无跨模块依赖。

## Assumptions

- The base (lessons 16–28) — ProviderService, ReActLoop/AgentService, built-in Tool system and ToolRegistry, MemoryService, Sandbox, AgentScheduler, ContextLoader, SessionManager, Profile/ProfileRegistry/AgentLoader (scan & derive skeleton) — is already delivered and stable; this lesson builds on it rather than re-deriving.
- Directory name = Agent name = unique identity; core phase does not implement same-name conflict policy (deferred).
- Installing a scripted Agent equals trusting its author; core-phase sandbox confines scripts to interpreter + the Agent's own scripts directory only. Container/network isolation is deferred.
- File-watch hot-reload is NOT built this lesson (next lesson does it); "edit body → next trigger effective" is achieved by the existing no-cache ContextLoader behavior, already pinned by lesson 17.
- Cross-Agent global Skill shared-capability library / use_skill / global index is explicitly NOT built — each Agent is independent and self-contained.
- External dependencies: JDK 21 + Spring Boot 3.x BOM (verify via `mvn dependency:tree` before coding), SnakeYAML (already in use).
