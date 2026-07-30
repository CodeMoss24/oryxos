# OryxOS

![OryxOS Logo](docs/images/logo.svg)

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-LLM%20Provider-green.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Status](https://img.shields.io/badge/Status-Core%20Phase%20WIP-yellow.svg)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)

> **一个企业能完全掌控的、Java 原生的、私有可审计的 Agent 统一底座。**

OryxOS 装在企业自己的 K8s、服务器或物理机上,作为统一底座,在底座上跑各种业务 Agent(运维助手、客服助手、HR 助手、销售助手、知识管理助手等),共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业自己的基础设施,不锁任何云生态。

- 🏗️ **统一**:多个业务 Agent 共享同一套底座,上新 Agent 只需放一个 Agent 目录
- 🔒 **私有**:数据和部署完全在企业自己手里,OryxOS 不收集任何企业数据
- 🔌 **易接入**:基于标准 Spring Boot 工程结构,跟现有 ERP/CRM/CMDB/SSO/监控系统直接对接
- 📊 **可观测**:标准 Prometheus 指标、结构化 JSON 日志、健康检查接口

> **当前状态**:核心阶段(运行时内核)开发中。企业级治理层(多租户、SSO、完整审计、Tool Policy)放扩展阶段。核心阶段是地基,不是终局。

---

## 为什么需要 OryxOS

业界开源 Agent OS 格局目前由 **OpenClaw**(Node.js,偏个人)和 **Hermes Agent**(Python,偏小团队)代表,合起来留下了三个空白:

1. **完整的企业级治理**——多租户 RBAC、SSO、审计架构、合规留证
2. **企业 IT 系统的深度集成**——ERP/CRM/CMDB/监控系统的现成 connector
3. **Java 生态的缺位**——没有任何 Java 项目把 "Agent OS" 作为定位

严监管企业(银行、政府、电信、能源、医疗)的核心业务数据不能出企业、系统必须完全可审计、技术栈要跟现有体系对齐。OryxOS 锚定的就是这块**确定的、刚性的、当前无人满足的**需求——不管 "Agent OS" 这个词未来演变与否,"严监管企业要一个自己能完全掌控的 Agent 底座" 这件事都不会变。

完整论证见 [`docs/IndustryResearch.md`](docs/IndustryResearch.md)。

---

## 架构

![OryxOS Architecture](docs/images/architecture.svg)

OryxOS 是一个 Spring Boot 单体应用,对外有 **三个触发入口**:CLI(人推)、Web Service(人推)、`AgentScheduler` 定时任务(钟推)。三个入口的消息最终都汇入同一个 `AgentService`,由 **ReAct 循环** 驱动 **Provider / Memory / Tool** 三块能力。

**五大核心能力**:

| 能力 | 说明 |
|------|------|
| **对接 LLM** | Provider 抽象 + provider name → ChatModel 显式映射,Agent 不感知具体调哪家模型 |
| **ReAct 循环** | Agent 大脑,自实现约数十行 Java,LLM 思考 + 工具执行,多步骤任务自主完成 |
| **Memory 三层记忆** | `MemoryService` 统一门面,核心阶段会话 + 长期(`MEMORY.md`),跨对话记住偏好 |
| **Plugin Tool + 内置工具集** | 9 个内置 Tool + Plugin 三档(零代码 `AGENT.md`+MCP / 轻代码自写 MCP / 重代码 `@Tool` Bean) |
| **Web Service** | REST API 暴露所有能力,业务系统集成的唯一通道 |

详见 [`docs/TechnicalSolution.md`](docs/TechnicalSolution.md)。

---

## 快速开始

### 环境要求

- **JDK 21+**
- **Maven 3.9+**
- **Linux 主流发行版**(Ubuntu 22.04+ / CentOS 8+ / Debian 11+ / Alibaba Cloud Linux 3 / Rocky Linux)
- 至少一个 LLM Provider 的 API key(推荐 DeepSeek 或 Kimi)

### 安装与构建

```bash
git clone <repo-url> oryxos
cd oryxos
mvn clean package
```

生成 fat JAR,`java -jar oryxos-boot/target/oryxos-boot-*.jar` 启动。

### 初始化工作区

```bash
oryxos init
```

在当前目录下创建 `.oryxos/` 工作区:

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent(AGENT.md + 可选 skills/ scripts/ REFERENCE.md)
├── skills/            # 全局 Skill 库,Agent 按名引用
├── output/            # Agent 产出物
├── memory/
│   └── MEMORY.md      # 长期记忆(## 核心记忆 / ## 归档记忆)
├── sessions/          # 会话历史
├── logs/              # 结构化日志
├── mcp_servers.yaml   # MCP 配置
├── AGENTS.md          # Bootstrap:项目级 agent 行为说明
├── SOUL.md            # Bootstrap:默认 agent 人格定义
├── USER.md            # Bootstrap:用户偏好
└── oryxos.db          # SQLite
```

### 配置 Provider

编辑 `application.yaml`,通过环境变量注入 API key(不明文写死):

```yaml
oryxos:
  providers:
    - name: deepseek
      model: deepseek-chat
      api-key: ${DEEPSEEK_API_KEY}
```

```bash
export DEEPSEEK_API_KEY=sk-xxxxx
```

### 定义一个 Agent

一个目录 = 一个 Agent。`oryxos profile create daily-weather` 生成最小模板,编辑 `.oryxos/agents/daily-weather/AGENT.md`:

```yaml
---
name: daily-weather
description: 每天早上查天气并推送穿搭建议
identity:
  agent_name: DailyWeather
  prompt: 你是一个穿搭顾问助手
provider:
  name: deepseek
  model: deepseek-chat
tools: [http_get, notify]
notify_channels:
  - type: webhook
    url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
schedules:
  - cron: "0 0 8 * * ?"
    zone: Asia/Shanghai
    message: 查北京今天的天气并给我穿搭建议,推送出来
---

查天气,生成穿搭建议,通过 notify 推送。
```

### 三种运行模式

```bash
# 交互对话(开发调试)
oryxos chat --profile daily-weather

# 启动 REST API 服务(默认 8080)
oryxos serve

# 多渠道守护进程(同时挂多个 Channel)
oryxos gateway
```

### 业务系统集成示例

```bash
# 创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profile_name":"daily-weather","user_id":"alice"}'

# 发消息
curl -X POST http://localhost:8080/api/v1/sessions/{id}/messages \
  -H "Content-Type: application/json" \
  -d '{"content":"今天北京天气如何?"}'

# 查历史
curl http://localhost:8080/api/v1/sessions/{id}
```

---

## 模块说明

OryxOS 是 Maven 多模块项目,由 9 个模块组成:

| 模块 | 职责 |
|------|------|
| [`oryxos-core`](oryxos-core) | 核心抽象:`OryxTool` 接口、`Session`、`Profile`、`ContextLoader`、`AgentLoader`(扫 `.oryxos/agents/`、`deriveProfile`)、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| [`oryxos-provider`](oryxos-provider) | `ProviderService`、Function Calling 适配、provider name → `ChatModel` 显式映射 |
| [`oryxos-memory`](oryxos-memory) | `MemoryService` 统一门面、`LongTermMemory`(三档后端:Markdown/SQLite/Mem0)、`MemoryTools` |
| [`oryxos-tool`](oryxos-tool) | 内置 Tool(`FileTools`/`ShellTools`/`HttpTools`/`MemoryTools`/`NotifyTools`)、`McpClientService`、`McpToolAdapter`、`ToolRegistry`、`Sandbox` + `WhitelistSandbox`、`NotifyChannelAdapter` + `WebhookNotifyAdapter` |
| [`oryxos-channel-cli`](oryxos-channel-cli) | `CliChannel`、`oryxos chat` 实现 |
| [`oryxos-web`](oryxos-web) | `WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI 文档 |
| [`oryxos-storage`](oryxos-storage) | SQLite 持久化层、各 Repository |
| [`oryxos-cli`](oryxos-cli) | Picocli 主入口、12 个子命令、`ConfigLoader` |
| [`oryxos-boot`](oryxos-boot) | Spring Boot 启动模块、主类、自动配置、依赖聚合 |

模块之间通过接口解耦。扩展阶段加新 Channel 或新 Tool 实现只加新模块不改 core。

---

## 定义一个 Agent:三种丰富度

OryxOS 借 Anthropic Agent Skills 的目录形态,但在 OryxOS **一个目录 = 一个 Agent**。`AGENT.md` 的 frontmatter 派生 Profile,正文注入 system prompt,目录里的子指令/脚本/参考按需经 `read_file`/`shell` 取用(渐进式披露)。

| 形态 | 示例 | 适合场景 |
|------|------|---------|
| 光杆 `AGENT.md` | 每日天气 | 简单任务,只用内置 Tool |
| `AGENT.md` + `skills/` 子指令 | 每日科技日报 | 较长的组稿规范、产出格式约束 |
| `AGENT.md` + `scripts/` 脚本 | 每日 GitHub 日报 | 需要确定性数据,Agent 跑脚本拿 JSON |

业务方全程不写 Java 代码,只写 markdown 目录 + 复用社区 MCP server,就能上线一个新场景。

---

## 路线图

### 核心阶段(进行中)

4 周 × 3 小时 = 12 小时,按 user story 依赖推进:

```
US-1 (Provider) → US-2 (ReAct) → ┌─ US-3 (Memory) ─┐ → US-5 (Web Service)
                                  └─ US-4 (Tool)    ─┘
```

第四周末跑通三个验收 Demo:每日天气 / 每日科技日报 / 每日 GitHub 日报。

### 扩展阶段

- 多 Channel 接入(企业微信、飞书、钉钉、Slack、邮件)
- Provider Fallback 与 Adaptive Routing
- Memory 自动抽取 + 语义检索(向量库)
- Tool Policy + 完整 Sandbox(容器/microVM)
- Web 仪表板、SSO、多租户、完整审计
- 集群化部署与高可用(Nacos / ETCD)

### 社区共建

Skills Marketplace、SDK 多语言支持(Java → Python → TypeScript → Go)、可视化 Profile 编辑器、Native 文件生成、Kubernetes Operator、移动端管理台、Voice Channel、RISC-V/边缘部署。

详见 [`docs/DemandAnalysis.md`](docs/DemandAnalysis.md) 第 6、7 章。

---

## 文档

| 文档 | 内容 |
|------|------|
| [`docs/IndustryResearch.md`](docs/IndustryResearch.md) | 业界格局、Java 生态缺位、OryxOS 定位 |
| [`docs/DemandAnalysis.md`](docs/DemandAnalysis.md) | 需求文档(What) |
| [`docs/TechnicalSolution.md`](docs/TechnicalSolution.md) | 技术方案(How,权威) |
| [`docs/AiProgrammingGuide.md`](docs/AiProgrammingGuide.md) | AI 编程实施指引(Spec-Kit + 手动提示词) |
| [`CLAUDE.md`](CLAUDE.md) | AI agent 工作指引(constitution + 模块 + 陷阱) |

---

## 贡献

欢迎通过 PR 贡献代码。请先阅读:

- [`CLAUDE.md`](CLAUDE.md) — 不可违背原则与常见陷阱
- [`docs/AiProgrammingGuide.md`](docs/AiProgrammingGuide.md) — 主体开发用 Spec-Kit,增量开发用 Claude Code

**核心阶段**优先推进五大核心能力;**扩展阶段**的治理层、企业 IT 系统 connector、多 Channel 等开放给社区共建。

提交前请确保:
- 遵守 JDK 21 + Spring Boot 3.x 技术栈
- 不启用 Spring AI 的自动 tool 执行(详见 constitution 原则四)
- 审计表(`tool_invocations` / `llm_calls`)写入 SQLite,不只放日志
- 每个 user story 完成后有可演示 Demo

---

## License

[MIT](LICENSE)
