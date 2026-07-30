# CLI Commands

OryxOS provides 12 CLI commands built on Picocli.

## Startup & Status

| Command | Description |
|---------|-------------|
| `oryxos init` | Initialize workspace |
| `oryxos status` | Show configuration and runtime status |
| `oryxos chat [--profile <name>]` | Interactive chat |
| `oryxos serve` | Start HTTP API server (default port 8080) |
| `oryxos gateway` | Start multi-channel daemon |

## Profile Management

| Command | Description |
|---------|-------------|
| `oryxos profile list` | List all profiles |
| `oryxos profile create <name>` | Create new profile (generates minimal AGENT.md template) |
| `oryxos profile show <name>` | Show profile details |
| `oryxos profile delete <name>` | Delete profile (entire directory) |

## Queries

| Command | Description |
|---------|-------------|
| `oryxos provider list` | List configured providers |
| `oryxos tool list` | List registered tools |
| `oryxos session list` | List session history |

## Startup Modes

- Commands that don't need Spring context (`init`, `profile list`) use direct file operations — fast startup
- Commands requiring LLM calls (`chat`, `serve`, `gateway`) start the Spring context
