# Agent Development Guide

OryxOS core philosophy: **one directory = one Agent**. Define Agents with `AGENT.md`, not code.

## Agent Directory Structure

```
.oryxos/agents/<name>/
├── AGENT.md          # Required: Agent definition (frontmatter = Profile, body = task instructions)
├── skills/           # Optional: sub-instructions
├── scripts/          # Optional: scripts
└── REFERENCE.md      # Optional: reference material
```

## AGENT.md Format

```markdown
---
provider: openai
model: gpt-4o
max_iterations: 10
schedules:
  - cron: "0 9 * * *"
    message: "Query daily weather and push notification"
notify_channels:
  - type: webhook
    url: https://hooks.example.com/notify
---

You are a daily weather assistant. Every morning, query the weather
for the specified city, summarize in concise language, and push
the result via notify.
```

- **Frontmatter**: Agent's own Profile config, auto-derived by `AgentLoader.deriveProfile()`
- **Body**: Task instructions, injected into system prompt

## Zero-code Agent Example

Create a weather Agent without writing any Java code:

1. `oryxos profile create daily-weather`
2. Edit `.oryxos/agents/daily-weather/AGENT.md`
3. Define intent + configure schedule + configure notify channel
4. ReAct + built-in Tools handle the rest automatically

## Key Principles

- **`AGENT.md` is not an executable Tool**: Loaded by `oryxos-core`'s `ContextLoader`, body injected into system prompt, not registered in `ToolRegistry`
- **Sub-instructions/scripts are not pre-loaded**: Agent body guides on-demand reading via `read_file` / `shell` — progressive disclosure
- **No separate Profile YAML**: `.oryxos/profiles/` is removed, Profiles are derived from AGENT.md frontmatter
