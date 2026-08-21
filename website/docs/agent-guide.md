# Agent 开发指南

OryxOS 的核心理念：**一个目录 = 一个 Agent**。用 `AGENT.md` 定义 Agent，而不是写代码。

## Agent 目录结构

```
.oryxos/agents/<name>/
├── AGENT.md          # 必须：Agent 定义（frontmatter = Profile，正文 = 任务指令）
├── skills/           # 可选：子指令
├── scripts/          # 可选：脚本
└── REFERENCE.md      # 可选：参考资料
```

## AGENT.md 格式

```markdown
---
provider: openai
model: gpt-4o
max_iterations: 10
schedules:
  - cron: "0 9 * * *"
    message: "执行每日天气查询并推送"
---

你是一个每日天气助手。每天早上查询指定城市的天气，
用简洁的中文总结天气情况，然后通过 notify 推送结果。
```

- **frontmatter**：Agent 自己的 Profile 配置，`AgentLoader.deriveProfile()` 自动派生
- **正文**：任务指令，注入 system prompt
- **工具与通知渠道不写在 frontmatter**：工具走全局 `ToolRegistry`（内置 + MCP 全量可用），notify 出口走管理台「通知渠道」全局注册表（`/api/v1/notify-channels` CRUD，`notify` 工具按渠道名解析，未指名用第一个渠道）

## 零代码 Agent 示例

创建一个天气 Agent，不需要写任何 Java 代码：

1. `oryxos profile create daily-weather`
2. 编辑 `.oryxos/agents/daily-weather/AGENT.md`
3. 定义意图 + 配置定时 + 配置通知渠道
4. ReAct + 内置 Tool 自动完成其余工作

## 关键原则

- **`AGENT.md` 不是可执行 Tool**：加载归 `oryxos-core` 的 `ContextLoader`，正文注入 system prompt，不进 `ToolRegistry`
- **子指令/脚本不预载**：Agent 正文指引经 `read_file` / `shell` 按需读取，渐进式披露
- **不另写 Profile YAML**：`.oryxos/profiles/` 已取消，Profile 由 AGENT.md frontmatter 派生
