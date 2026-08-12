# Tasks: 定时任务模块（第三种触发源）

**Input**: Design documents from `/specs/009-scheduled-tasks/`

**Prerequisites**: plan.md（技术栈/落位）、spec.md（user stories）、research.md（H3 核实 + D1~D6）、data-model.md（ScheduleConfig/frontmatter 契约）、contracts/（scheduler-contract + config-contract）

**Tests**: 课件"验收 harness"要求测试先行（harness 判卷：`AgentSchedulerTest` 一个类覆盖四个坑），本清单按 story 拆测试任务并标 [P]（AgentLoaderTest 与 AgentSchedulerTest 不同文件可并行）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1=配置驱动注册+时区+会话身份（P1）、US2=防重叠（P2）、US3=失败隔离（P3）
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 改造前的基线对照

- [ ] T001 跑基线确认前序节全绿：`mvn -pl oryxos-core test`（预期全绿——构造 AgentScheduler 改造前的对照基线；若红先停，不进入实现）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 配置模型层——`ScheduleConfig` 值对象 + Profile 换型 + frontmatter 解析。US1 的"配置驱动"依赖此层完成；无此层 AgentScheduler 无法编译（runOnce(Profile, ScheduleConfig) 签名依赖）。

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T002 [P] 新增 `ScheduleConfig` record：`oryxos-core/src/main/java/com/oryxos/core/scheduler/ScheduleConfig.java` — `(String id, String cron, String zone, String message)` + `ZoneId zoneId()` 访问器（zone 非空 → `ZoneId.of(zone)`，否则 `ZoneId.systemDefault()`）；注释说明 id 全局唯一（操作者责任，28 节 task_id 同源）
- [ ] T003 改造 `Profile`：`oryxos-core/src/main/java/com/oryxos/core/profile/Profile.java` — 删除内嵌 `record Schedule(String cron, String zone, String message)`，`schedules` 字段类型改 `List<ScheduleConfig>`（import `com.oryxos.core.scheduler.ScheduleConfig`），getter/setter 形状不变
- [ ] T004 [P] `AgentLoaderTest` 补 schedules 解析用例（harness 先行，先红）：`oryxos-core/src/test/java/com/oryxos/core/profile/AgentLoaderTest.java` — ①完整条目（id/cron/zone/message 全量）②缺 id 条目被跳过且其余条照常 ③缺 zone 存 null ④多条并列 ⑤无 schedules 键 → 空列表。方法名英文，@DisplayName 中文说明
- [ ] T005 实现 `AgentLoader.deriveProfile` 解析：`oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java` — frontmatter `schedules`（List\<Map\>）→ `List<ScheduleConfig>` → `setSchedules`；条目缺 id/空 id → log.warn 跳过该条（无锁键可用，§8.2 不阻断启动先例）；zone/message 可为 null 原样存

**Checkpoint**: 配置模型层就绪——`mvn -pl oryxos-core test -Dtest=AgentLoaderTest` 绿

---

## Phase 3: User Story 1 - 配置驱动注册 + 时区 + 会话身份 (Priority: P1) 🎯 MVP

**Goal**: 启动时扫描全部 Profile 的 schedules 逐条注册进 Spring TaskScheduler（cron+时区一起传）；到点触发走 `SessionManager.getOrCreate("scheduler","scheduler",profileName)` + `AgentService.process`，session_id 拼接只在 SessionManager 内部

**Independent Test**: AgentSchedulerTest 的注册参数测试（ArgumentCaptor 抓 Trigger → cron 字符串 + nextExecution 行为断言时区）+ 会话身份测试（getOrCreate 三元组 verify + 两次触发同一 Session）——不真等时间，直接调 runOnce

### Tests for User Story 1 ⚠️（harness 先行，先红）

- [ ] T006 [P] [US1] 写 `AgentSchedulerTest` 两个测试（**先红**，目标签名 `lockFor(String)`/`runOnce(Profile, ScheduleConfig)` 尚未存在）：`oryxos-core/src/test/java/com/oryxos/core/scheduler/AgentSchedulerTest.java` — mock ProfileRegistry/AgentService/SessionManager/TaskScheduler（Mockito）；①`registerAll_registersCronTriggerWithCronAndZone`（@DisplayName「注册时 CronTrigger 带上了配置的 cron 和时区」）：ArgumentCaptor\<Trigger\> 抓 `schedule(runnable, trigger)`，断言 instanceof CronTrigger、`getExpression()==cron`、`nextExecution(new SimpleTriggerContext(base,base,base))==按配置时区计算的期望时刻`（cron "0 0 9 * * *" + Asia/Shanghai，基准 2026-08-12T00:00:00Z → 期望 2026-08-12T01:00:00Z——坑四，**禁用 equals**：实证 equals 不比 zone）②`runOnce_usesFixedSchedulerIdentity_sameSessionForBothTriggers`（@DisplayName「会话三元组固定 scheduler/scheduler/profileName，两次触发拿到同一 Session」）：runOnce 两次 → `verify(sessionManager).getOrCreate("scheduler","scheduler",profileName)` times(2)、process 两次收到同一 Session 实例

### Implementation for User Story 1

- [ ] T007 [US1] 骨架改造 `AgentScheduler`：`oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java` — 构造器改 `(ProfileRegistry, AgentService, SessionManager, TaskScheduler)`（Spring 注入自动装配 bean，research D2）；`@PostConstruct registerAll()` 遍历 `profileRegistry.list()` 每条 schedule 逐条 try/catch 隔离注册 `taskScheduler.schedule(() -> runOnce(profile, sc), new CronTrigger(sc.getCron(), sc.zoneId()))`（非法 cron 记 error 不阻断启动，D4）；`runOnce(Profile, ScheduleConfig)` 包级可见：`sessionManager.getOrCreate("scheduler","scheduler",profile.getName())` → `agentService.process(session, sc.getMessage())`；**删除**手拼 sessionId、`registerProfile`/`unregisterProfile`、`ScheduledFuture` 句柄表、`@PreDestroy` 自建线程池清理（运行时增删=扩展阶段，D6）；锁与 catch/finally 留 T009/T011 补

**Checkpoint**: US1 可独立验证——T006 两测试绿

---

## Phase 4: User Story 2 - 防重叠执行 (Priority: P2)

**Goal**: 每个任务（按 id 键）一把进程内 ReentrantLock，tryLock 失败 → 跳过本次触发、不排队

**Independent Test**: 锁被占时触发 → `verify(agentService, never()).process(any(), any())`

### Tests for User Story 2 ⚠️（harness 先行，先红）

- [ ] T008 [US2] 在 `AgentSchedulerTest` 加第三个测试（**先红**）：`skipsTrigger_whenPreviousRunStillHoldingLock`（@DisplayName「上一次还没跑完，本次触发直接跳过」）——`scheduler.lockFor("task-1").lock()` 模拟占锁 → `scheduler.runOnce(profile, scheduleConfig("task-1"))` → `verify(agentService, never()).process(any(), any())` → finally 放锁（课件原样回归，坑二）

### Implementation for User Story 2

- [ ] T009 [US2] `AgentScheduler` 补并发控制：`oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java` — `Map<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>()`；`Lock lockFor(String taskId)` 包级可见（computeIfAbsent）；runOnce 首行 `lock.tryLock()` 失败 → log.info 记任务 id + 跳过，成功才进 process（不排队、不堆积）

**Checkpoint**: US2 可独立验证——T008 绿

---

## Phase 5: User Story 3 - 失败隔离 (Priority: P3)

**Goal**: runOnce 内部异常不外抛只记日志、finally 必放锁——调度器不崩、不留死锁

**Independent Test**: process 抛异常 → assertDoesNotThrow；再触发一次能进来（二进宫）→ `verify(times(2)).process`

### Tests for User Story 3 ⚠️（harness 先行，先红）

- [ ] T010 [US3] 在 `AgentSchedulerTest` 加第四个测试（**先红**）：`runOnceSurvivesException_andReleasesLock`（@DisplayName「任务抛异常，不外抛且锁必须被释放」）——`when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"))`；`assertDoesNotThrow(() -> scheduler.runOnce(profile, scheduleConfig("task-1")))`；再 `runOnce` 一次 → `verify(agentService, times(2)).process(any(), any())`（课件"二进宫"原样回归：只有 finally 真放锁，第二次才能进来——坑三）

### Implementation for User Story 3

- [ ] T011 [US3] `AgentScheduler.runOnce` 收拢失败处理：`oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java` — process 包 try/catch（`catch (Exception e) log.error("scheduled task {} failed", sc.getId(), e)` 不外抛）+ `finally { lock.unlock(); }`（成功失败都放锁）；至此 runOnce 与课件骨架逐行同构

**Checkpoint**: US3 可独立验证——T010 绿；此时 `mvn -pl oryxos-core test` 全绿 = harness 判卷通过

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 门禁收口 + 遗留核对

- [ ] T012 全量门禁与收尾核对：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD，语法禁区——无 Java 18+ 增强 switch `default ->` 写法）；`git grep` 确认无明文 key 新增、无手拼 session_id 残留（`scheduler.*profileName` 拼接模式不得出现）；对照 quickstart.md 输出剩余人工项清单（真实到点触发 / 改 cron 不重编 / 端到端预演）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: T002 → T003 → T004/T005；T004 与 T005 同文件对（先测试后实现）；T002~T005 阻塞全部 user stories
- **User Stories (Phase 3~5)**: 全部依赖 Phase 2
  - US1（T006 → T007）：无前置 story
  - US2（T008 → T009）：依赖 US1（runOnce 骨架 + lockFor 签名）
  - US3（T010 → T011）：依赖 US2（二进宫断言验证的是"finally 放锁"，锁必须已存在）
- **Polish (Phase 6)**: 依赖所有 story 完成

### User Story Dependencies

- **US1 (P1)**: Foundational 后即可开始
- **US2 (P2)**: 依赖 US1 完成（同一方法族 runOnce 的增量）
- **US3 (P3)**: 依赖 US2 完成（二进宫测试的前提是锁已存在）

### Within Each User Story

- 测试先写（红）→ 实现 → 当场跑绿（写后 DoD，不攒到最后）
- 课件 harness 两个最值钱回归（T008 锁被占 never、T010 二进宫 times(2)）原样落地，方法名译英文，课件中文进 @DisplayName

### Parallel Opportunities

- T002 与 T004 可并行（不同文件：ScheduleConfig.java vs AgentLoaderTest.java；T004 依赖 T003 的类型签名，可在 T003 前只写"缺 id/多条"等不引用类型的用例，或待 T003 后启动——建议 T003 落地后并行跑 T004+T002 的编译依赖）
- T006 与 T004 可并行（不同测试文件）
- 其余任务同文件链式依赖（AgentScheduler.java 的 T007→T009→T011 必须顺序）

---

## Parallel Example: Foundational Phase

```bash
# T003 落地后，T004 与 T002 的编译依赖齐备，可并行：
Task: "T004 写 AgentLoaderTest schedules 用例（先红）"
Task: "T006 写 AgentSchedulerTest US1 两测试（先红）"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: T001 基线
2. Phase 2: T002~T005（配置模型层）
3. Phase 3: T006 测试 → T007 骨架（US1 独立可测：注册参数 + 会话身份）
4. **STOP and VALIDATE**: `mvn -pl oryxos-core test -Dtest=AgentSchedulerTest` 绿

### Incremental Delivery

1. Foundation 就绪（配置模型层）→ 2. US1（注册+会话身份）→ 3. US2（防重叠）→ 4. US3（失败隔离）→ 5. 全量门禁（Phase 6）
2. 每 story 增量后跑 `mvn -pl oryxos-core test` 验证，红了当场修

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 课件"本节交付物"逐项对照：代码 `AgentScheduler`(registerAll+runOnce+按任务 id 的 ReentrantLock 表)✅（T007/T009/T011）、`ScheduleConfig`(id/cron/zone/message)✅（T002）、测试 `AgentSchedulerTest`✅（T006/T008/T010）、配置 Profile `schedules` 字段✅（T003 换型 + T005 解析）、约定会话身份固定 (scheduler,scheduler,profileName) + 失败只记日志✅（T007/T011）
- 无新表、无新配置键、无 boot 改动、无新增第三方依赖
- 不自动 commit——同步时机由用户决定
