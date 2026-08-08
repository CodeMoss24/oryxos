# 任务清单：ReAct 循环引擎

**输入**: `/specs/003-react-loop/` 下的设计文档

**前置条件**: plan.md（技术栈/结构）、spec.md（用户故事）、research.md（技术决策）、data-model.md（数据模型）、quickstart.md（验证指南）

**测试策略**: 课件验收 harness 要求 5 个测试类（ReActLoopTest、PromptBuilderTest、ToolExecutorTest、AgentServiceTest、ContextLoaderTest），全部单测不碰网络，`mvn test` 秒级跑完。

**组织方式**: 按用户故事分组，测试先行（harness 先行）。

## 格式: `[ID] [P?] [Story] 描述`

- **[P]**: 可并行执行（不同文件，无依赖）
- **[Story]**: 归属用户故事（US1~US6）
- 描述包含精确文件路径

---

## Phase 1: 基础设施补全（阻断性前置）

**目的**: 审计基础设施和依赖注入，所有用户故事依赖这些才能运行

- [ ] T001 [P] 在 `oryxos-storage/src/main/resources/schema.sql` 追加 `tool_invocations` 建表 DDL（id/session_id/tool_name/input_json/result_json/success/error_message/duration_ms/created_at）
- [ ] T002 在 `oryxos-core/src/main/java/com/oryxos/core/react/ToolExecutor.java` 注入 `ToolInvocationRepository`，`execute()` 方法增加 `sessionId` 参数，成功/失败都写审计记录（成功写 success=true，失败写 success=false + error_message，异常捕获后审计写入再返回错误消息）
- [ ] T003 在 `oryxos-core/src/main/java/com/oryxos/core/AgentService.java` 注入 `SessionRepository`，`process()` 方法中 `ReActLoop.run()` 返回后调用 `sessionRepository.save()` 持久化 Session
- [ ] T004 在 `oryxos-core/src/main/java/com/oryxos/core/context/ContextLoader.java` 的 `readIfExists()` 方法中，文件缺失时增加 `log.warn()` 输出

**检查点**: 基础设施就绪——审计链路、Session 持久化、日志输出全部接线完毕

---

## Phase 2: User Story 1 - 简单对话一轮收尾 (Priority: P1) 🎯 MVP

**目标**: ReActLoop 收到无工具调用的 LLM 响应时，恰好 1 轮返回

**独立测试**: Mock ProviderPort 返回无工具调用的 LlmResponse，验证循环只执行 1 轮即返回文本

### 测试（先行，先跑红再实现）

- [ ] T005 [P] [US1] 编写 `oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopTest.java` — 测试方法 `returnsResponseWhenNoToolCalls()`：Mock Provider 返回无工具调用响应，验证恰好调 1 次 provider、返回文本正确

### 实现

- [ ] T006 [US1] 更新 `oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java`：`run()` 方法签名中 `ToolExecutor.execute()` 调用传入 `session.getSessionId()`（对齐课件骨架 `toolExecutor.execute(session.id(), call)`）

**检查点**: ReActLoop 无工具调用场景全绿

---

## Phase 3: User Story 2 - 多轮工具调用完成任务 (Priority: P1)

**目标**: ReActLoop 在有工具调用时执行工具、回填结果、进入下一轮

**独立测试**: Mock Provider 第一轮返回带工具调用的响应、第二轮返回无工具调用的文本

### 测试（先行）

- [ ] T007 [P] [US1] 在 `ReActLoopTest.java` 中增加 `executesToolAndContinuesLoop()`：Mock Provider 首轮返回工具调用、次轮返回文本，验证 ToolExecutor 被调用、Session 包含工具结果消息

### 实现

- [ ] T008 [US2] 确认 `ReActLoop.java` 的多轮循环逻辑正确：Tool 结果通过 `session.append(Message.tool(result))` 回填，下一轮 Prompt 包含上轮上下文

**检查点**: ReActLoop 多轮工具调用全绿

---

## Phase 4: User Story 3 - 最大轮数强制终止 (Priority: P2)

**目标**: 模型永不收敛时，恰好在 maxIterations 次后强制终止

**独立测试**: Mock Provider 每轮都返回工具调用（永不收敛），验证循环恰好在 profile.maxIterations 次后停止

### 测试（先行）

- [ ] T009 [P] [US3] 在 `ReActLoopTest.java` 中增加 `stopsAtMaxIterationsWhenModelNeverConverges()`（课件原文"模型一直要调工具_转满最大轮数强制停"）：Mock Provider 每轮都返回工具调用，验证恰好调 10 次 provider、返回含"达到最大迭代次数"的消息。`@DisplayName` 保留课件原文"模型一直要调工具_转满最大轮数强制停"
- [ ] T010 [P] [US3] 在 `ReActLoopTest.java` 中增加 `accumulatesMessagesInSession()`：验证每轮 assistant 消息和 tool 消息都累积到 Session

**检查点**: 死循环兜底 + 消息累积全绿

---

## Phase 5: User Story 4 - 上下文管理与截断 (Priority: P2)

**目标**: PromptBuilder 按 maxHistoryTurns 截断历史消息，system prompt 末尾含日期时间

**独立测试**: 构造超过 maxHistoryTurns 的 Session 历史，验证截断行为

### 测试（先行）

- [ ] T011 [P] [US4] 编写 `oryxos-core/src/test/java/com/oryxos/core/react/PromptBuilderTest.java` — 测试方法 `truncatesHistoryWhenExceedsMaxTurns()`：Session 有 30 轮历史、maxHistoryTurns=20，验证输出 20 轮
- [ ] T012 [P] [US4] 在 `PromptBuilderTest.java` 中增加 `preservesAllHistoryWhenUnderLimit()`：Session 历史少于 maxHistoryTurns 时全部保留
- [ ] T013 [P] [US4] 在 `PromptBuilderTest.java` 中增加 `systemPromptContainsCurrentDateTime()`：验证 buildSystemPrompt 的返回值包含当前日期时间文本

### 实现

- [ ] T014 [US4] 确认 `oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java` 的 `truncateHistory()` 和 `assembleMessages()` 逻辑正确——四部分顺序为 system prompt（含日期）→ memory → history → tools

**检查点**: 上下文截断 + 日期时间全绿

---

## Phase 6: User Story 5 - 审计记录完整性 (Priority: P1)

**目标**: ToolExecutor 每次执行都写 tool_invocations 审计（成功/失败都记）

**独立测试**: 分别模拟工具执行成功和失败，验证 ToolInvocationRepository.save() 被调用且字段正确

### 测试（先行）

- [ ] T015 [P] [US5] 编写 `oryxos-core/src/test/java/com/oryxos/core/react/ToolExecutorTest.java` — 测试方法 `recordsSuccessAuditWhenToolSucceeds()`：Mock Tool 返回 success=true 的 ToolResult，验证 `toolInvocationRepository.save()` 被调用且 entity.success=true
- [ ] T016 [P] [US5] 在 `ToolExecutorTest.java` 中增加 `recordsFailureAuditWhenToolFails()`：Mock Tool 抛出 RuntimeException，验证 entity.success=false、entity.errorMessage 包含异常消息

### 实现

- [ ] T017 [US5] 确认 `ToolExecutor.java` 的审计写入逻辑：`sessionId` 参数传入、`duration_ms` 记录耗时、`created_at` 为当前时间

**检查点**: 审计写入全绿

---

## Phase 7: User Story 6 - AgentService 统一编排与 ProfileContext 安全 (Priority: P1)

**目标**: AgentService.process() 管理 ProfileContext 生命周期（入口设、出口清，异常也清），结束后持久化 Session

**独立测试**: Mock ReActLoop 抛异常，验证 finally 中 ProfileContext.clear() 被执行

### 测试（先行）

- [ ] T018 [P] [US6] 编写 `oryxos-core/src/test/java/com/oryxos/core/AgentServiceTest.java` — 测试方法 `profileContextIsClearedAfterException()`（课件原文"处理中抛异常_ProfileContext也必须被清掉"）：Mock ReActLoop 抛 RuntimeException，`assertThrows` 后 `assertNull(ProfileContext.current())`。`@DisplayName` 保留课件原文"处理中抛异常_ProfileContext也必须被清掉"
- [ ] T019 [P] [US6] 在 `AgentServiceTest.java` 中增加 `profileContextIsAccessibleDuringProcessing()`：验证处理期间 `ProfileContext.get()` 可取得当前 Profile
- [ ] T020 [P] [US6] 在 `AgentServiceTest.java` 中增加 `sessionIsPersistedAfterSuccessfulProcessing()`：验证 `sessionRepository.save()` 被调用

### 实现

- [ ] T021 [US6] 确认 `AgentService.java` 的 Session 持久化逻辑：从 Session 转换为 SessionEntity（`messagesJson` 字段用 JSON 序列化 `session.getMessages()`），调用 `sessionRepository.save()`

**检查点**: ProfileContext 泄漏防护 + Session 持久化全绿

---

## Phase 8: ContextLoader 验收 (无独立 User Story，归属 FR-006)

**目标**: ContextLoader 无缓存、缺文件 WARN

### 测试（先行）

- [ ] T022 [P] 编写 `oryxos-core/src/test/java/com/oryxos/core/context/ContextLoaderTest.java` — 测试方法 `reloadsFileOnEachBuild()`：写临时 Bootstrap 文件 → build → 改文件内容 → 再次 build，验证两次结果不同（无缓存）
- [ ] T023 [P] 在 `ContextLoaderTest.java` 中增加 `warnsWhenBootstrapFileMissing()`：Profile 引用不存在的 bootstrap 文件，验证 WARN 日志输出

**检查点**: ContextLoader 无缓存 + WARN 全绿

---

## Phase 9: 收尾与门禁

**目的**: 全量门禁 + 前序回归

- [ ] T024 运行 `mvn clean verify`（含 P3C/SpotBugs/FindSecBugs/PMD），确认全绿
- [ ] T025 [P] 运行前序节（16 节 Provider）全部测试，确认回归绿：`mvn test -pl oryxos-core,oryxos-provider,oryxos-storage`
- [ ] T026 按 `quickstart.md` 逐项确认验收点

---

## 依赖关系与执行顺序

### 阶段依赖

- **Phase 1（基础设施）**: 无依赖，立即开始 → 阻塞所有用户故事
- **Phase 2-7（用户故事）**: 全部依赖 Phase 1 完成，彼此之间无依赖，可并行
- **Phase 8（ContextLoader）**: 依赖 Phase 1，可与 Phase 2-7 并行
- **Phase 9（收尾）**: 依赖所有前序 Phase 完成

### 用户故事依赖

- **US1 (P1)**: Phase 1 完成后可开始，不依赖其他故事
- **US2 (P1)**: Phase 1 完成后可开始，可复用 US1 的测试类文件（同文件不同方法）
- **US3 (P2)**: Phase 1 完成后可开始，在 ReActLoopTest.java 中追加方法
- **US4 (P2)**: Phase 1 完成后可开始，独立测试类
- **US5 (P1)**: Phase 1 完成后可开始，独立测试类
- **US6 (P1)**: Phase 1 完成后可开始，独立测试类
- **ContextLoader**: Phase 1 完成后可开始，独立测试类

### 每个故事内部

- 测试（先行）→ 实现 → 验证 `mvn test -Dtest=XxxTest`

---

## 并行机会

```bash
# Phase 1 中两个独立任务可并行：
Task: T001 "追加 tool_invocations 建表 DDL schema.sql"
Task: T004 "ContextLoader 补 WARN 日志"

# Phase 2-8 五个测试类可并行编写：
Task: T005 "ReActLoopTest"
Task: T011 "PromptBuilderTest"
Task: T015 "ToolExecutorTest"
Task: T018 "AgentServiceTest"
Task: T022 "ContextLoaderTest"
```

---

## 实施策略

### MVP 优先（US1 + US5 + US6 三个 P1）

1. 完成 Phase 1: 基础设施补全
2. 完成 Phase 2: US1（ReActLoop 一轮收尾）
3. 完成 Phase 6: US5（审计完整性）
4. 完成 Phase 7: US6（ProfileContext 安全）
5. **暂停验证**: `mvn test -Dtest=ReActLoopTest,ToolExecutorTest,AgentServiceTest`
6. 此时核心链路可演示 < 1 分钟

### 增量交付

1. Phase 1 → 审计基础设施就绪
2. + US1/US5/US6 → 核心链路 + 审计完整（MVP！）
3. + US2 → 多轮工具调用
4. + US3 → 死循环兜底
5. + US4 → 上下文截断
6. + ContextLoader → 无缓存验证
7. Phase 9 → 全量门禁

---

## "本节交付物"对照表

| 课件交付物 | 对应任务 | 状态 |
|-----------|---------|------|
| `ReActLoop` | T006, T008 | 已有骨架，签名微调 |
| `PromptBuilder` | T014 | 已有实现，确认正确 |
| `ToolExecutor` | T002, T017 | 已有骨架，补审计 |
| `AgentService` | T003, T021 | 已有骨架，补持久化 |
| `ProfileContext` | — | 已有，完整 |
| `ContextLoader` | T004 | 已有骨架，补 WARN |
| `ToolInvocation` 实体 | — | 已有，完整 |
| `ToolInvocationRepository` | — | 已有，完整 |
| `ReActLoopTest` | T005, T007, T009, T010 | 新建 |
| `PromptBuilderTest` | T011, T012, T013 | 新建 |
| `ToolExecutorTest` | T015, T016 | 新建 |
| `AgentServiceTest` | T018, T019, T020 | 新建 |
| `ContextLoaderTest` | T022, T023 | 新建 |
| `tool_invocations` 表 | T001 | schema.sql 补行 |