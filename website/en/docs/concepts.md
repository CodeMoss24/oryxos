# Core Concepts

## ReAct Loop

The core engine of OryxOS is a custom ReAct (Reason + Act) loop:

1. Receive user message, append to session history
2. Assemble prompt (system prompt + Memory + conversation history + Tool list)
3. Call LLM for response
4. If response contains Tool calls → execute Tools → append results to history → go to step 2
5. If no Tool calls → return final response
6. Force-stop after max 10 iterations

**Key Design**: ReAct is fully custom, not dependent on Spring AI's Agent abstraction. Spring AI is only used for Provider abstraction + protocol conversion. Tool scheduling is controlled by `ReActLoop` + `ToolExecutor`.

## Three-tier Memory

| Tier | Purpose | Characteristics |
|------|---------|-----------------|
| Session History | Current conversation context | Truncated by `maxHistoryTurns` |
| Core Memory | Permanent critical info | Full injection into system prompt, never truncated |
| Archival Memory | Historical knowledge | Keyword search + truncation |

Three backends switchable via config: `markdown` (default) / `sqlite` / `mem0` — no upper-layer code changes.

## Tool System

Unified `OryxTool` interface — built-in Tools, `@Tool` plugins, and MCP Tools are all wrapped as `OryxTool` and registered in `ToolRegistry`.

Three integration tiers:

| Method | Barrier | Recommendation |
|--------|---------|---------------|
| AGENT.md + existing MCP server | Zero code | ⭐⭐⭐ |
| Custom MCP server | Light code | ⭐⭐ |
| Java `@Tool` Bean | Heavy code | ⭐ |

## Sandbox

Three-layer security boundary: file path whitelist, command whitelist, domain whitelist. Interface-first design — upgrade to container isolation or microVM without changing callers.

## Agent as Directory

`.oryxos/agents/<name>/AGENT.md` = one Agent. Frontmatter defines Profile, body defines task instructions. Sub-instructions and scripts in the Agent directory are not pre-loaded — loaded on demand.
