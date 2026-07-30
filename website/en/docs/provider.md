# LLM Provider

OryxOS uses Spring AI + Spring AI Alibaba for LLM Provider abstraction, supporting all OpenAI-compatible models.

## Configuration

Configure in `.oryxos/config.yaml`:

```yaml
provider:
  name: openai        # or dashscope, zhipu, etc.
  api-key: sk-xxx
  model: gpt-4o
  base-url: https://api.openai.com/v1  # optional, custom endpoint
```

## Provider Mapping

OryxOS maintains an explicit `provider name → ChatModel` mapping rather than type scanning. This avoids ambiguity when multiple Beans share the same type.

## Function Calling

Spring AI handles `@Tool` annotation schema generation but **does not auto-execute** Tools. Tool scheduling is fully controlled by `ReActLoop` + `ToolExecutor` — one of OryxOS's most important design decisions.
