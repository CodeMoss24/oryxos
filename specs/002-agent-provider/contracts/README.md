# Contracts: Agent Provider

## ProviderService

The primary public API. Consumed by `ReActLoop` (lesson 17).

```java
package com.oryxos.provider;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.OryxTool;

/**
 * Unified LLM calling facade.
 *
 * Responsibilities:
 * - Selects the correct ChatModel by profile.provider.name
 * - Assembles messages + tool schemas (auto-execution OFF)
 * - Initiates the call and records audit (success AND failure)
 * - Returns response (content + tool call requests)
 *
 * Does NOT:
 * - Implement loops (that's ReActLoop)
 * - Execute tools (that's ToolExecutor)
 * - Manage context (that's PromptBuilder)
 */
public interface ProviderService {
    Response chat(String sessionId, Profile profile, Prompt prompt);
}
```

Where `Prompt` and `Response` are:

```java
public record Prompt(List<Message> messages, List<OryxTool> availableTools) {}
public record Response(String content, List<ToolCallRequest> toolCalls, Usage usage) {}
public record Usage(int promptTokens, int completionTokens, int totalTokens) {}
public record ToolCallRequest(String name, Map<String, Object> arguments) {}
```

## ToolSchemaAdapter

Translates internal tool definitions to LLM-compatible format. No execution.

```java
package com.oryxos.provider;

public interface ToolSchemaAdapter {
    /**
     * Translate OryxOS tool definitions to the target LLM format.
     * Output must NOT contain executable callbacks or trigger side effects.
     */
    List<Object> toTargetTools(List<OryxTool> tools);
}
```

The exact return type (`List<Object>`) is abstract to accommodate Spring AI API evolution — the actual type will be confirmed during implementation against Spring AI 1.0.0-M4.

## ProfileLoader

Loads and validates Profile YAML files.

```java
package com.oryxos.core.profile;

public interface ProfileLoader {
    /** Scan .oryxos/profiles/, parse valid YAML, return loaded profiles. */
    List<Profile> loadAll();

    /** Resolve ${ENV} placeholders in string values. */
    String resolvePlaceholders(String value);
}
```

## ProfileRegistry

In-memory index of loaded Profiles.

```java
package com.oryxos.core.profile;

public interface ProfileRegistry {
    void register(Profile profile);
    Profile get(String name);
    List<Profile> listAll();
    int size();
}
```

## LlmCallRepository

Standard Spring Data JPA repository for the `llm_calls` audit table.

```java
package com.oryxos.storage.repository;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {}
```

Where `LlmCall` entity fields map directly to the DDL in data-model.md.

## OryxTool (Forward Reference)

Declared in `oryxos-core` for use by `ProviderService` and `ToolSchemaAdapter`. Full implementation in lesson 20.

```java
package com.oryxos.core.tool;

public interface OryxTool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    ToolResult execute(Map<String, Object> arguments);
}
public record ToolResult(String content, boolean success, String errorMessage) {}
```

## Audit Interface

```java
package com.oryxos.provider;

public interface LlmCallAudit {
    void record(String sessionId, String provider, String model,
                Usage usage, boolean success, String errorMessage, long durationMs);
}
```

The `record` method is called in a try/catch around the LLM call — success path records immediately after response; failure path records in `catch` block before re-throwing.