package com.oryxos.core.react;

import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 的核心循环引擎。输入 Session 和用户消息,输出最终响应。
 * 内部维护当前迭代次数,调用 ProviderService 调 LLM,调用 ToolExecutor 执行 Tool。
 *
 * <p>核心算法(Reason + Act):
 * <pre>
 * 1. 接到用户消息追加到 Session 对话历史
 * 2. 组装 Prompt(system + Bootstrap + Skill + Memory + 对话历史 + 可用 Tool 列表)
 * 3. 调用 LLM Provider 获取响应
 * 4. 如果响应没有 Tool 调用 → 返回最终响应
 * 5. 如果有 Tool 调用 → OryxOS 执行 Tool 并把结果作为 tool 消息追加到对话历史
 * 6. 回到步骤 2 继续循环
 * 7. 达到最大迭代次数(默认 10 次)强制结束
 * </pre>
 *
 * <p>注意:不依赖 Spring AI 的 Agent 抽象,自实现;Spring AI 只用协议转换 + schema 生成,
 * 禁用其自动 tool 执行,否则 tool 会被调两次。
 */
@Component
public class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

    private final PromptBuilder promptBuilder;
    private final ProviderPort providerPort;
    private final ToolExecutor toolExecutor;
    private final MemoryService memoryService;

    public ReActLoop(PromptBuilder promptBuilder,
                     ProviderPort providerPort,
                     ToolExecutor toolExecutor,
                     MemoryService memoryService) {
        this.promptBuilder = promptBuilder;
        this.providerPort = providerPort;
        this.toolExecutor = toolExecutor;
        this.memoryService = memoryService;
    }

    public String run(Session session, String userMessage, Profile profile, String agentMdBody) {
        session.append(Message.user(userMessage));

        String systemPrompt = promptBuilder.buildSystemPrompt(profile, agentMdBody);
        String memoryBlock = promptBuilder.buildMemoryBlock(profile, session);
        String toolListBlock = promptBuilder.buildToolListBlock(profile);
        int maxIterations = profile.getSettings().getMaxIterations();

        for (int i = 0; i < maxIterations; i++) {
            LlmResponse response = providerPort.call(profile, systemPrompt, memoryBlock, toolListBlock,
                    promptBuilder.truncateHistory(session, profile.getSettings().getMaxHistoryTurns()));

            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                session.append(Message.assistant(response.content()));
                return response.content();
            }

            session.append(Message.assistant(response.content()));
            for (ToolCall call : response.toolCalls()) {
                String result = toolExecutor.execute(call, profile);
                session.append(Message.tool(result));
            }
        }

        log.warn("ReAct loop reached MAX_ITERATIONS={} for session {}", maxIterations, session.getSessionId());
        return "已达到最大迭代次数,Agent 终止。";
    }
}
