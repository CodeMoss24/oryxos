# Implementation Plan: OryxOS Core Runtime

**Branch**: `001-oryxos-core-runtime` | **Date**: 2026-08-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-oryxos-core-runtime/spec.md`

## Summary

OryxOS 核心阶段交付一个面向严监管企业的私有可审计 Agent OS 运行时内核。系统基于 JDK 21 + Spring Boot 3.x 单体架构，自实现 ReAct 循环引擎，围绕五大核心能力（对接 LLM、ReAct、Memory、Tool、Web Service）组织。实施按 4 周 × 3 小时节奏推进，每周末有可演示成果，第四周末三个验收 Demo 全跑通。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**: Spring Boot 3.3.x, Spring AI 1.0.0-M4, Spring AI Alibaba, Picocli 4.7.x, SnakeYAML 2.3, SQLite JDBC 3.46.x, MCP Java SDK, Logback + SLF4J, Spring Data JPA, springdoc-openapi

**Storage**: SQLite (sessions, audit, scheduled tasks) + Markdown file (long-term memory via MEMORY.md)

**Testing**: JUnit 5 + Spring Boot Test (implied by Spring Boot starter)

**Target Platform**: Linux (Ubuntu 22.04+, CentOS 8+, Debian 11+, Rocky Linux)

**Project Type**: web-service + cli (Spring Boot monolithic application with Picocli CLI)

**Performance Goals**: Single Agent response < 30s (incl. LLM time), Web Service 50+ concurrent requests, CLI instant startup for non-Spring commands

**Constraints**: Linux only, single binary JAR, no external process dependencies except LLM API, no SecurityManager (JDK 21 removed), HTTPS 443 blocked in dev WSL2 environment (SSH only for git)

**Scale/Scope**: Single instance, multiple concurrent Agents (3 demo Agents), single tenant (no multi-tenant in core phase)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪法原则 | 合规检查 | 状态 |
|---------|---------|------|
| I. JDK 21 + Spring Boot 3.x 单体架构 | JDK 21, Spring Boot 3.3.x, 9 Maven 模块, 单 JAR | ✅ |
| II. 五大核心能力优先 | 只交付 5 大能力, 治理层全放扩展阶段 | ✅ |
| III. 自实现 ReAct Loop | ReActLoop 自实现, 不依赖 Spring AI Agent 抽象 | ✅ |
| IV. Spring AI 使用边界 | 仅用 Provider 抽象/@Tool schema, 禁用自动 tool 执行 | ✅ |
| V. Plugin Tool 三档接入 | 主推 AGENT.md + MCP 零代码方式 | ✅ |
| VI. SQLite + MEMORY.md | SQLite 持久化, MEMORY.md 长期记忆, 向量检索放扩展 | ✅ |
| VII. 审计 Day One 落库 | tool_invocations/llm_calls 核心阶段写入 SQLite | ✅ |
| VIII. 接口先行 | Sandbox/NotifyChannelAdapter/LongTermMemoryStore 先定接口 | ✅ |
| IX. 可演示 Demo | 每 user story 可演示, 三个验收 Demo 全跑通 | ✅ |

**Gate Result**: 全部通过 ✅。无宪法违规。

## Project Structure

### Documentation (this feature)

```text
specs/001-oryxos-core-runtime/
├── plan.md              # Implementation plan
├── research.md          # Phase 0: research & decisions
├── data-model.md        # Phase 1: data model
├── quickstart.md        # Phase 1: validation guide
├── contracts/           # Phase 1: interface contracts
│   ├── api-contracts.md
│   └── tool-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md             # (created by /speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-core/            # Core abstractions, ReActLoop, AgentService, etc.
oryxos-provider/        # ProviderService, ChatModel mapping
oryxos-memory/          # MemoryService, LongTermMemory stores
oryxos-tool/            # Built-in tools, MCP client, Sandbox, Notify
oryxos-channel-cli/     # CLI interaction channel
oryxos-web/             # REST API controllers
oryxos-storage/         # SQLite, JPA repositories
oryxos-cli/             # Picocli commands
oryxos-boot/            # Spring Boot entry point
```

**Structure Decision**: 9-module Maven multi-module project, already established. Follow existing conventions.

## Complexity Tracking

> No constitution violations to justify. Skip.

## Phase 0: Research

No NEEDS CLARIFICATION items exist — all technical decisions are defined in the constitution and spec. Research covers implementation approach consolidation only (see [research.md](research.md)).

## Phase 1: Design Artifacts

| 产出 | 文件 |
|------|------|
| 数据模型 | [data-model.md](data-model.md) |
| 接口契约 | [contracts/](contracts/) |
| 快速验证指南 | [quickstart.md](quickstart.md) |