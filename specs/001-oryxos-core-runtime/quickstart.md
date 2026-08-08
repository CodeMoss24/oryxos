# Quickstart: OryxOS Core Runtime

快速验证指南。从下载到跑通三个验收 Demo 的端到端操作步骤。

---

## Prerequisites

- JDK 21+
- Maven 3.9+
- LLM API key（OpenAI 兼容协议，如 DeepSeek / Kimi / Qwen）

## 1. 编译

```bash
mvn clean package -DskipTests
```

**预期输出**: `BUILD SUCCESS`，在 `oryxos-boot/target/` 生成 fat JAR。

## 2. 初始化工作区

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init
```

**预期产出**: 创建 `.oryxos/` 目录结构：
```
.oryxos/
├── agents/
├── memory/
│   └── MEMORY.md
├── mcp_servers.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
└── oryxos.db
```

## 3. 验证 US-1：系统状态

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar status
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar provider list
```

**预期**: 显示系统配置和已注册的 Provider 列表。

## 4. 验证 US-2：CLI 多轮对话

```bash
# 启动交互式对话，指定一个可调用 HTTP Tool 的 Agent
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat --profile weather-agent
```

**输入**:
```
> 今天北京天气怎么样？
```

**预期**: Agent 通过 `http_get` 调用天气 API，返回天气信息。

**继续对话**:
```
> 那明天呢？
```

**预期**: Agent 基于对话上下文回答，至少 3 轮连续对话。

## 5. 验证 US-3：长期记忆

```bash
oryxos chat --profile memory-test-agent
```

**输入**:
```
> 记住，我关注 AI 和芯片方向。
> 今天有什么科技新闻？
```

**预期**: Agent 在回答中自然侧重 AI 和芯片内容。

**退出并重新启动**:
```bash
oryxos chat --profile memory-test-agent
> 我之前关注什么方向？
```

**预期**: Agent 从长期记忆中提取偏好并回答。

## 6. 验证 US-4：REST API

### 启动 Web Service

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve
```

### 创建会话并发送消息

```bash
# 创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profile_name": "weather-agent", "channel": "web", "user_id": "test-user"}'

# 发送消息
curl -X POST http://localhost:8080/api/v1/sessions/{session_id}/messages \
  -H "Content-Type: application/json" \
  -d '{"content": "今天北京天气怎么样？"}'
```

### 无状态调用

```bash
curl -X POST http://localhost:8080/api/v1/agents/weather-agent/invoke \
  -H "Content-Type: application/json" \
  -d '{"content": "今天北京天气怎么样？", "user_id": "test-user"}'
```

### 查询

```bash
curl http://localhost:8080/api/v1/profiles
curl http://localhost:8080/api/v1/tools
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/info
```

## 7. 验证 US-5：定时任务 (Demo 一：每日天气)

### 配置 Agent

创建 `.oryxos/agents/weather-agent/AGENT.md`：

```yaml
---
name: weather-agent
description: 每天早上 8 点推送天气穿搭建议
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
notify_channels:
  - type: webhook
    url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
schedules:
  - cron: "0 8 * * *"
    zone: Asia/Shanghai
    message: 查询今天天气并生成穿搭建议
---
你是一个天气助手。每天早上查询当天天气，根据温度给出穿搭建议，然后推送到通知渠道。
```

### 启动守护进程

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve
```

### 手动补跑验证

```bash
curl -X POST http://localhost:8080/api/v1/agents/weather-agent/invoke \
  -H "Content-Type: application/json" \
  -d '{"content": "查询今天天气并生成穿搭建议", "user_id": "scheduler"}'
```

### 查看执行记录

```bash
curl http://localhost:8080/api/v1/sessions/{session_id}
```

验证 `tool_invocations` 和 `llm_calls` 表中已有审计记录。

## 8. 验收 Demo 清单

| Demo | 命令/操作 | 验证点 |
|------|----------|--------|
| **每日天气** | `oryxos chat --profile weather-agent` + `curl` invoke | LLM + ReAct + HTTP Tool + Notify + 定时 |
| **每日科技日报** | 配置 `daily-tech-digest` Agent + invoke | Memory + MCP + read_file 子指令 |
| **每日 GitHub 日报** | 配置 `github-daily` Agent + invoke | shell 跑脚本 + 沙箱信任边界 |