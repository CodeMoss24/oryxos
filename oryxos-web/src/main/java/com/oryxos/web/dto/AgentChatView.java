package com.oryxos.web.dto;

import java.util.List;

/**
 * Agent 会话聚合视图(第 30 节 5.2.2 的会话 tab 增强):管理台固定会话(admin:console)优先, 再按 lastActiveAt 倒序并入该 Agent
 * 最近其他会话(invoke 手动 / scheduler 定时)的消息——整块拼接不切碎一轮对话, 总消息 ≤100 条。
 *
 * <p>前端 chatTurns 按 user/assistant/tool 分组渲染不感知会话边界;source 字段让每条消息可溯源。
 */
public record AgentChatView(
    String sessionId, String profileName, List<ChatMessageView> messages, List<String> sessions) {}
