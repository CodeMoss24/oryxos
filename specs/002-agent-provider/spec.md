# Feature Specification: Agent Provider

**Feature Branch**: `016-lesson16-provider`

**Created**: 2026-08-07

**Status**: Draft

**Input**: User description: "第16节需求：Provider——Agent和大模型之间的统一前台"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multi-Provider Routing (Priority: P1)

As a developer managing multiple LLM providers, I want the system to route each agent's LLM calls to the correct provider based on configuration, so that I can configure DeepSeek for production workloads and Kimi for experimentation without either interfering with the other.

**Why this priority**: This is the core function of the Provider abstraction — without correct routing, the entire system cannot talk to LLMs. The explicit mapping between provider names and models is the foundation that all downstream features depend on.

**Independent Test**: Configure two providers, send a request targeting one, and verify only that provider's model was invoked while the other was never touched.

**Acceptance Scenarios**:

1. **Given** two providers (deepseek and kimi) are configured, **When** a request arrives with Profile specifying "kimi", **Then** the kimi model is called and deepseek is never invoked.
2. **Given** a Profile references a provider name not in the global configuration, **When** the request is made, **Then** a clear error is raised immediately (no fallback to a wrong provider).

---

### User Story 2 - Audit Trail for Every LLM Call (Priority: P2)

As a compliance officer or system operator, I want every LLM call — successful or failed — to be recorded with provider, model, token usage, duration, and outcome, so that I can audit all AI interactions and trace any incident back to its root cause.

**Why this priority**: Auditability is OryxOS's key differentiator from other Agent OS platforms. Without audit records for failed calls, a production incident (timeout, rate limit, model error) leaves zero trace in the system — making post-mortem analysis impossible.

**Independent Test**: Trigger both a successful and a failed LLM call, then verify both produce audit records with the correct success/failure flags and metadata.

**Acceptance Scenarios**:

1. **Given** a valid LLM call completes successfully, **When** the response returns, **Then** an audit record is persisted with success=true, provider name, model name, token counts, and duration.
2. **Given** an LLM call fails (timeout/rate limit/model error), **When** the exception occurs, **Then** an audit record is persisted with success=false and the error message, AND the exception continues to propagate to the caller.
3. **Given** the audit storage table has been created, **When** records are inserted and queried, **Then** both the success flag and error_message column are present and correctly populated.

---

### User Story 3 - Tool Schema Translation Without Execution (Priority: P3)

As the ReAct loop engine, I want the Provider layer to translate tool definitions into LLM-compatible schemas without executing any tools, so that I retain full control over tool scheduling, sandbox enforcement, and execution ordering.

**Why this priority**: This enforces the boundary between "describing tools to the LLM" and "running tools". If the Provider executes tools automatically, sandbox checks are bypassed and tools may be invoked twice (once by Provider, once by ToolExecutor).

**Independent Test**: Pass tool definitions to the Provider, verify the resulting request contains tool schemas but has auto-execution disabled, and verify no tool execution side effects occurred.

**Acceptance Scenarios**:

1. **Given** a set of available tools with their input schemas, **When** the Provider builds an LLM request, **Then** the request includes translated tool schemas in the correct format AND auto-execution is explicitly disabled.
2. **Given** tool schemas are generated, **When** the translation output is inspected, **Then** it contains only schema metadata (names, descriptions, parameter definitions) with no execution logic.

---

### Edge Cases

- What happens when the global provider configuration is empty (no providers declared)? The system should fail fast at startup with a clear message.
- What happens when a Profile YAML file is malformed (invalid YAML syntax)? That Profile should be skipped with an error log; other valid Profiles should still load.
- What happens when a Profile references an environment variable that is not set? The placeholder should be resolved to empty or fail with a clear validation error.
- What happens when the LLM call duration exceeds expectations? The duration is recorded in the audit record regardless of outcome.
- What happens when multiple Profiles reference the same provider? Each gets routed correctly via the shared mapping — no interference.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST load Profile configurations from YAML files in the profiles directory at startup, parsing all defined fields (name, description, identity, provider, tools, skills, mcp_servers, channels, notify_channels, schedules, bootstrap, settings).
- **FR-002**: System MUST validate each loaded Profile — at minimum, verify the referenced provider name exists in the global provider configuration. Invalid Profiles MUST be logged as errors but MUST NOT prevent other valid Profiles from loading.
- **FR-003**: System MUST resolve `${ENV_VAR}` placeholders in configuration values from environment variables at load time.
- **FR-004**: System MUST maintain a global provider registry (declared in application configuration) where each provider declares its name and credential source (environment variable reference). No plaintext credentials in configuration files.
- **FR-005**: System MUST maintain an explicit mapping from provider name to the underlying model instance, built at startup from the global provider registry. Type-scanning approaches that cannot distinguish providers of the same type are forbidden.
- **FR-006**: System MUST provide a unified `chat` interface that accepts a session identifier, a Profile, and a Prompt, and returns the LLM response. The method MUST: (a) select the correct model by provider name from the explicit mapping, (b) raise a clear error if the provider is not found, (c) include tool schemas in the request when tools are available, (d) disable automatic tool execution.
- **FR-007**: System MUST translate internal tool definitions into LLM-compatible tool schemas (names, descriptions, parameter definitions). The translation output MUST NOT include any execution logic or trigger any tool side effects.
- **FR-008**: System MUST persist an audit record for every LLM call — both successful and failed — containing: session identifier, provider name, model name, token usage (prompt/completion/total), duration in milliseconds, success flag, and error message (on failure). The record MUST be written before the response is returned (success) or the exception propagates (failure).
- **FR-009**: System MUST persist audit records into a dedicated storage table with columns for success flag and error message, created via a manual schema script (not auto-generated by the ORM).

### Key Entities

- **Profile**: An agent's runtime configuration, loaded from YAML. Contains identity (name, description, agent_name, prompt), provider selection (name, model, temperature), tool/service/channel declarations, schedule definitions, bootstrap file references, and operational settings (max_iterations, max_history_turns).
- **Provider Configuration**: A global declaration of available LLM backends, each with a unique name and a credential source (environment variable). Defines "what can we connect to" at the system level.
- **LLM Call Record (Audit)**: An immutable record of a single LLM invocation. Captures which provider and model were used, the session context, token consumption, wall-clock duration, and whether it succeeded (with error details on failure).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All unit tests (routing, validation, audit, translation) pass without requiring network access — tests complete in under 5 seconds.
- **SC-002**: A manual integration test with real credentials successfully completes an LLM call and verifies the audit record was persisted.
- **SC-003**: A grep for plaintext credentials (e.g., key prefixes like "sk-") across all source files and configuration files returns zero matches.
- **SC-004**: Every functional requirement (FR-001 through FR-009) is covered by at least one automated test assertion.
- **SC-005**: Malformed or invalid Profile files do not prevent the system from starting with other valid Profiles.

## Assumptions

- The underlying LLM communication protocol is OpenAI-compatible, which is the de facto industry standard.
- Environment variables are the mechanism for injecting sensitive credentials; the runtime environment is responsible for setting them before process start.
- The persistence layer uses an embedded database suitable for single-binary deployment; schema migrations are handled via manual scripts rather than ORM auto-generation.
- Tool definitions exist as an abstraction (OryxTool interface) that the translation layer consumes; the Provider itself does not define or discover tools.
- Profile YAML files are placed in a known directory relative to the working directory, created manually by the operator (no upload API in this phase).
- This feature is the first module built — no upstream dependencies on other OryxOS modules beyond the shared Maven project structure.
- The system runs as a single process; multi-instance coordination is out of scope for this phase.