package com.oryxos.core.react;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 组装每轮 LLM 调用的 Prompt。按四部分顺序拼接:
 * 1. system prompt(AGENT.md 正文 + Bootstrap,由 ContextLoader 提供;末尾附当前日期时间)
 * 2. Memory 注入(会话历史 + 长期记忆,由 MemoryService 提供)
 * 3. 对话历史(按 maxHistoryTurns 截断后的 Session messages)
 * 4. 当前 Profile 可用的 Tool 列表(按 Function Calling 格式)
 */
@Component
public class PromptBuilder {

    private final ContextLoader contextLoader;
    private final MemoryService memoryService;
    private final ToolRegistry toolRegistry;

    public PromptBuilder(ContextLoader contextLoader,
                         MemoryService memoryService,
                         ToolRegistry toolRegistry) {
        this.contextLoader = contextLoader;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
    }

    public String buildSystemPrompt(Profile profile, String agentMdBody) {
        return contextLoader.loadSystemPrompt(profile, agentMdBody);
    }

    public String buildMemoryBlock(Profile profile, Session session) {
        return memoryService.loadContext(profile, session);
    }

    public List<Message> truncateHistory(Session session, int maxTurns) {
        List<Message> messages = session.getMessages();
        if (messages.size() <= maxTurns) {
            return messages;
        }
        return messages.subList(messages.size() - maxTurns, messages.size());
    }

    public String buildToolListBlock(Profile profile) {
        if (profile.getTools() == null || profile.getTools().isEmpty()) {
            return "";
        }
        List<OryxTool> tools = toolRegistry.subset(profile.getTools());
        return tools.stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
