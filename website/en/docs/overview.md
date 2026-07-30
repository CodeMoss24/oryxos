# Overview

OryxOS is a privately-deployed Agent OS for enterprises: **you issue a task in natural language → the platform decomposes it → organizes an Agent team → multiple Agents collaborate → delivers a result.** Enabling every company to run its own Agents with natural language.

## Why OryxOS

Every company has work that should be delegated to Agents, but Agents are mostly stuck at the demo stage, blocked by four barriers:

- **Defining an Agent requires code** — the people who know the business best can't create one
- **Cloud platforms take your data** — compliance won't allow it
- **Execution is a black box** — no audit, no whitelists, no approval — enterprises won't go to production
- **One Agent is easy, a fleet is hard** — no one provides the "Agent OS" layer

OryxOS removes all four barriers at once: natural language definition, private deployment, full audit + sandbox, and lifecycle management for an entire Agent fleet.

## Agent Harness OS

An Agent runtime is the execution kernel that makes a single Agent run. Agent Harness OS sits above the runtime, managing a fleet of Agents: lifecycle management, unified channels, shared memory, multi-tenancy, governance, and cross-node collaboration in distributed deployments.

**A runtime makes one Agent run; Agent Harness OS makes a fleet of Agents run and be managed.** OryxOS is the latter.

## Five Core Capabilities

- **LLM Integration**: Unified Provider abstraction for mainstream LLMs, runtime switching with no lock-in
- **ReAct Loop**: Custom reasoning engine with fully controllable loop behavior
- **Memory**: Session memory + long-term memory, interface reserved for vector search upgrade
- **Tool System**: Built-in tools + three integration tiers (zero-code / light-code / heavy-code)
- **Web Service**: REST API exposure, integrable from any language

## Core Features

- 🤖 **One directory = one Agent**: Define an Agent with `AGENT.md`, no code needed
- ☕ **Java-native**: JDK 21, single JAR deployment, leverage existing Java ops tooling
- 🔒 **Private & controllable**: Deploy on your own servers, data never leaves
- 🛡️ **Security isolation**: Whitelist validation + mandatory sandbox + full audit trail
- 🧠 **Custom ReAct**: Core loop self-implemented, no external framework dependency
- 🔌 **Open standards**: Tools via MCP, collaboration via A2A
- 🧩 **Three-tier tool extension**: Zero-code → light-code → heavy-code, choose by barrier
- 💾 **Cross-conversation memory**: Session + long-term two-layer memory
- 🌐 **Stateless & scalable**: External state, ready for distributed evolution

## Design Principles

- **Platform over Agent**: The most important deliverable is an environment where any Agent can reliably run
- **Config-as-Agent**: An Agent is defined by configuration, not code
- **Open standards**: Tools via MCP, collaboration via A2A — work with the ecosystem
- **Security is foundation, not patch**: Security built into architecture from day one
- **Phased restraint**: Every architectural upgrade justified by real usage data

## Next Steps

Read [Core Concepts](/en/docs/concepts) to understand OryxOS design philosophy, or jump to [Quick Start](/en/docs/quick-start) to run your first Agent.
