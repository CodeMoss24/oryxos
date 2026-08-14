package com.oryxos.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 独立 mock provider(挂 "mock" 名下,第 27 节):无 key、不联网,按脚本驱动一次确定性的 ReAct。
 *
 * <p>脚本两轮:
 *
 * <ol>
 *   <li>第一轮(消息里没有 tool 结果):把用户消息里 "记住：…" 的事实抽出来,请求一次 {@code save_memory} 工具调用(真实写入
 *       MEMORY.md,给全链路一个可观测的"文件写入"行为);
 *   <li>第二轮(PromptBuilder 已把工具结果渲染成 tool 消息):直接返回最终答复。
 * </ol>
 *
 * 只有"模型"是假的——ReActLoop / ToolExecutor / Memory / Session / 审计全部走真实路径。
 */
public class MockChatModel implements ChatModel {

  private static final Logger log = LoggerFactory.getLogger(MockChatModel.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String TOOL_NAME = "save_memory";
  private static final String TOOL_CALL_ID = "call_mock_1";

  @Override
  public ChatResponse call(Prompt prompt) {
    boolean hasToolResult =
        prompt.getInstructions().stream().anyMatch(m -> m instanceof ToolResponseMessage);
    if (hasToolResult) {
      return finalAnswerResponse(prompt);
    }
    return toolCallResponse(prompt);
  }

  /** 第一轮:请求一次 save_memory,arguments 带从用户消息抽出来的事实。 */
  private ChatResponse toolCallResponse(Prompt prompt) {
    String fact = extractFact(prompt);
    String arguments;
    try {
      arguments = JSON.writeValueAsString(Map.of("content", fact, "scope", "ARCHIVAL"));
    } catch (Exception e) {
      throw new IllegalStateException("MockChatModel: failed to build save_memory arguments", e);
    }
    AssistantMessage.ToolCall toolCall =
        new AssistantMessage.ToolCall(TOOL_CALL_ID, "function", TOOL_NAME, arguments);
    AssistantMessage assistant = new AssistantMessage("", Map.of(), List.of(toolCall));
    return response(new Generation(assistant));
  }

  /** 第二轮:工具结果已在上下文中,直接返回最终答复(引用刚记住的事实,便于断言)。 */
  private ChatResponse finalAnswerResponse(Prompt prompt) {
    String fact = extractFact(prompt);
    String answer = "好的，已记住：" + fact;
    return response(new Generation(new AssistantMessage(answer)));
  }

  private ChatResponse response(Generation generation) {
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .withUsage(new DefaultUsage(10L, 20L, 30L))
            .withModel("mock")
            .build();
    return new ChatResponse(List.of(generation), metadata);
  }

  /** 取第一条用户消息,去掉 "记住：" 前缀(全/半角冒号均可),无前缀时整条作为事实。 */
  private String extractFact(Prompt prompt) {
    return prompt.getInstructions().stream()
        .filter(m -> m instanceof UserMessage)
        .map(m -> ((UserMessage) m).getContent())
        .findFirst()
        .orElse("")
        .replaceFirst("^记住[:：]\\s*", "");
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return OpenAiChatOptions.builder().withModel("mock").build();
  }
}
