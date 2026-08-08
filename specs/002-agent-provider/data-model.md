# Data Model: Agent Provider

## Entities

### Profile (Value Object / Record)

Not persisted to SQLite — loaded from YAML files in `.oryxos/profiles/` at startup, held in memory (`ProfileRegistry`).

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Unique profile name (from filename or frontmatter) |
| `description` | String | No | Human-readable description |
| `identity` | Identity | No | Agent identity sub-record |
| `provider` | ProviderRef | Yes | Provider selection sub-record |
| `tools` | List\<String\> | No | Tool names enabled for this profile |
| `skills` | List\<String\> | No | Skill names enabled |
| `mcpServers` | List\<String\> | No | MCP server references |
| `channels` | List\<String\> | No | Active channels (cli, webhook, etc.) |
| `notifyChannels` | List\<NotifyChannelConfig\> | No | Notification targets |
| `schedules` | List\<ScheduleConfig\> | No | Cron-triggered task definitions |
| `bootstrap` | List\<String\> | No | Bootstrap files to load (AGENTS.md, SOUL.md, USER.md) |
| `settings` | Settings | No | Operational settings |

**Identity sub-record**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentName` | String | No | Display name for this agent |
| `prompt` | String | No | System prompt override |

**ProviderRef sub-record**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Provider name (must match global config) |
| `model` | String | Yes | Model identifier (e.g., deepseek-chat) |
| `temperature` | double | No | Sampling temperature (default: 0.7) |

**Settings sub-record**:

| Field | Type | Required | Default |
|-------|------|----------|---------|
| `maxIterations` | int | No | 10 |
| `maxHistoryTurns` | int | No | 20 |

### Provider Configuration (non-persistent)

Declared in `application.yaml` under `oryxos.providers`. Not a database entity — used to construct `ChatModel` instances at startup.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Unique provider identifier (e.g., deepseek, kimi) |
| `apiKey` | String | Yes | `${ENV_VAR}` reference to environment variable holding the API key |

### LlmCall (JPA Entity → `llm_calls` table)

**Table**: `llm_calls`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK, AUTOINCREMENT | Unique record ID |
| `session_id` | TEXT | NOT NULL | Session identifier (from caller) |
| `provider` | TEXT | NOT NULL | Provider name used |
| `model` | TEXT | NOT NULL | Model identifier used |
| `prompt_tokens` | INTEGER | | Token count in prompt |
| `completion_tokens` | INTEGER | | Token count in completion |
| `total_tokens` | INTEGER | | Total tokens consumed |
| `duration_ms` | BIGINT | NOT NULL | Wall-clock duration of the call |
| `success` | INTEGER | NOT NULL, DEFAULT 0 | 1 = success, 0 = failure |
| `error_message` | TEXT | | Error details (null on success) |
| `created_at` | TEXT | NOT NULL | ISO-8601 timestamp |

**DDL** (`schema.sql`):
```sql
CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    duration_ms BIGINT NOT NULL,
    success INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TEXT NOT NULL
);
```

### Prompt (Value Object)

Input to `ProviderService.chat()`. Carries the message content and available tool list.

| Field | Type | Description |
|-------|------|-------------|
| `messages` | List\<Message\> | Conversation messages to send to LLM |
| `availableTools` | List\<OryxTool\> | Tools available for this call (for schema generation) |

### ProviderService Response (Value Object)

Output from `ProviderService.chat()`. Wraps the LLM response.

| Field | Type | Description |
|-------|------|-------------|
| `content` | String | Text response from LLM |
| `toolCalls` | List\<ToolCallRequest\> | Tool calls requested by LLM (if any) |
| `usage` | Usage | Token usage metadata |

## Relationships

```
application.yaml (oryxos.providers)
    │
    └──[builds]──> Map<String, ChatModel>
                        │
                        │ [lookup by profile.provider.name]
                        │
Profile (from .oryxos/profiles/*.yaml) ──[passed to]──> ProviderService.chat()
                                                            │
                                                            │ [audit]
                                                            ▼
                                                    LlmCall (llm_calls table)
```

## Validation Rules

1. Profile `provider.name` must exist in global `oryxos.providers` list — if not, the Profile is invalid (logged, not loaded).
2. Profile YAML files must be parseable — malformed files are skipped with error log, do not block startup.
3. `${ENV_VAR}` references must resolve to non-null values — unresolvable placeholders should produce a clear validation error.
4. `ChatModel` lookup failure (profile.provider.name not in map) throws `ProviderNotFoundException`.