# 核心概念

## ReAct 循环

OryxOS 的核心引擎是自实现的 ReAct（Reason + Act）循环：

1. 接收用户消息，追加到 Session 对话历史
2. 组装 Prompt（system prompt + Memory + 对话历史 + Tool 列表）
3. 调用 LLM 获取响应
4. 如果响应包含 Tool 调用 → 执行 Tool → 结果追加到对话历史 → 回到步骤 2
5. 如果响应没有 Tool 调用 → 返回最终响应
6. 最大 10 轮迭代后强制结束

**关键设计**：ReAct 完全自实现，不依赖 Spring AI 的 Agent 抽象。Spring AI 只用 Provider 抽象 + 协议转换，Tool 调度由 `ReActLoop` + `ToolExecutor` 控制。

## Memory 三层架构

| 层 | 作用 | 特点 |
|----|------|------|
| 会话历史 | 当前对话上下文 | 按 `maxHistoryTurns` 截断 |
| 核心记忆 | 永久关键信息 | 全量注入 system prompt，永不断、不检索 |
| 归档记忆 | 历史知识 | 关键词检索 + 截断 |

三档后端靠配置切换：`markdown`（默认）/ `sqlite` / `mem0`，上层代码不动。

## Tool 体系

统一的 `OryxTool` 接口，内置 Tool、`@Tool` 插件、MCP Tool 都被包装成 `OryxTool` 注册到 `ToolRegistry`。

三档接入方式：

| 方式 | 门槛 | 推荐度 |
|------|------|--------|
| AGENT.md + 复用 MCP server | 零代码 | ⭐⭐⭐ |
| 自写 MCP server | 轻代码 | ⭐⭐ |
| Java `@Tool` Bean | 重代码 | ⭐ |

## Sandbox

三层安全边界：文件路径白名单、命令白名单、域名白名单。接口先行，未来升级容器隔离或 microVM 不改调用方。

## Agent 即目录

`.oryxos/agents/<name>/AGENT.md` = 一个 Agent。frontmatter 定义 Profile，正文定义任务指令。Agent 目录里的子指令/脚本不预载，按需读取。
