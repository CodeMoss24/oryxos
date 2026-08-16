---

description: "Task list for Plugin Agent Directory (Lesson 29)"
---

# Tasks: Plugin Agent Directory (一个目录定义一个会自己跑的 Agent)

**Input**: Design documents from `/specs/011-plugin-agent-dir/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/agent-directory.md, quickstart.md

**Tests**: 本节课件"验收 harness"显式给出六个测试类，按 lesson-dev 门禁 harness 关键回归测试原样落地（测试方法名英文、课件原文进 `@DisplayName`、断言逐条保真）。测试任务先于或伴随对应实现任务（harness 先行）。

**Organization**: 按用户故事分组。本节三个用户故事共享同一批 core 既有类改造，故 Phase 2 Foundational 放跨故事的共享改造（ProfileRegistry 并发化、AgentScheduler.registerProfile+句柄表），各故事的独立面在 Phase 3/4/5。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 归属用户故事（US1/US2/US3）
- 含确切文件路径

## Path Conventions

- oryxos-core: `oryxos-core/src/main/java/com/oryxos/core/`，测试 `oryxos-core/src/test/java/com/oryxos/core/`
- oryxos-boot: `oryxos-boot/src/main/java/com/oryxos/boot/`
- 示例 Agent: `.oryxos/agents/daily-reconcile/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖核实与分支就绪

- [ ] T001 在仓库根跑 `mvn dependency:tree -pl oryxos-core -am` 核实 snakeyaml + spring-context（TaskScheduler 来源）在锁定 BOM 里存在，贴关键输出到实现笔记
- [ ] T002 确认在 `029-lesson29-plugin-agent` 分支上（`git branch --show-current`）

**Checkpoint**: 依赖锁定核实、分支正确

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 跨用户故事的 core 既有类改造——运行时注册地基（FR-005）。MUST 完成后各故事才可展开。

**⚠️ CRITICAL**: US1/US2/US3 都依赖本相位

- [ ] T003 [P] 改 `ProfileRegistry` 的 `profiles` 由 `LinkedHashMap` → `ConcurrentHashMap`（文件 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileRegistry.java`）；`register/remove/exists/find/list` 签名不变，确认并发可见语义
- [ ] T004 [P] 在 `AgentScheduler` 抽 `registerProfile(Profile)`（public，抽自 `registerAll` 循环体：幂等检查→CronTrigger schedule→store.register→taskRefs.put），新增 `scheduledTasks` 字段 `Map<String, ScheduledFuture<?>>`（ConcurrentHashMap），捕获 `taskScheduler.schedule(...)` 返回的 `ScheduledFuture` 入表（与既有 taskLocks/taskRefs/scheduledTaskIds 并存）；`registerAll` 改为遍历调 `registerProfile`（文件 `oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java`）。写前 H3 核实 `TaskScheduler.schedule(Runnable, Trigger)` 返回 `ScheduledFuture<?>`

### Foundational 测试（harness 先行）

- [ ] T005 [P] 新建 `ProfileRegistryRuntimeTest`（`oryxos-core/src/test/java/com/oryxos/core/profile/ProfileRegistryRuntimeTest.java`）：`register()` 后立即 `find()`/`exists()` 可见；`remove()` 后不可见；并发语义冒烟
- [ ] T006 [P] 新建 `AgentSchedulerRegisterTest`（`oryxos-core/src/test/java/com/oryxos/core/scheduler/AgentSchedulerRegisterTest.java`）：`registerProfile(profile)` 后 `scheduledTasks` 含该 schedule id 的 `ScheduledFuture` 句柄；cron/时区来自 `Profile.schedules`；幂等（重复注册不重复调度）。Mock `TaskScheduler`，`when(taskScheduler.schedule(...)).thenReturn(mock(ScheduledFuture.class))`

**Checkpoint**: 运行时注册地基就位，跑 T005/T006 应绿（先红后绿）

---

## Phase 3: User Story 1 - 一个目录上线一个会自己跑的 Agent (Priority: P1) 🎯 MVP

**Goal**: 往 `.oryxos/agents/` 丢一个自足目录 → 扫描派生 Profile → 注册 → 带 schedules 的进调度器 → 到点自动跑、审计留账。零 Java、不动底座。

**Independent Test**: 放一个 `daily-reconcile` 目录 → 出现在 Profile 列表 → schedules 已注册（句柄表有句柄）→ 手动 invoke 补跑走同一条 `AgentService.process` 链路。

### Tests for User Story 1 (harness 先行)

- [ ] T007 [P] [US1] 扩 `AgentLoaderTest`（`oryxos-core/src/test/java/com/oryxos/core/profile/AgentLoaderTest.java`）：补"缺 provider 必填项 → 报错点名 `Agent '<name>': missing required field 'provider.name'`"；补"缺 name（目录名为空场景）报错点名"；既有用例保持绿
- [ ] T008 [P] [US1] 新建 `DeriveProfileTest`（`oryxos-core/src/test/java/com/oryxos/core/profile/DeriveProfileTest.java`）：frontmatter 各字段（identity/provider/tools/notify_channels/schedules）正确映射到 Profile；**`schedules` 原样带进派生 Profile**（定时来自 Agent 的直接证据）；`notify_channels` 的 `${ENV}` 占位解析
- [ ] T009 [P] [US1] 新建 `AgentScanRegisterTest`（`oryxos-core/src/test/java/com/oryxos/core/profile/AgentScanRegisterTest.java`）：扫一个含 N 个 Agent 目录的目录 → `ProfileRegistry` 出现 N 个；带 schedules 的都进了 `AgentScheduler`（句柄表有句柄）；坏目录/缺必填项目录不阻断其余

### Implementation for User Story 1

- [ ] T010 [US1] 在 `AgentLoader.deriveProfile` 加缺必填项校验：`provider.name` 为空/null → 抛 `IllegalArgumentException("Agent '<name>': missing required field 'provider.name'")`；`name`（目录名）为空 → 抛 `IllegalArgumentException("Agent '<name>': missing required field 'name'")`（文件 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java`）。`scanAndRegister` 既有 try/catch 已兜底不阻断
- [ ] T011 [US1] 在 `AgentLoader` 加 `listResources(Path agentDir)` 方法（返回 `scripts`/`skills`/`REFERENCE.md` 路径存在性，供 harness 断言资源识别，不进 Profile）（同文件）
- [ ] T012 [US1] 在 `AgentLoader` 加 `warnUnregisteredTools(Profile, ToolRegistry)` 方法：tools 中 name 不在 `ToolRegistry.find(name)` → `log.warn` 不阻断（同文件）；包级/public 方法参数注入 ToolRegistry，构造期不硬依赖
- [ ] T013 [US1] 在装配层 `OryxOsApplication.run` 扫描后补 provider 真实性校验：遍历 `profileRegistry.list()`，对 `provider.name` 调 16 节 `ProviderService` 校验映射到已注册 ChatModel，未映射 `log.warn` 不阻断；扫描循环里对每个 Profile 调 `agentLoader.warnUnregisteredTools(profile, toolRegistry)`（文件 `oryxos-boot/src/main/java/com/oryxos/boot/OryxOsApplication.java`）。**core 不出现 `import com.oryxos.provider`**
- [ ] T014 [US1] 产出示例 Agent 目录 `.oryxos/agents/daily-reconcile/`：`AGENT.md`（frontmatter: identity/provider:deepseek/tools:[shell,read_file,notify,save_memory]/notify_channels:[webhook,${OPS_WEBHOOK_URL}]/schedules:[{id:reconcile-morning,cron:"0 0 9 * * *",zone:Asia/Shanghai,message:到点了，核对昨天的订单对账。}]；正文按课件§1.3 四步编排）+ `scripts/reconcile.py`（课件§1.4 原文，纯标准库无 key）+ `skills/report-format.md`（课件§1.4 原文）+ `REFERENCE.md`（课件§1.4 原文），四部分俱全

**Checkpoint**: US1 功能成立——丢目录即上线、schedules 进调度器留句柄、校验两层到位

---

## Phase 4: User Story 2 - 渐进式披露：资源按需进上下文 (Priority: P2)

**Goal**: 正文进 system prompt（常驻、不缓存）；子指令/参考/脚本不预载，靠底座 `read_file`/`shell` 按需取（脚本产出进上下文、代码不进）。

**Independent Test**: 构造多文件 Agent 目录，`ContextLoader.loadSystemPrompt` 结果含正文、不含子指令/参考/脚本内容。

### Tests for User Story 2 (harness 先行)

- [ ] T015 [P] [US2] 新建 `ProgressiveDisclosureTest`（`oryxos-core/src/test/java/com/oryxos/core/context/ProgressiveDisclosureTest.java`）：构造含正文 + skills 子指令 + REFERENCE.md + scripts 的 Agent 目录；调 `ContextLoader.loadSystemPrompt(profile, agentMdBody)` 断言：①返回含正文；②返回不含子指令内容；③返回不含参考内容；④返回不含脚本代码

### Implementation for User Story 2

- [ ] T016 [US2] 确认 `ContextLoader.loadSystemPrompt` 只注 bootstrap + `agentMdBody`（正文）+ 当前时间，不碰 skills/scripts/REFERENCE.md（文件 `oryxos-core/src/main/java/com/oryxos/core/context/ContextLoader.java`）。若已满足则仅补注释固化不变量（无行为改动）；harness T015 钉死

**Checkpoint**: 渐进式披露不变量由 harness 固化（17 节无缓存回归已钉"改完即时生效"）

---

## Phase 5: User Story 3 - 运行时注册：新 Agent 立即可见、定时留句柄 (Priority: P3)

**Goal**: 运行时注册与启动扫描走同一段 core 校验、同一异常类型+同一消息；句柄表为下节注销铺路。

**Independent Test**: 运行时 `register` 一个合法 Agent 立即可见、schedules 留句柄；缺 provider 的非法配置，运行时与启动扫描抛同一异常类型+同一消息。

### Tests for User Story 3 (harness 先行)

- [ ] T017 [P] [US3] 扩 `ProfileRegistryRuntimeTest`（Phase 2 T005 已建）：补"两条来源同规矩"——运行时 `register(非法Profile)` 与启动扫描 `scanAndRegister` 对同一缺 provider 配置抛**同一异常类型（IllegalArgumentException）+ 同一消息**（`Agent '<name>': missing required field 'provider.name'`）

### Implementation for User Story 3

- [ ] T018 [US3] 确认运行时注册路径调同一段 `AgentLoader.deriveProfile`（从而同一段校验、同一异常）——在 `ProfileRegistry.register` 之外，提供一个装配层/runtime 入口能调 `agentLoader.deriveProfile` + `registry.register` + `scheduler.registerProfile` 的同段组合（若 30 节才需独立 Service，本节在 boot 或测试中用既有方法组合验证同源即可，不新建 Service 类）。确认 `AgentScheduler.registerProfile`（T004）与 `registerAll` 走同一段

**Checkpoint**: 两条来源同规矩由 harness 钉死；句柄表就位（T004/T006）

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁与节级收尾

- [ ] T019 [P] 跑依赖方向门禁：`grep -rn 'import com.oryxos.provider' oryxos-core/src/main && echo VIOLATION || echo OK`，预期 OK
- [ ] T020 跑 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD），贴关键输出
- [ ] T021 跑前序节回归：`mvn test` 全模块绿（跨节契约证据）
- [ ] T022 H4 六条全局不变量逐条自查（详见 plan/research）；产出节级验收报告 + 变更总结
- [ ] T023 跑 quickstart.md 场景 1（harness 全绿）+ 场景 7（依赖方向 grep）确认；剩余人工项（真模型定时链路、webhook、资源按需加载人工抽查、正文即时生效）列入报告待人工过

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞全部用户故事**
- **US1 (Phase 3)**: 依赖 Phase 2（运行时注册地基）
- **US2 (Phase 4)**: 依赖 Phase 2；与 US1 独立（ContextLoader 不改行为，仅 harness 固化）
- **US3 (Phase 5)**: 依赖 Phase 2 + US1 的校验逻辑（T010）——"两条来源同规矩"要 US1 的 deriveProfile 校验已落
- **Polish (Phase 6)**: 依赖全部用户故事完成

### User Story Dependencies

- **US1 (P1)**: Phase 2 后开始，无故事间依赖
- **US2 (P2)**: Phase 2 后开始，独立可测
- **US3 (P3)**: Phase 2 + US1（T010 校验）后开始

### Within Each User Story

- 测试先写先红，实现跟进转绿
- harness 关键回归测试断言逐条保真、方法名英文 + `@DisplayName` 课件原文

### Parallel Opportunities

- Phase 1: T001/T002 并行
- Phase 2: T003/T004 并行（不同文件）；T005/T006 测试并行
- Phase 3: T007/T008/T009 测试并行；T010/T011/T012 同文件串行
- Phase 4/5 测试与 Phase 3 部分并行（不同测试文件）

---

## Parallel Example: User Story 1

```bash
# 测试先行(并行):
Task: "AgentLoaderTest 补缺必填项用例 (T007)"
Task: "DeriveProfileTest (T008)"
Task: "AgentScanRegisterTest (T009)"
# 实现跟进(同文件串行):
Task: "AgentLoader 校验+资源识别+tools告警 (T010/T011/T012)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup（依赖核实）
2. Phase 2 Foundational（ProfileRegistry 并发化 + AgentScheduler.registerProfile+句柄表）—— 阻塞
3. Phase 3 US1（校验 + 资源识别 + tools 告警 + 装配层 provider 校验 + daily-reconcile 示例目录）
4. **STOP VALIDATE**: US1 独立可测——丢目录即上线、schedules 留句柄

### Incremental Delivery

1. Setup + Foundational → 运行时注册地基
2. +US1 → 一个目录上线一个会自己跑的 Agent（MVP）
3. +US2 → 渐进式披露 harness 固化
4. +US3 → 两条来源同规矩 harness 钉死
5. Polish → 全量门禁 + 节级验收

---

## Notes

- 本节大量骨架（AgentLoader/ProfileRegistry/ContextLoader/AgentScheduler）前序节已立，是"补四样"非"从零造"
- 软门禁点：不新增交付物点名的对外概念（不引入 AgentDirResources record、不新建 Service 类——用既有方法组合）；已定字面量逐字保真
- 不自动 commit/push/package.sh，同步时机由用户决定
- harness 六测类映射课件§3 验收表，关键回归测试逐个对号
