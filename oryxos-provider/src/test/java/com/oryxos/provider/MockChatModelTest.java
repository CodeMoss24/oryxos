package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * MockChatModel 单测(第 27 节):两轮脚本的行为保真——第一轮返回 save_memory 工具调用,第二轮(上下文里已有 tool 结果)返回最终答复;usage 固定
 * 10/20/30。
 */
class MockChatModelTest {

  private final MockChatModel model = new MockChatModel();

  @Test
  @DisplayName("第一轮(无tool结果):返回save_memory工具调用,arguments带抽出去前缀的事实")
  void firstRound_returnsSaveMemoryToolCall() {
    ChatResponse response = model.call(new Prompt(List.of(new UserMessage("记住:我住在上海"))));

    Generation generation = response.getResults().get(0);
    AssistantMessage output = generation.getOutput();

    assertThat(output.getContent()).isEmpty();
    assertThat(output.getToolCalls()).hasSize(1);
    AssistantMessage.ToolCall toolCall = output.getToolCalls().get(0);
    assertThat(toolCall.name()).isEqualTo("save_memory");
    assertThat(toolCall.type()).isEqualTo("function");
    assertThat(toolCall.arguments()).contains("\"content\":\"我住在上海\"");
    assertThat(toolCall.arguments()).contains("\"scope\":\"ARCHIVAL\"");
  }

  @Test
  @DisplayName("第二轮(tool结果已在上下文):直接返回最终答复,无工具调用")
  void secondRound_returnsFinalAnswer() {
    UserMessage user = new UserMessage("记住:我住在上海");
    ToolResponseMessage toolResult =
        new ToolResponseMessage(
            List.of(new ToolResponseMessage.ToolResponse("call_mock_1", null, "saved")));
    ChatResponse response = model.call(new Prompt(List.of(user, toolResult)));

    Generation generation = response.getResults().get(0);
    AssistantMessage output = generation.getOutput();

    assertThat(output.getContent()).isEqualTo("好的，已记住：我住在上海");
    assertThat(output.getToolCalls()).isEmpty();
  }

  @Test
  @DisplayName("无前缀的用户消息整条作为事实(全角冒号与半角冒号都被剥掉)")
  void factExtraction_stripsPrefixes() {
    ChatResponse response = model.call(new Prompt(List.of(new UserMessage("今天天气不错"))));

    assertThat(response.getResults().get(0).getOutput().getToolCalls().get(0).arguments())
        .contains("\"content\":\"今天天气不错\"");

    ChatResponse fullWidth = model.call(new Prompt(List.of(new UserMessage("记住：中午吃面"))));
    assertThat(fullWidth.getResults().get(0).getOutput().getToolCalls().get(0).arguments())
        .contains("\"content\":\"中午吃面\"");
  }

  @Test
  @DisplayName("usage固定10/20/30,model=mock")
  void response_hasFixedUsage() {
    ChatResponse response = model.call(new Prompt(List.of(new UserMessage("记住:x"))));

    assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(10L);
    assertThat(response.getMetadata().getUsage().getGenerationTokens()).isEqualTo(20L);
    assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(30L);
  }

  @Test
  @DisplayName("getDefaultOptions返回model=mock的OpenAiChatOptions")
  void defaultOptions_modelIsMock() {
    assertThat(model.getDefaultOptions().getModel()).isEqualTo("mock");
  }
}
