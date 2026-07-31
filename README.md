# OryxOS

![OryxOS Logo](docs/images/logo.svg)

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Status](https://img.shields.io/badge/Status-Core%20Phase%20WIP-yellow.svg)

> **企业私有部署的 Agent 操作系统：你用一句自然语言发布一个任务 → 底座把它拆解 → 组织一支 Agent 团队 → 多个 Agent 分工协作 → 交付一个结果。** 让每一家公司，都能用自然语言跑起来自己的 Agent。

OryxOS 是开源的 **Agent Harness OS**（Agent 运行骨架）——套在模型外面、把模型变成能干活的 Agent 的那层脚手架。**北极星公式：自然语言(md) + Memory + Tool + MCP(Connector) + Skill + 知识库 + Notify = 一个 Agent。** 一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。覆盖模型接入、推理循环、记忆、工具调用、对外服务五大核心能力。

---

## 为什么需要 OryxOS

每家公司都有该交给 Agent 的活，但 Agent 大多还停在 demo，卡在四道门槛上：

1. **定义一个 Agent 要写代码** — 最懂业务的人反而做不了
2. **云平台要把数据拿走** — 合规过不去
3. **执行是黑盒** — 没审计、没白名单、没人审批，企业不敢上生产
4. **跑一个容易、跑一群难** — 没有人把「一群 Agent 的操作系统」这一层交给你

OryxOS 一次拆掉这四道门槛：自然语言定义、私有部署、全链路审计加沙箱、以及为一整队 Agent 准备的生命周期与治理。

更深一层的判断是：**让 Agent 在生产环境可靠工作，瓶颈通常不在模型本身，而在 Agent 的运行环境。** OryxOS 做的不是又一个 Agent，而是这个让一群 Agent 可靠运行和协同的底座本身。

---

## Agent Harness OS

Agent runtime 是让单个 Agent 跑起来的执行内核，负责调用模型、执行工具、管理上下文、控制推理循环。Agent Harness OS 在 runtime 之上，管理的是一群 Agent：多个 Agent 的生命周期、统一的对外渠道与对内接入、统一的记忆、多租户与治理，以及分布式形态下的跨节点协作。

**runtime 让一个 Agent 跑起来，Agent Harness OS 让一群 Agent 被运行和管理起来。** OryxOS 是后者。

---

## 架构

![OryxOS Architecture](docs/images/architecture.svg)

OryxOS 是 Spring Boot 单体应用，三个触发入口（CLI / Web Service / AgentScheduler）汇入同一个 `AgentService`，由 **ReAct 循环** 驱动五大核心能力。

| 能力 | 说明 |
|------|------|
| **对接 LLM** | Provider 抽象 + 显式映射，Agent 不感知具体厂商，运行时切换不锁定 |
| **ReAct 循环** | Agent 大脑，自实现约数十行 Java，虚拟线程高并发，机制完全可控 |
| **Memory 记忆** | MemoryService 统一门面，核心区全量注入 + 归档区关键词检索，三档后端可切换 |
| **Tool 工具** | 9 个内置 Tool + Plugin 三档接入（零代码 MCP / 自写 MCP / @Tool Java Bean） |
| **Web Service** | 10 个 REST 端点，所有能力通过 HTTP API 暴露，任何语言可集成 |

---

## 快速开始

### 环境要求

- **JDK 21+**
- **Maven 3.9+**
- Linux 主流发行版（Ubuntu 22.04+ / CentOS 8+ / Debian 11+）

### 安装

```bash
git clone git@github.com:CodeMoss24/oryxos.git
cd oryxos
mvn clean package
```

### 初始化

```bash
oryxos init
```

创建 `.oryxos/` 工作区：

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent（AGENT.md + 可选 skills/scripts/）
├── skills/            # 全局 Skill 库
├── output/            # Agent 产出物
├── memory/
│   └── MEMORY.md      # 长期记忆（## 核心记忆 / ## 归档记忆）
├── sessions/          # 会话历史
├── logs/              # 结构化日志
├── mcp_servers.yaml   # MCP 配置
├── AGENTS.md          # Bootstrap：项目级 agent 行为说明
├── SOUL.md            # Bootstrap：默认 agent 人格
├── USER.md            # Bootstrap：用户偏好
└── oryxos.db          # SQLite
```

### 定义第一个 Agent

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
    message: 查北京今天的天气并给我穿搭建议，推送出来
---

查天气，生成穿搭建议，通过 notify 推送。
```

### 三种运行模式

```bash
oryxos chat --profile daily-weather   # 交互对话（开发调试）
oryxos serve                           # 启动 REST API（默认 8080）
oryxos gateway                         # 多渠道守护进程
```

---

## 模块结构

OryxOS 是 Maven 多模块项目，9 个模块：

| 模块 | 职责 |
|------|------|
| `oryxos-core` | 核心抽象：`OryxTool`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| `oryxos-provider` | ProviderService、Function Calling 适配、显式映射 |
| `oryxos-memory` | MemoryService 统一门面、三档后端（Markdown/SQLite/Mem0）、MemoryTools |
| `oryxos-tool` | 内置 Tool、MCP Client、ToolRegistry、Sandbox、NotifyChannelAdapter |
| `oryxos-channel-cli` | CliChannel、`oryxos chat` |
| `oryxos-web` | WebServer、6 个 ApiController、GlobalExceptionHandler |
| `oryxos-storage` | SQLite 持久化、各 Repository |
| `oryxos-cli` | Picocli 主入口、12 个子命令 |
| `oryxos-boot` | Spring Boot 启动模块 |

---

## 设计原则

- **底座优先于 Agent** — 最重要的交付不是某个强大的 Agent，而是让任意 Agent 都能可靠运行的环境
- **自实现核心，可控优先** — 核心推理循环自己实现，底层模型协议适配复用成熟库，不重复造轮子
- **配置即 Agent** — 一个 Agent 由一份配置定义，而不是由代码写出
- **对接开放标准** — 工具用 MCP、协作用 A2A、技能用开放格式，与生态协同
- **无状态实例，状态外置** — 从单机平滑走向分布式的前提
- **安全是地基不是补丁** — 工具来源受控、最小权限、强制沙箱、凭证不落地、全链路可审计，安全从第一天就在架构里
- **分阶段克制** — 当前只做运行时内核的最小完备集，治理与重型分布式基础设施留到后续，每次架构升级都用真实使用数据证明其必要性

---

## 路线图

我们的开发理念是：**慢就是快，克制且聚焦。** 先把单机的运行时内核做扎实，让一个节点上运行和管理一群 Agent 这件事真正可用、有人用，再在它之上逐步生长出分布式能力。

- **阶段一（当前）单机运行时内核**
  - 五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API 接入、对接 MCP
  - 把单节点运行和管理一群 Agent 做到可用
- **阶段二（规划）底座分布式**
  - 节点无状态化、状态外置、多副本部署
  - 支撑更大规模与高可用
- **阶段三（愿景）跨节点 Agent 协作**
  - 引入 Agent 通信底座，对接 A2A
  - 让多节点上的 Agent 跨节点发现、委托、可靠异步协同
- **横向能力（伴随各阶段逐步补齐）**
  - 多租户、SSO、完整审计、工具策略、可观测、Web 管理

---

## 文档

| 文档 | 内容 |
|------|------|
| [`docs/oryxos.md`](docs/oryxos.md) | 项目总览 |
| [`docs/IndustryResearch.md`](docs/IndustryResearch.md) | 业界格局与定位 |
| [`docs/DemandAnalysis.md`](docs/DemandAnalysis.md) | 需求文档 |
| [`docs/TechnicalSolution.md`](docs/TechnicalSolution.md) | 技术方案（权威） |
| [`docs/AiProgrammingGuide.md`](docs/AiProgrammingGuide.md) | AI 编程实施指引 |
| [`CLAUDE.md`](CLAUDE.md) | AI Agent 工作指引 |

---

## 项目信息

- **语言**：Java（JDK 21）
- **协议**：Apache 2.0
- **生态**：oryx-labs
- **长期目标**：走进 Apache 基金会，努力成为 Apache 顶级项目

---

## License

[Apache 2.0](LICENSE)