# ReAct Loop

ReAct (Reason + Act) is the most critical piece of code in OryxOS. Custom implementation, ~dozens of lines of Java, not dependent on Spring AI's Agent abstraction.

## Algorithm

```
1. Receive user message, append to session history
2. Assemble prompt (system prompt + Bootstrap + Skill + Memory + conversation history + available Tools)
3. Call LLM Provider for response
4. If no Tool calls → return final response
5. If Tool calls → execute Tools, append results to history
6. Go to step 2
7. Force-stop after max iterations (default 10)
```

## Prompt Assembly Order

1. **System Prompt**: `AGENT.md` body + Bootstrap (current date/time appended at end)
2. **Memory Injection**: Session history + long-term memory
3. **Conversation History**: Truncated by `maxHistoryTurns`
4. **Available Tool List**

## Three Trigger Sources

- **CLI** (`oryxos chat`) — human-driven
- **Web Service** (`POST /agents/{name}/invoke`) — human-driven
- **AgentScheduler** (cron-triggered) — clock-driven

All three call `AgentService.process`. ReActLoop is unaware of the trigger source.

## Context Length Management

Core phase strategy: retain system prompt and the most recent N turns (N configurable via Profile, default 20). Excess is discarded.
