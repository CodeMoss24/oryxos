# Tasks: Notify 通知模块

**Input**: Design documents from `specs/005-notify-module/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: 课件验收 harness 要求两个测试类（WebhookNotifyAdapterTest + NotifyToolsTest），列为必做任务。

**Organization**: 任务按 user story 分组，实现代码已就位，焦点在补齐测试。

## Format: `- [ ] [ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup（依赖与基线检查）

**Purpose**: 补齐测试依赖，验证已有代码编译通过

- [ ] T001 Add OkHttp MockWebServer test dependency to `oryxos-tool/pom.xml` (scope: test, groupId: com.squareup.okhttp3, artifactId: mockwebserver3)
- [ ] T002 Run `mvn dependency:tree -pl oryxos-tool` to confirm MockWebServer resolved without conflicts
- [ ] T003 Run `mvn clean compile -pl oryxos-tool -am` to confirm existing notify code compiles (WebhookNotifyAdapter, NotifyTools, NotifyChannelAdapter, Profile.NotifyChannel)

---

## Phase 2: Foundational（现有代码对标核查）

**Purpose**: 逐条确认已有实现与课件/技术方案一致，差异点登记

- [ ] T004 [P] Verify `NotifyChannelAdapter` interface in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyChannelAdapter.java` — check: send(NotifyTarget, String) signature; NotifyTarget record with channelType + config; no channel-specific terms in signature
- [ ] T005 [P] Verify `WebhookNotifyAdapter` in `oryxos-tool/src/main/java/com/oryxos/tool/notify/WebhookNotifyAdapter.java` — check: Sandbox.enforce called before HTTP POST; url missing → IllegalArgumentException; 5xx → exception propagated; uses JDK HttpClient
- [ ] T006 [P] Verify `NotifyTools` in `oryxos-tool/src/main/java/com/oryxos/tool/notify/NotifyTools.java` — check: implements OryxTool; getName="notify"; reads ProfileContext.get(); empty notifyChannels → ToolResult.failure; channel param matching logic; delegates to WebhookNotifyAdapter
- [ ] T007 [P] Verify `Profile.NotifyChannel` in `oryxos-core/src/main/java/com/oryxos/core/profile/Profile.java` — check: NotifyChannel record exists with type + config(String,String); notifyChannels field of type List<NotifyChannel> accessible via getNotifyChannels()

**Checkpoint**: 现有代码全部对标核查完毕，差异点已登记。核查结论确认 → 进入测试阶段。

---

## Phase 3: User Story 1 — Agent 定时触发后主动推送结果到群 (Priority: P1) 🎯 MVP

**Goal**: WebhookNotifyAdapter 发送逻辑完整可验证——用 MockWebServer 在本地模拟 HTTP 服务端，验证 POST body、URL 来源、错误传播。

**Independent Test**: `mvn test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest` 全绿。

### Tests for User Story 1

> **课件 harness 第一批——本节立即可跑。写测试、跑红、确认实现修绿。**

- [ ] T008 [P] [US1] Create `WebhookNotifyAdapterTest` in `oryxos-tool/src/test/java/com/oryxos/tool/notify/WebhookNotifyAdapterTest.java`:
  - Test 1 (`sendsPostRequestWithContentInBody`): MockWebServer 返回 200 → adapter.send(target, "hello") → 断言收到 POST 请求 body 含 "hello"、URL 来自 NotifyTarget.config 非硬编码
  - Test 2 (`throwsExceptionWhenUrlMissing`): NotifyTarget.config 无 "url" key → assertThrows(IllegalArgumentException)
  - Test 3 (`throwsExceptionOnServerError5xx`): MockWebServer 返回 500 → assertThrows(RuntimeException)

---

## Phase 4: User Story 2 — 对话中手动触发推送 (Priority: P2)

**Goal**: NotifyTools 作为 OryxTool 实现，端到端验证 Profile→NotifyTarget→Adapter 链路和 Sandbox 顺序保证。

**Independent Test**: `mvn test -pl oryxos-tool -Dtest=NotifyToolsTest` 全绿。

### Tests for User Story 2

> **课件 harness 第二批——mock Sandbox 和 Adapter，不依赖真实 HTTP。InOrder 顺序断言是安全关键。**

- [ ] T009 [P] [US2] Create `NotifyToolsTest` in `oryxos-tool/src/test/java/com/oryxos/tool/notify/NotifyToolsTest.java`:
  - Test 1 (`reportsErrorWhenNoNotifyChannelsConfigured`): ProfileContext.set(profile with empty notifyChannels) → notify("hi") → ToolResult.success=false, errorMessage contains "no notify_channels"
  - Test 2 (`usesFirstChannelWhenChannelParamOmitted`): ProfileContext.set(profile with 2 channels ["webhook-a", "webhook-b"]) → notify("hi") (no channel param) → adapter.send called with "webhook-a" target
  - Test 3 (`enforceBeforeSendOrderVerifiedWithInOrder`): mock Sandbox + mock Adapter → notify("hello", "default") → InOrder verify: sandbox.enforce(argThat(a → a.type()==HTTP_REQUEST)) BEFORE adapter.send(any(), eq("hello"))
    - @DisplayName("发送前必须先过白名单校验")

---

## Phase 5: User Story 3 — 渠道配置变更无需改 Agent 指令 (Priority: P3)

**Goal**: 验证接口中立性——确保 NotifyChannelAdapter 签名不依赖具体渠道实现，Profile 配置变更不影响 Agent 指令。

**Independent Test**: 代码审查方式——确认接口签名中不含 "webhook"/"feishu"/"wecom" 等渠道特有词，NotifyTarget.config 由实现类自行解释。

### Implementation for User Story 3

- [ ] T010 [US3] Code review: verify `NotifyChannelAdapter.send(NotifyTarget, String)` signature has zero channel-specific terms; confirm `NotifyTarget` only carries channelType: String and config: Map — no subclass required per channel type

---

## Phase 6: Polish & 门禁验证

**Purpose**: 全量门禁通过，课件交付物核对

- [ ] T011 Run `mvn test -pl oryxos-tool -Dtest="com.oryxos.tool.notify.*"` — both test classes pass
- [ ] T012 Run `mvn clean verify` from repo root — full gate (compile + test + P3C + SpotBugs + PMD + FindSecBugs) all green
- [ ] T013 Verify "本节交付物" per courseware: NotifyChannelAdapter ✅, NotifyTarget ✅, WebhookNotifyAdapter ✅, NotifyTools ✅, Profile.NotifyChannel ✅ → 2 test files exist and non-empty
- [ ] T014 Run quickstart validation per `specs/005-notify-module/quickstart.md`
- [ ] T015 [P] Spot-check H4 invariants: ① notify calls Sandbox.enforce on IO ② tool_invocations written by ToolExecutor ③ no plaintext keys in code ④ session_id only in SessionManager ⑤ no Reactor/CompletableFuture/thread pools ⑥ no Spring AI auto tool execution

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion (need compilation)
- **User Story 1 (Phase 3)**: Depends on Phase 2 (need verified implementation)
- **User Story 2 (Phase 4)**: Depends on Phase 2 (need verified implementation); independent of Phase 3
- **User Story 3 (Phase 5)**: Depends on Phase 2; independent of Phase 3/4
- **Polish (Phase 6)**: Depends on all prior phases complete

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories
- **US2 (P2)**: No dependencies on US1; both tests mock different layers
- **US3 (P3)**: Pure code review; no dependencies on US1/US2

### Within Each User Story

- Tests written first per harness 先行原则
- Tests run red → implementation verified/fixed → tests green

### Parallel Opportunities

- T004, T005, T006, T007 (Phase 2 code reviews) can run in parallel
- T008 (WebhookNotifyAdapterTest) and T009 (NotifyToolsTest) can run in parallel
- US1 (Phase 3) and US2 (Phase 4) can proceed in parallel after Phase 2

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (MockWebServer dependency)
2. Complete Phase 2: Foundational (code audit)
3. Complete Phase 3: US1 — WebhookNotifyAdapterTest
4. **STOP and VALIDATE**: `mvn test -pl oryxos-tool -Dtest=WebhookNotifyAdapterTest` green
5. Demo: MockWebServer proves notify webhook protocol correct

### Full Delivery

1. Setup + Foundational → baseline established
2. US1 (WebhookNotifyAdapterTest) → Batch 1 green
3. US2 (NotifyToolsTest) → Batch 2 green, InOrder safety check verified
4. US3 (Interface neutrality review) → Signature confirmed future-proof
5. Polish → `mvn clean verify` all green, deliverables checklist complete

---

## Notes

- [P] tasks = different files, no dependencies — can execute in parallel
- [Story] label maps task to specific user story for traceability
- Existing implementation code in `oryxos-tool/src/main/java/com/oryxos/tool/notify/` — only tests to write
- MockWebServer test dependency is new to project; T001 adds it with test scope
- NotifyToolsTest requires @AfterEach to clear ProfileContext (ThreadLocal cleanup)
- InOrder test (T009 Test 3) is the security-critical assertion — must not be weakened
