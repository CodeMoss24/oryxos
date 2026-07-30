# ReAct Loop

ReAct（Reason + Act）是 OryxOS 最核心的一段代码。自实现，约数十行 Java，不依赖 Spring AI Agent 抽象。

## 算法流程

```
1. 接到用户消息追加到 Session 对话历史
2. 组装 Prompt（system prompt + Bootstrap + Skill + Memory + 对话历史 + 可用 Tool 列表）
3. 调用 LLM Provider 获取响应
4. 如果响应没有 Tool 调用 → 返回最终响应
5. 如果有 Tool 调用 → 执行 Tool 并把结果追加到对话历史
6. 回到步骤 2 继续循环
7. 达到最大迭代次数（默认 10 次）强制结束
```

## Prompt 组装顺序

1. **System Prompt**：`AGENT.md` 正文 + Bootstrap（末尾附当前日期时间）
2. **Memory 注入**：会话历史 + 长期记忆
3. **对话历史**：按 `maxHistoryTurns` 截断
4. **可用 Tool 列表**

## 三种触发源

- **CLI**（`oryxos chat`）— 人推
- **Web Service**（`POST /agents/{name}/invoke`）— 人推
- **AgentScheduler**（cron 定时触发）— 钟推

三者都调用 `AgentService.process`，ReActLoop 不感知触发来源。

## 上下文长度管理

核心阶段策略：保留 system prompt 和最近 N 轮对话（N 由 Profile 配置，默认 20 轮），超出部分丢弃。
