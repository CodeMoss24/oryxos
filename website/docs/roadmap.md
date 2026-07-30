# 路线图

我们的开发理念：**慢就是快，克制且聚焦**。先把单机的运行时内核做扎实，再逐步生长分布式能力。

## 阶段一（当前）：单机运行时内核

- 五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API 接入、对接 MCP
- 把单节点运行和管理一群 Agent 做到可用

### 四周节奏

| 周次 | 能力主线 | 可演示成果 |
|------|---------|-----------|
| 第一周 | 对接 LLM + ReAct 循环 | `oryxos chat` 多轮对话，Agent 通过 ReAct 调 HTTP Tool 完成天气查询 |
| 第二周 | Memory + Tool 体系 | Agent 记住用户偏好并后续对话用到，能调本地文件和外部 MCP server |
| 第三周 | Web Service | 外部系统通过 10 个 REST 端点完整调用 OryxOS |
| 第四周 | 多 Agent 演示 + 工程化收尾 | 多 Agent 并存，CLI 完整，Session 跨重启恢复，定时任务到点自动触发，主页可访问 |

### 三个验收 Demo

| Demo | Agent 形态 | 验证能力 |
|------|-----------|---------|
| 每日天气 | 光杆 AGENT.md | LLM + ReAct + 内置 HTTP Tool + NotifyTools + 定时 |
| 每日科技日报 | AGENT.md + skills/ 子指令 | Memory + MCP 方式二 + `read_file` 按需加载 |
| 每日 GitHub 日报 | AGENT.md + scripts/ 脚本 | `shell` 跑脚本 + 沙箱信任边界 |

## 阶段二（规划）：底座分布式

- 节点无状态化、状态外置、多副本部署
- 支撑更大规模与高可用

## 阶段三（愿景）：跨节点 Agent 协作

- 引入 Agent 通信底座，对接 A2A
- 让多节点上的 Agent 跨节点发现、委托、可靠异步协同

## 横向能力（伴随各阶段逐步补齐）

- 多租户、SSO、完整审计、工具策略、可观测、Web 管理
