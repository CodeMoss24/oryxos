# 快速开始

5 分钟跑通你的第一个 OryxOS Agent。

## 前置条件

- JDK 21+
- Maven 3.8+
- 一个 LLM API Key（OpenAI 兼容协议均可）

## 1. 初始化工作区

```bash
git clone https://github.com/CodeMoss24/oryxos.git
cd oryxos
mvn clean package -DskipTests
```

## 2. 初始化

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar init
```

这会在当前目录创建 `.oryxos/` 工作区结构。

## 3. 配置 Provider

编辑 `.oryxos/config.yaml`：

```yaml
provider:
  name: openai
  api-key: sk-your-key-here
  model: gpt-4o
```

## 4. 开始对话

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar chat
```

输入问题，Agent 会通过 ReAct 循环自动调用内置 Tool 完成任务。

## 5. 启动 Web 服务

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar serve --port 8080
```

然后通过 REST API 调用：

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profile": "default"}'
```

## 下一步

- [核心概念](/docs/concepts) — 理解 ReAct、Memory、Tool 的设计
- [Agent 开发指南](/docs/agent-guide) — 用 AGENT.md 零代码创建 Agent
