# Implementation Plan: Agent Provider

**Branch**: `002-agent-provider` | **Date**: 2026-08-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-agent-provider/spec.md`

## Summary

Deliver the Provider abstraction layer — the unified front-desk between Agent OS and LLMs. Implement Profile loading from YAML, explicit provider-name-to-ChatModel mapping, LLM call routing with audit logging (success AND failure), and tool schema translation without execution. This is the first module built in OryxOS; Profile is a foundational entity consumed by all subsequent lessons.

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.3.5, Spring AI 1.0.0-M4 (BOM), Spring AI Alibaba (ChatModel abstraction), SnakeYAML 2.3, SQLite 3.46.1.3 + Spring Data JPA, springdoc-openapi 2.6.0

**Storage**: SQLite via Spring Data JPA; Profile YAML files on filesystem; `llm_calls` table via manual schema script (not hibernate.ddl-auto=update)

**Testing**: JUnit 5 (Spring Boot Starter Test), Mockito; unit tests for routing/validation/audit/translation; single `@Tag("integration")` smoke test for real LLM connectivity

**Target Platform**: Linux server (Ubuntu 22.04+, CentOS 8+, Debian 11+, Alibaba Cloud Linux 3, Rocky Linux); single-binary JAR deployment

**Project Type**: Maven multi-module (9 modules) — this feature touches oryxos-core, oryxos-provider, oryxos-storage

**Performance Goals**: Unit test suite completes in <5 seconds (no network); single LLM call duration depends on provider latency (not controlled by this layer)

**Constraints**: No plaintext credentials in config files (${ENV} placeholders only); P3C/ASM-compatible Java syntax (no enhanced switch `default ->`); manual schema script for SQLite table creation

**Scale/Scope**: Core phase — single instance, single process; multi-instance coordination out of scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. JDK 21 + Spring Boot 3.x 单体 | ✅ PASS | JDK 21, Spring Boot 3.3.5, Maven multi-module → single JAR |
| II. 五大核心能力优先 | ✅ PASS | Provider = 核心能力一, no governance features added |
| III. 自实现 ReAct Loop | ✅ PASS | Provider does NOT implement loop; it's a thin call layer |
| IV. Spring AI 使用边界 | ✅ PASS | Provider only uses ChatModel abstraction + tool schema generation; auto-execution explicitly disabled |
| V. Plugin Tool 三档接入 | ⬜ N/A | Not in scope for Provider |
| VI. SQLite + MEMORY.md | ✅ PASS | `llm_calls` table via manual schema script |
| VII. 审计 Day One 落库 | ✅ PASS | `llm_calls` written on every call (success AND failure) |
| VIII. 接口先行 | ⬜ N/A | Sandbox/NotifyChannelAdapter not in scope here; Profile is a record, not an interface |
| IX. 可演示 Demo | ⬜ N/A | Provider alone has no user-facing demo; it enables US-1 (chat + HTTP tool) once ReAct is built in lesson 17 |

**Gate result**: ALL PASS (or N/A). No violations. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/002-agent-provider/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (public API contracts)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── profile/
│   ├── Profile.java              # Record: all Profile fields
│   ├── ProfileLoader.java        # Scans .oryxos/profiles/, SnakeYAML parse + validate
│   └── ProfileRegistry.java      # Map<String, Profile> index, startup registration
├── tool/
│   ├── OryxTool.java             # Interface (exists as forward reference for ToolSchemaAdapter)
│   └── ToolResult.java           # Record (exists as forward reference)
└── exception/
    └── ProviderNotFoundException.java

oryxos-provider/src/main/java/com/oryxos/provider/
├── ProviderService.java          # chat(sessionId, Profile, Prompt), explicit mapping, audit, disable auto-exec
├── ProviderAutoConfiguration.java # Spring @Configuration: build Map<String, ChatModel> from oryxos.providers
└── ToolSchemaAdapter.java        # OryxTool.getInputSchema() → Spring AI tool spec (translate only, no exec)

oryxos-storage/src/main/java/com/oryxos/storage/
├── entity/
│   └── LlmCall.java              # JPA @Entity for llm_calls table
├── repository/
│   └── LlmCallRepository.java    # Spring Data JPA repository
└── schema.sql                    # Manual DDL: CREATE TABLE llm_calls

oryxos-core/src/test/java/com/oryxos/core/profile/
├── ProfileLoaderTest.java        # YAML parse, validation, env var resolution
oryxos-provider/src/test/java/com/oryxos/provider/
├── ProviderServiceTest.java      # Routing, audit, auto-exec disabled
├── ToolSchemaAdapterTest.java    # Schema translation fidelity
oryxos-storage/src/test/java/com/oryxos/storage/
├── LlmCallRepositoryTest.java    # schema.sql-based DDL, CRUD, success/error_message columns
oryxos-provider/src/test/java/com/oryxos/provider/
├── ProviderSmokeIT.java          # @Tag("integration") — real key, real call, verify audit record
```

**Structure Decision**: Standard Maven multi-module layout. New packages: `com.oryxos.core.profile`, `com.oryxos.provider`, `com.oryxos.storage.entity`, `com.oryxos.storage.repository`. All test classes mirror their production packages under `src/test/java`.

## Complexity Tracking

> No violations to justify. All constitution gates passed or are N/A.