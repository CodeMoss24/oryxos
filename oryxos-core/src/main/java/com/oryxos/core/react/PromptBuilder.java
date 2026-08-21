package com.oryxos.core.react;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 组装每轮 LLM 调用的 Prompt。按四部分顺序拼接: 1. system prompt(AGENT.md 正文 + Bootstrap,由 ContextLoader
 * 提供;末尾附当前日期时间) 2. Memory 注入(长期记忆,由 MemoryService 提供) 3. 对话历史(按 maxHistoryTurns 截断后的 Session
 * messages) 4. 全局可用的 Tool 列表(按 Function Calling 格式;31 节起不按 Agent 过滤)
 */
@Component
public class PromptBuilder {

  private final ContextLoader contextLoader;
  private final MemoryService memoryService;
  private final ToolRegistry toolRegistry;

  public PromptBuilder(
      ContextLoader contextLoader, MemoryService memoryService, ToolRegistry toolRegistry) {
    this.contextLoader = contextLoader;
    this.memoryService = memoryService;
    this.toolRegistry = toolRegistry;
  }

  public String buildSystemPrompt(Profile profile, String agentMdBody) {
    return contextLoader.loadSystemPrompt(profile, agentMdBody);
  }

  public String buildMemoryBlock(Session session) {
    return memoryService.buildContext(session);
  }

  public List<Message> truncateHistory(Session session, int maxTurns) {
    List<Message> messages = session.getMessages();
    if (messages.size() <= maxTurns) {
      return messages;
    }
    int from = messages.size() - maxTurns;
    // 截断边界不能落在 tool 消息上:OpenAI 兼容协议要求 role=tool 必须紧跟带 tool_calls 的 assistant 消息,
    // 硬截断切掉配对会 400("Messages with role 'tool' must be a response to a preceding message with
    // 'tool_calls'")。
    // 向前回退到最近一条非 tool 消息(即该轮 assistant 起点),宁多带两条也不切断配对。
    while (from > 0 && messages.get(from).role().equals("tool")) {
      from--;
    }
    return messages.subList(from, messages.size());
  }

  /** 31 节:Agent 不再内联 tools,可用工具 = 全局 ToolRegistry 全部注册项(内置 + MCP + notify)。 */
  public String buildToolListBlock() {
    List<OryxTool> tools = toolRegistry.list();
    if (tools.isEmpty()) {
      return "";
    }
    return tools.stream()
        .map(t -> "- " + t.getName() + ": " + t.getDescription())
        .collect(Collectors.joining("\n"));
  }

  /** 把 builder 产出的各组件拼成 ProviderPort.chat() 所需的 Message 列表。 */
  public List<Message> assembleMessages(
      String systemPrompt, String memoryBlock, String toolListBlock, List<Message> history) {
    List<Message> messages = new java.util.ArrayList<>();
    StringBuilder sb = new StringBuilder(systemPrompt);
    if (memoryBlock != null && !memoryBlock.isBlank()) {
      sb.append("\n\n").append(memoryBlock);
    }
    if (toolListBlock != null && !toolListBlock.isBlank()) {
      sb.append("\n\n可用工具:\n").append(toolListBlock);
    }
    messages.add(Message.system(sb.toString()));
    messages.addAll(history);
    return messages;
  }
}
