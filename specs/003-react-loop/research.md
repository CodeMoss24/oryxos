# 研究结论：ReAct 循环引擎

**日期**: 2026-08-08 | **关联计划**: [plan.md](./plan.md)

## 决策 1: ToolExecutor 签名对齐课件

**决策**: `ToolExecutor.execute()` 增加 `sessionId` 参数，签名为 `execute(String sessionId, ToolCall call, Profile profile)`。sessionId 用于 tool_invocations 审计表关联，Profile 用于 Sandbox 检查（24 节接线）。

**理由**: 课件骨架明确写 `toolExecutor.execute(session.id(), call)`，审计表 `tool_invocations` 有 `session_id` 列必须写入。Profile 参数保留给 Sandbox 检查（取 profile 的 tools 过滤/沙箱策略）。

**替代方案**: 仅传 sessionId，从 ProfileContext 取 Profile。不采纳——显式传参更清晰，且 ToolExecutor 可能被非 AgentService 路径调用（如测试），此时 ProfileContext 可能为空。

## 决策 2: Session 持久化方案

**决策**: `AgentService` 注入 `SessionRepository`（已有），在 `ReActLoop.run()` 返回后将 `Session` 转换为 `SessionEntity` 并 `save()`。转换逻辑：`messagesJson` = JSON 序列化 `session.getMessages()` 列表。

**理由**: `SessionRepository` 和 `SessionEntity` 已在 oryxos-storage 中定义。18 节 CLI 会补充完整的 SessionManager 接口和转换逻辑，本节只需最小可行实现确保 Session 被持久化——"process 结束后 Session 持久化"是课件验收标准之一。折中方案：本节在 AgentService 中直接注入 SessionRepository 做持久化（不引入 SessionManager 抽象），18 节重构为 SessionManager 时再提取。

**替代方案**: 
- 立即创建 SessionManager 接口——增加本节交付物范围，18 节的 SessionManager 设计尚未确定。
- 先不做持久化——验收测试"结束后 Session 被持久化"无法通过。

## 决策 3: ContextLoader 日志级别

**决策**: Bootstrap 文件缺失打 `WARN`（如 `AGENTS.md`、`SOUL.md`、`USER.md` 中任一文件不存在），Skill 引用文件缺失打 `ERROR`。

**理由**: 课件明确："Profile 里显式引用的文件缺失要报错、Bootstrap 缺失至少 WARN"。

**替代方案**: 全部 ERROR——但 Bootstrap 三件套不应阻断启动（用户可能只配了部分）。

## 决策 4: ToolExecutor 异常处理策略

**决策**: `OryxTool.execute()` 抛出的 `RuntimeException` 在 ToolExecutor 中被 catch → 写审计（success=false, error_message=异常消息）→ 返回错误字符串 `"Tool error: <message>"`，不重新上抛。

**理由**: ReAct 循环不应因为一个 Tool 执行失败而中断整个 Agent 处理。将错误信息以文本形式返回给 LLM，让模型自己决定如何处理（重试、换策略、或告知用户）。这与课件骨架一致——循环不感知工具执行成败，只把结果文本追加到对话历史。

**替代方案**: 工具失败直接抛异常中断循环——这会让 Agent 过于脆弱，与 "Agent 应该能应对工具失败" 的设计意图不符。

## 决策 5: PromptBuilder 四部分组装验证

**决策**: 当前 `assembleMessages()` 的实现已正确：system prompt（含 memory 注入和 tool 列表）→ history 消息。但顺序与课件描述不完全一致——课件说"长期记忆"和"对话历史"是独立的第二部分和第三部分，当前实现把 memory 和 tool 列表都拼进了 system message 正文。保持当前实现，因为最终发给 LLM 的效果一致（memory 和 tool 列表都是 system prompt 的一部分），且 Prompt 类的 messages 列表结构 `[system, user, assistant, tool, ...]` 不区分"memory"和"system"。

**理由**: 课件 PromptBuilder 说四部分顺序但 Spring AI 的 Message 结构是 role-based，不是 section-based。将四部分内容按职责合并进 system message 是工程上合理的做法，LLM 解读效果等价。

## 确认的依赖项

| 依赖 | 来源 | 状态 |
|------|------|------|
| ProviderPort.chat() | oryxos-core (16 节) | ✅ 已有 |
| Profile / ProfileRegistry | oryxos-core (16 节) | ✅ 已有 |
| ProfileContext | oryxos-core (16 节) | ✅ 已有 |
| Session / Message | oryxos-core (16 节) | ✅ 已有 |
| ToolRegistry | oryxos-core (16 节) | ✅ 已有 |
| ToolInvocationEntity | oryxos-storage (预创建) | ✅ 已有 |
| ToolInvocationRepository | oryxos-storage (预创建) | ✅ 已有 |
| SessionEntity / SessionRepository | oryxos-storage (预创建) | ✅ 已有 |
| MemoryService | oryxos-core (16 节) | ✅ 已有接口 |
| ContextLoader | oryxos-core (16 节) | ✅ 已有 |