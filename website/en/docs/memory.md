# Memory

`MemoryService` provides a unified facade, exposing a single interface to the ReAct loop.

## Three-tier Architecture

| Tier | Purpose | Storage | Characteristics |
|------|---------|---------|-----------------|
| Session History | Current conversation context | Session object | Truncated by `maxHistoryTurns` |
| Core Memory | Permanent critical info | `## Core Memory` | Full injection into system prompt, never truncated |
| Archival Memory | Historical knowledge | `## Archival Memory` | Keyword search + truncation |

## Explicit Scope

`save_memory(content, scope)` requires the Agent to explicitly specify scope:

- `CORE`: Core memory — full injection, never truncated, not searchable
- `ARCHIVAL`: Archival memory — truncation + keyword search

The system does not guess scope — the Agent must specify it.

## Three Backends

| Backend | Config Value | Description |
|---------|-------------|-------------|
| MarkdownMemoryStore | `markdown` (default) | Read/write `MEMORY.md` file |
| SqliteMemoryStore | `sqlite` | Embedded SQLite |
| Mem0MemoryStore | `mem0` | Cloud Mem0 service |

Switch backends by changing config only — no upper-layer code changes.
