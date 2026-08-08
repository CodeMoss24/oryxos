# Tasks: Agent Provider

**Input**: Design documents from `/specs/002-agent-provider/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Included per courseware harness requirements. Tests MUST be written first and verified to FAIL before implementation.

**Organization**: Tasks grouped by user story for independent verification. Profile system and storage entities are foundational; ProviderService integrates all three user stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Build Infrastructure)

**Purpose**: Fix build issues and wire Maven module dependencies

- [ ] T001 Fix oryxos-boot/pom.xml — add explicit versions for spring-boot-starter-actuator and micrometer-registry-prometheus in dependencyManagement, or remove from depMgmt to inherit from spring-boot-starter-parent
- [ ] T002 [P] Verify oryxos-core/pom.xml has SnakeYAML dependency (inherited from Spring Boot parent)
- [ ] T003 [P] Verify oryxos-provider/pom.xml has Spring AI Alibaba starter and oryxos-core dependency
- [ ] T004 [P] Verify oryxos-storage/pom.xml has spring-boot-starter-data-jpa, sqlite-jdbc, hibernate-community-dialects dependencies
- [ ] T005 Run `mvn compile -pl oryxos-core,oryxos-provider,oryxos-storage` to verify all module dependencies resolve correctly

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Profile system, forward-reference interfaces, schema, and storage entities — MUST complete before ProviderService

**⚠️ CRITICAL**: No user story task can begin until this phase is complete

### Tests for Foundational (write FIRST, ensure they FAIL)

- [ ] T006 [P] Create ProfileLoaderTest in oryxos-core/src/test/java/com/oryxos/core/profile/ProfileLoaderTest.java — test: legal YAML full-field parse, missing provider error clear, bad file does not block remaining loads, ${ENV} resolution
- [ ] T007 [P] Create LlmCallRepositoryTest in oryxos-storage/src/test/java/com/oryxos/storage/LlmCallRepositoryTest.java — test: schema.sql-based DDL, CRUD, success and error_message columns present

### Implementation for Foundational

- [ ] T008 [P] Create OryxTool interface in oryxos-core/src/main/java/com/oryxos/core/tool/OryxTool.java — getName(), getDescription(), getInputSchema(), execute()
- [ ] T009 [P] Create ToolResult record in oryxos-core/src/main/java/com/oryxos/core/tool/ToolResult.java
- [ ] T010 [P] Create ProviderNotFoundException in oryxos-core/src/main/java/com/oryxos/core/exception/ProviderNotFoundException.java
- [ ] T011 [P] Create Profile record in oryxos-core/src/main/java/com/oryxos/core/profile/Profile.java — all fields: name, description, identity, provider, tools, skills, mcpServers, channels, notifyChannels, schedules, bootstrap, settings (with nested records Identity, ProviderRef, Settings, NotifyChannelConfig, ScheduleConfig)
- [ ] T012 Create ProfileLoader in oryxos-core/src/main/java/com/oryxos/core/profile/ProfileLoader.java — scan .oryxos/profiles/, SnakeYAML parse, validate provider.name exists in global config, ${ENV} resolution, bad file logs error but does not block
- [ ] T013 Create ProfileRegistry in oryxos-core/src/main/java/com/oryxos/core/profile/ProfileRegistry.java — Map<String, Profile> index, register(), get(), listAll(), size()
- [ ] T014 Create schema.sql in oryxos-storage/src/main/resources/schema.sql — CREATE TABLE IF NOT EXISTS llm_calls (id, session_id, provider, model, prompt_tokens, completion_tokens, total_tokens, duration_ms, success, error_message, created_at)
- [ ] T015 [P] Create LlmCall entity in oryxos-storage/src/main/java/com/oryxos/storage/entity/LlmCall.java — JPA @Entity mapping to llm_calls table
- [ ] T016 [P] Create LlmCallRepository in oryxos-storage/src/main/java/com/oryxos/storage/repository/LlmCallRepository.java — Spring Data JPA interface
- [ ] T017 Verify T006 and T007 now pass (ProfileLoaderTest, LlmCallRepositoryTest)

**Checkpoint**: Foundation ready — Profile system, schema, and storage entities complete. User story implementation can now begin.

---

## Phase 3: User Story 1 - Multi-Provider Routing (Priority: P1) 🎯 MVP

**Goal**: ProviderService maintains explicit provider name → ChatModel mapping and routes calls correctly without cross-interference

**Independent Test**: Configure two mock ChatModels, call with Profile targeting "kimi", verify kimi receives the call and deepseek is never touched

### Tests for User Story 1 (write FIRST, ensure they FAIL)

- [ ] T018 [P] [US1] Create ProviderServiceTest in oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java — test methods: routeByNameTwoProvidersNoCrossTalk, unknownProviderThrowsException (using mock ChatModels and mock audit)
- [ ] T019 [P] [US1] Add routing-related test method to ProviderServiceTest: routeByNameTwoProvidersNoCrossTalk — verify kimi called, deepseek never touched

### Implementation for User Story 1

- [ ] T020 [P] [US1] Create LlmCallAudit interface in oryxos-provider/src/main/java/com/oryxos/provider/LlmCallAudit.java — record(sessionId, provider, model, usage, success, errorMessage, durationMs)
- [ ] T021 [P] [US1] Create Prompt and Response records in oryxos-provider/src/main/java/com/oryxos/provider/Prompt.java — Prompt(messages, availableTools), Response(content, toolCalls, usage), Usage(promptTokens, completionTokens, totalTokens), ToolCallRequest(name, arguments)
- [ ] T022 [US1] Implement ProviderService in oryxos-provider/src/main/java/com/oryxos/provider/ProviderService.java — constructor(Map<String, ChatModel>, ToolSchemaAdapter, LlmCallAudit), chat(sessionId, profile, prompt): lookup model by name (throw ProviderNotFoundException if missing), build messages, call model, return Response
- [ ] T023 [US1] Create ProviderAutoConfiguration in oryxos-provider/src/main/java/com/oryxos/provider/ProviderAutoConfiguration.java — @ConfigurationProperties bind oryxos.providers, create ChatModel per entry, build Map<String, ChatModel>, expose ProviderService @Bean
- [ ] T024 [US1] Verify T018/T019 routing tests pass with ProviderService and ProviderAutoConfiguration wired

**Checkpoint**: User Story 1 complete — ProviderService routes correctly by name, unknown provider throws clear error

---

## Phase 4: User Story 2 - Audit Trail (Priority: P2)

**Goal**: Every LLM call (success AND failure) is recorded to llm_calls table with provider, model, tokens, duration, success flag, and error message

**Independent Test**: Trigger a successful and a failed LLM call, verify both produce audit records with correct metadata

### Tests for User Story 2 (write FIRST, ensure they FAIL)

- [ ] T025 [P] [US2] Add audit test methods to ProviderServiceTest in oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java — callFailureAuditRecordedWithSuccessFalse (mock ChatModel throws RuntimeException, verify audit.record called with success=false + error message, verify exception still thrown)

### Implementation for User Story 2

- [ ] T026 [US2] Implement LlmCallAudit in oryxos-provider/src/main/java/com/oryxos/provider/LlmCallAuditImpl.java — record() writes LlmCall entity via LlmCallRepository (from oryxos-storage)
- [ ] T027 [US2] Integrate audit into ProviderService.chat() — try/catch around model.call(): on success record audit then return; on failure record audit then re-throw
- [ ] T028 [US2] Update ProviderAutoConfiguration to inject LlmCallRepository and wire LlmCallAudit
- [ ] T029 [US2] Verify T025 audit tests pass; verify T007 LlmCallRepositoryTest still passes

**Checkpoint**: User Stories 1 AND 2 both work — routing correct, audit records written on success AND failure

---

## Phase 5: User Story 3 - Tool Schema Translation (Priority: P3)

**Goal**: ToolSchemaAdapter translates OryxTool definitions into LLM-compatible schemas with auto-execution disabled, outputting only metadata (no execution logic)

**Independent Test**: Pass tools to ProviderService, verify request has tool schemas, auto-execution disabled, no tool side effects

### Tests for User Story 3 (write FIRST, ensure they FAIL)

- [ ] T030 [P] [US3] Create ToolSchemaAdapterTest in oryxos-provider/src/test/java/com/oryxos/provider/ToolSchemaAdapterTest.java — test methods: schemaFieldsAlignedAfterTranslation, outputContainsNoExecutionLogic
- [ ] T031 [P] [US3] Add tool schema test method to ProviderServiceTest: callWithToolSchemaDisablesAutoExecution — verify ChatModel called with autoExecuteTools=false and tools list present

### Implementation for User Story 3

- [ ] T032 [US3] Implement ToolSchemaAdapter in oryxos-provider/src/main/java/com/oryxos/provider/ToolSchemaAdapter.java — toTargetTools(List<OryxTool>) translates getInputSchema() to Spring AI tool definitions, no callbacks or execution logic
- [ ] T033 [US3] Integrate ToolSchemaAdapter into ProviderService.chat() — when prompt.availableTools is non-empty, call adapter.toTargetTools() and attach to request with auto-execution disabled
- [ ] T034 [US3] Verify T030, T031 tool schema tests pass

**Checkpoint**: All three user stories complete — routing + audit + tool schema translation

---

## Phase 6: Polish & Integration Verification

**Purpose**: Configuration files, integration smoke test, and final verification

- [ ] T035 [P] Configure oryxos.providers in oryxos-boot/src/main/resources/application.yaml — add example provider entries with ${ENV} placeholders
- [ ] T036 [P] Create example Profile YAML in .oryxos/profiles/ops-agent.yaml — demonstrate all fields per data-model.md
- [ ] T037 [P] Create ProviderSmokeIT in oryxos-provider/src/test/java/com/oryxos/provider/ProviderSmokeIT.java — @Tag("integration"), reads real key from env var, calls real LLM, asserts non-null response, verifies LlmCall record written with success=true
- [ ] T038 [P] Create application-test.yaml in oryxos-storage/src/test/resources/application-test.yaml — configure in-memory SQLite datasource for unit tests
- [ ] T039 Run `mvn clean verify` — all checks (Spotless, PMD, SpotBugs, Checkstyle, tests) must pass
- [ ] T040 Verify `grep -r "sk-" src/` returns zero matches (no plaintext credentials)
- [ ] T041 Run quickstart.md validation steps

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational
- **User Story 2 (Phase 4)**: Depends on US1 (extends ProviderService.chat() with audit)
- **User Story 3 (Phase 5)**: Depends on US1 (extends ProviderService.chat() with tool schema)
- **Polish (Phase 6)**: Depends on all user stories

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational — independent routing logic
- **US2 (P2)**: Depends on US1 (adds audit to existing chat() method)
- **US3 (P3)**: Depends on US1 (adds tool schema to existing chat() method)
- US2 and US3 can be implemented in parallel after US1 is complete

### Within Each User Story

- Tests MUST be written first and verified to FAIL
- Then implement, then verify tests PASS
- Core implementation before integration

### Parallel Opportunities

- T002, T003, T004 (verify module poms) can run in parallel
- T006, T007 (foundational tests) can run in parallel
- T008, T009, T010, T011 (forward refs + Profile + exception) can run in parallel
- T014, T015, T016 (schema + entity + repository) can run in parallel
- T030, T031 (US3 tests) can run in parallel
- T035, T036, T037, T038 (config + smoke test) can run in parallel

---

## Parallel Example: Foundational Phase

```bash
# Launch all forward-reference types together:
Task: "Create OryxTool interface in oryxos-core/.../tool/OryxTool.java"
Task: "Create ToolResult record in oryxos-core/.../tool/ToolResult.java"
Task: "Create ProviderNotFoundException in oryxos-core/.../exception/ProviderNotFoundException.java"
Task: "Create Profile record in oryxos-core/.../profile/Profile.java"

# Launch storage artifacts together:
Task: "Create schema.sql in oryxos-storage/src/main/resources/schema.sql"
Task: "Create LlmCall entity in oryxos-storage/.../entity/LlmCall.java"
Task: "Create LlmCallRepository in oryxos-storage/.../repository/LlmCallRepository.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (routing)
4. **STOP and VALIDATE**: ProfileLoaderTest + ProviderServiceTest (routing tests) pass
5. This is the minimal usable Provider layer

### Incremental Delivery

1. Setup + Foundational → Profile system + schema + entities ready
2. US1 → Provider routes by name, no cross-talk ✅
3. US2 → Audit records written for success AND failure ✅
4. US3 → Tool schemas translated, auto-execution disabled ✅
5. Polish → `mvn clean verify` green, smoke test passes

### Completion Criteria

- `mvn clean verify` all green
- All 5 test classes exist and pass (4 unit + 1 integration)
- No plaintext credentials in codebase
- Courseware deliverable checklist verified (see Phase 2 of lesson-dev skill)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- ProviderService.chat() is shared across US1/US2/US3 — implementation evolves incrementally
- Courseware harness tests MUST be written with English method names; Chinese courseware names go in @DisplayName
- P3C/PMD/SpotBugs/Checkstyle are build gates — avoid Java 18+ syntax forms (enhanced switch `default ->`)
- Commit after each phase checkpoint