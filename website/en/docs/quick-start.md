# Quick Start

Run your first OryxOS Agent in 5 minutes.

## Prerequisites

- JDK 21+
- Maven 3.8+
- An LLM API key (OpenAI-compatible protocol)

## 1. Initialize Workspace

```bash
git clone https://github.com/CodeMoss24/oryxos.git
cd oryxos
mvn clean package -DskipTests
```

## 2. Initialize

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar init
```

This creates the `.oryxos/` workspace structure in the current directory.

## 3. Configure Provider

Edit `.oryxos/config.yaml`:

```yaml
provider:
  name: openai
  api-key: sk-your-key-here
  model: gpt-4o
```

## 4. Start Chatting

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar chat
```

Type a question and the Agent will use the ReAct loop to automatically call built-in Tools.

## 5. Start Web Service

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar serve --port 8080
```

Then call via REST API:

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profile": "default"}'
```

## Next Steps

- [Core Concepts](/en/docs/concepts) — Understand ReAct, Memory, Tool design
- [Agent Development](/en/docs/agent-guide) — Create Agents with zero-code AGENT.md
