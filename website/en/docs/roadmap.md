# Roadmap

Our development philosophy: **slow is fast, restrained and focused**. Build a solid single-machine runtime kernel first, then grow distributed capabilities incrementally.

## Phase 1 (Current): Single-machine Runtime Kernel

- Five core capabilities working: config-as-Agent, multi-agent coexistence, REST API, MCP integration
- Make single-node Agent management genuinely usable

### Four-week Cadence

| Week | Focus | Demo Outcome |
|------|-------|-------------|
| Week 1 | LLM integration + ReAct loop | `oryxos chat` multi-turn conversation, Agent queries weather via ReAct + HTTP Tool |
| Week 2 | Memory + Tool system | Agent remembers user preferences, accesses local files and external MCP servers |
| Week 3 | Web Service | External systems fully interact via 10 REST endpoints |
| Week 4 | Multi-agent demo + engineering polish | Multi-agent coexistence, complete CLI, session recovery, scheduled tasks, website live |

### Three Acceptance Demos

| Demo | Agent Form | Verified Capabilities |
|------|-----------|----------------------|
| Daily Weather | Bare AGENT.md | LLM + ReAct + built-in HTTP Tool + NotifyTools + scheduling |
| Daily Tech Digest | AGENT.md + skills/ sub-instructions | Memory + MCP method 2 + on-demand `read_file` |
| Daily GitHub Digest | AGENT.md + scripts/ | `shell` script execution + sandbox trust boundary |

## Phase 2 (Planned): Distributed Foundation

- Node statelessness, external state, multi-replica deployment
- Support larger scale and high availability

## Phase 3 (Vision): Cross-node Agent Collaboration

- Agent communication infrastructure, A2A integration
- Cross-node discovery, delegation, reliable async collaboration

## Horizontal Capabilities (progressively added across phases)

- Multi-tenancy, SSO, complete audit, tool policies, observability, web management
