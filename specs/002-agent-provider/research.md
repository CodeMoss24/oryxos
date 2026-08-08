# Research: Agent Provider

## 1. Spring AI 1.0.0-M4 API Surface

**Decision**: Use `ChatModel.call(Prompt)` API from Spring AI 1.0.0-M4.

**Rationale**: Spring AI 1.0.0-M4 (milestone) is the BOM version locked in the project. The `ChatModel` interface provides the core `call(Prompt)` method. Tool calling in Spring AI is handled through `Prompt` options — specifically `ChatOptions` with tool definitions and a flag to control auto-execution.

**Alternatives considered**:
- Spring AI 1.0.0-SNAPSHOT: Unstable, no release guarantees
- Direct HTTP calls to each LLM API: Defeats the purpose of Provider abstraction, requires maintaining per-provider protocol adapters

**Key API notes for implementation**:
- `ChatModel.call(Prompt)` returns `ChatResponse` containing `List<Generation>` with `ChatGenerationMetadata`
- Tool definitions are passed via `Prompt.getOptions()` → `ChatOptions.getTools()` (Spring AI's `ToolCallback` or `FunctionCallback` types)
- Auto-execution control: Spring AI's default `ChatClient` can auto-execute; the raw `ChatModel.call()` with explicitly configured options disables this
- Token usage: Available via `ChatResponse.getMetadata().getUsage()` → `Usage` (prompt tokens, generation tokens, total tokens)

## 2. SnakeYAML Profile Parsing

**Decision**: Use SnakeYAML 2.3 (managed by Spring Boot starter parent) to parse Profile YAML files. No Spring Boot `@ConfigurationProperties` binding — manual `Yaml.load()` + type-safe mapping.

**Rationale**: Profile files live outside Spring's config path (`.oryxos/profiles/`), and the set of files is dynamic (scan directory). `@ConfigurationProperties` is designed for `application.yaml` properties, not arbitrary file parsing.

**Alternatives considered**:
- Jackson YAML: Requires additional dependency; SnakeYAML is already included via Spring Boot
- Spring `@ConfigurationProperties`: Not designed for external file scanning

**Implementation notes**:
- `Yaml.load(inputStream)` returns `Map<String, Object>`; map manually to Profile record
- `${ENV_VAR}` resolution: scan string values for `${...}` pattern, resolve via `System.getenv()`
- SnakeYAML's `Constructor` can be used for direct object mapping if the YAML structure matches exactly

## 3. SQLite + Spring Data JPA with Manual Schema

**Decision**: Use Spring Data JPA with SQLite JDBC driver (3.46.1.3). Schema creation via `schema.sql` executed manually or via `spring.sql.init.schema-locations` — NOT via `hibernate.ddl-auto=update`.

**Rationale**: SQLite's ALTER TABLE is weak; `ddl-auto=update` silently corrupts or skips migrations. Manual DDL ensures the schema exactly matches expectations. `schema.sql` placed in `oryxos-storage/src/main/resources/` can be loaded by Spring's `DataSourceInitializer`.

**Alternatives considered**:
- Flyway/Liquibase: Overkill for 5 tables in core phase; deferred to extension phase
- Hibernate `ddl-auto=update`: SQLite incompatibility risk — rejected per constitution VI

**Implementation notes**:
- Hibernate dialect: `org.hibernate.community.dialects.SQLiteDialect` (hibernate-community-dialects dependency)
- `spring.jpa.hibernate.ddl-auto=none` (or `validate` once schema.sql is stable)
- `spring.sql.init.mode=always` for development; change to `never` once stable
- `llm_calls` has auto-increment PK `id`; SQLite uses `INTEGER PRIMARY KEY AUTOINCREMENT`

## 4. ChatModel Bean Creation from Configuration

**Decision**: Create `ChatModel` beans programmatically in `ProviderAutoConfiguration` based on `oryxos.providers` list in `application.yaml`. Each provider entry creates a `ChatModel` via Spring AI Alibaba's builder, registered in a `Map<String, ChatModel>`.

**Rationale**: Per constitution, provider-name-to-ChatModel must be an explicit mapping, not type-scanning. Spring AI Alibaba's `SpringAiAlibabaAutoConfiguration` creates `ChatModel` beans, but we need named instances for the explicit map.

**Alternatives considered**:
- `@Qualifier` on individually declared `@Bean` methods: Works but doesn't support dynamic provider count from config
- Type-scanning `List<ChatModel>`: Cannot distinguish providers — rejected per known trap

**Implementation notes**:
- `@ConfigurationProperties("oryxos")` to bind the providers list
- For each entry, create a `ChatModel` using the Spring AI Alibaba builder pattern
- `@Primary` not needed — the map is the source of truth; nothing autowires ChatModel directly
- Need to verify Spring AI 1.0.0-M4 ChatModel builder API (may differ from GA versions)

## 5. Tool Schema Translation (No Execution)

**Decision**: `ToolSchemaAdapter.toSpringAiTools(List<OryxTool>)` produces descriptions/schemas suitable for inclusion in the LLM request's tool definitions. It does NOT produce executable callbacks.

**Rationale**: Per constitution IV, Spring AI's auto-execution must be disabled. The adapter only generates metadata (name, description, JSON Schema for parameters). The actual tool execution happens in `ToolExecutor` (lesson 17).

**Implementation notes**:
- `OryxTool.getInputSchema()` returns a `Map<String, Object>` representing JSON Schema
- Translate to Spring AI's `FunctionCallback` or tool definition format appropriate for Spring AI 1.0.0-M4
- The key test: output of `toSpringAiTools()` must contain no executable code or callback references
- Exact API for tool definitions in Spring AI 1.0.0-M4 to be confirmed during implementation (check `ChatOptions.Builder.tools()` or `Prompt` constructor)

## 6. Build Infrastructure Fix

**Issue**: `oryxos-boot/pom.xml` has `spring-boot-starter-actuator` and `micrometer-registry-prometheus` without versions, and the parent `dependencyManagement` section also omits versions for these artifacts. These versions should come from `spring-boot-starter-parent`.

**Fix options**:
- Remove from parent `dependencyManagement` (let Spring Boot parent manage them)
- Or add explicit versions to `dependencyManagement`

**Resolution**: Will fix during implementation — the actuator and micrometer dependencies are managed by `spring-boot-starter-parent` 3.3.5, so either removing them from dependencyManagement or ensuring proper version cascade should resolve this.