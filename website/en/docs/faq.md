# FAQ

## How is OryxOS different from other Agent frameworks?

OryxOS is not an Agent framework — it's an Agent OS. Frameworks help you build an Agent; OryxOS helps you run and manage a fleet of Agents. Key differentiators: private deployment, full audit trail, multi-agent shared foundation, natural language Agent definition.

## Why Java instead of Python?

Enterprise deployment. Regulated industries (finance, healthcare, government) run on Java infrastructure with teams familiar with Java tooling. JDK 21 virtual threads provide competitive concurrency. Single JAR deployment, simple operations.

## Why custom ReAct instead of LangChain?

Control and simplicity. The custom ReAct loop is only dozens of lines of code, fully controllable, no dependency on external framework abstractions. Spring AI is used only for Provider abstraction + protocol conversion. Tool scheduling is entirely controlled by OryxOS.

## Where is data stored?

Core phase: all local. SQLite (sessions, audit, metadata) + filesystem (Agent definitions, Memory, config). Data never leaves your premises, no cloud services required.

## Which LLMs are supported?

All OpenAI-compatible models, including OpenAI, Qwen, Zhipu, DeepSeek, and more. Unified access via Spring AI's Provider abstraction with runtime switching and no lock-in.

## How is security ensured?

Three-layer security:
1. **Sandbox**: File path, command, and domain whitelists
2. **Audit**: `tool_invocations` and `llm_calls` tables persisted from day one
3. **Credentials**: API keys managed through enterprise secret management, never stored in plaintext

## How many Agents can run?

Core phase: single-machine. Instance count depends on hardware and Agent load. Virtual threads provide high concurrency. Extension phase supports multi-replica deployment and distributed architecture.
