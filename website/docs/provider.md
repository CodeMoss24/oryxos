# LLM Provider

OryxOS 通过 Spring AI + Spring AI Alibaba 实现 LLM Provider 抽象，支持所有 OpenAI 兼容协议的模型。

## 配置

在 `.oryxos/config.yaml` 中配置：

```yaml
provider:
  name: openai        # 或 dashscope、zhipu 等
  api-key: sk-xxx
  model: gpt-4o
  base-url: https://api.openai.com/v1  # 可选，自定义端点
```

## Provider 映射

OryxOS 维护 `provider name → ChatModel` 的显式映射，而非通过类型扫描。这避免了多个 Bean 类型相同时的歧义问题。

## Function Calling 适配

Spring AI 负责 `@Tool` 注解的 schema 生成，但 **不负责自动执行**。Tool 调度完全由 `ReActLoop` + `ToolExecutor` 控制——这是 OryxOS 最重要的设计决策之一。
