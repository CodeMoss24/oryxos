# Quickstart: Agent Provider

## Prerequisites

1. **JDK 21** installed: `java -version` → `21.x`
2. **Maven 3.9+** installed: `mvn --version`
3. **Environment variables** (for integration smoke test):
   - Set at least one provider API key, e.g. `export DEEPSEEK_API_KEY=sk-xxx`
4. **Build fix** (if needed): Ensure `oryxos-boot/pom.xml` actuator/micrometer dependencies have resolved versions (see research.md §6)

## Build and Test Commands

### Full build (all checks: Spotless, PMD, SpotBugs, tests)
```bash
mvn clean verify
```

Expected: All checks pass. This includes code formatting, static analysis, and unit tests.

### Unit tests only (fast, no network)
```bash
mvn test
```

Expected: All tests in `oryxos-core`, `oryxos-provider`, `oryxos-storage` pass in under 5 seconds.

### Module-specific tests
```bash
# Profile loading tests
mvn test -pl oryxos-core -Dtest="ProfileLoaderTest"

# Provider routing + audit + tool schema tests
mvn test -pl oryxos-provider -Dtest="ProviderServiceTest,ToolSchemaAdapterTest"

# Storage/repository tests (uses in-memory SQLite)
mvn test -pl oryxos-storage -Dtest="LlmCallRepositoryTest"
```

### Integration smoke test (requires real API key)
```bash
export DEEPSEEK_API_KEY=sk-xxx
mvn test -pl oryxos-provider -Dtest="ProviderSmokeIT" -Dgroups=integration
```

Expected: Single LLM call completes, response is non-null, `llm_calls` table has one `success=true` record.

## Verification Checklist

After implementation:

1. **`mvn clean verify`** — all static checks + tests pass
2. **`grep -r "sk-" src/`** — zero matches (no plaintext keys in code or config)
3. **`mvn dependency:tree -pl oryxos-provider`** — Spring AI Alibaba dependencies resolve correctly from BOM
4. **Manual schema verification** — `schema.sql` in `oryxos-storage/src/main/resources/` exists and matches `data-model.md` DDL
5. **Profile YAML example** — `.oryxos/profiles/` contains at least one valid profile YAML file

## Expected Module Dependencies

```
oryxos-core
├── Spring Boot Starter (provided by parent)
├── SnakeYAML (via Spring Boot parent)
└── (no dependencies on other OryxOS modules)

oryxos-provider
├── oryxos-core (for Profile, OryxTool)
├── Spring AI Auto-Configuration
├── Spring AI Alibaba (ChatModel)
└── (no dependencies on oryxos-storage — audit is via interface)

oryxos-storage
├── Spring Boot Starter Data JPA
├── SQLite JDBC Driver
├── Hibernate Community Dialects (SQLiteDialect)
└── (no dependencies on other OryxOS modules)
```

## Sample Provider Configuration

```yaml
# application.yaml
oryxos:
  providers:
    - name: deepseek
      api-key: ${DEEPSEEK_API_KEY}
    - name: kimi
      api-key: ${KIMI_API_KEY}
```

## Sample Profile YAML

```yaml
# .oryxos/profiles/ops-agent.yaml
name: ops-agent
description: "Operations support agent"
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
identity:
  agent_name: "Ops Agent"
  prompt: "You are a helpful operations assistant."
tools:
  - read_file
  - write_file
skills:
  - system-monitor
channels:
  - cli
notify_channels:
  - type: webhook
    url: ${WEBHOOK_URL}
bootstrap:
  - AGENTS.md
  - SOUL.md
settings:
  max_iterations: 10
  max_history_turns: 20
```