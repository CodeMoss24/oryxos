package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.oryxos.core.exception.ProviderNotFoundException;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.react.Prompt;
import com.oryxos.core.session.Message;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.function.FunctionCallingOptions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderService — 路由、审计、工具 schema 验收")
class ProviderServiceTest {

  @Mock private ChatModel deepseekModel;

  @Mock private ChatModel kimiModel;

  @Mock private ChatResponse chatResponse;

  @Mock private Generation generation;

  @Mock private AssistantMessage assistantMessage;

  @Mock private ChatResponseMetadata metadata;

  @Mock private Usage usage;

  @Mock private LlmCallRepository auditRepository;

  @Mock private OryxTool httpGetTool;

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  private static Profile profileUsing(String providerName) {
    Profile profile = new Profile();
    profile.setName("test");
    profile.setProvider(new Profile.Provider(providerName, "test-model", 0.7));
    return profile;
  }

  private static Prompt promptWithTools(OryxTool... tools) {
    return new Prompt(List.of(Message.user("test")), List.of(tools));
  }

  private static Prompt simplePrompt() {
    return new Prompt(List.of(Message.user("test")));
  }

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routeByNameTwoProvidersNoCrossTalk() {
    when(kimiModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(assistantMessage.getContent()).thenReturn("ok from kimi");
    when(assistantMessage.getToolCalls()).thenReturn(null);

    var service =
        new ProviderService(
            Map.of("deepseek", deepseekModel, "kimi", kimiModel), adapter, auditRepository);

    service.chat("s-1", profileUsing("kimi"), simplePrompt());

    verify(kimiModel, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    verify(deepseekModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  @DisplayName("未知 provider 名抛异常")
  void unknownProviderThrowsException() {
    var service = new ProviderService(Map.of("deepseek", deepseekModel), adapter, auditRepository);

    assertThatThrownBy(() -> service.chat("s-1", profileUsing("kimi"), simplePrompt()))
        .isInstanceOf(ProviderNotFoundException.class);

    verifyNoInteractions(deepseekModel);
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void callFailureAuditRecordedWithSuccessFalse() {
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("connect timeout"));

    var service = new ProviderService(Map.of("deepseek", deepseekModel), adapter, auditRepository);

    assertThatThrownBy(() -> service.chat("s-1", profileUsing("deepseek"), simplePrompt()))
        .isInstanceOf(RuntimeException.class);

    var captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.isSuccess()).isFalse();
    assertThat(entity.getErrorMessage()).contains("timeout");
  }

  @Test
  @DisplayName("带工具schema调用_请求里关闭了自动执行")
  void callWithToolSchemaDisablesAutoExecution() {
    when(httpGetTool.getName()).thenReturn("http_get");
    when(httpGetTool.getDescription()).thenReturn("HTTP GET request");
    when(httpGetTool.getInputSchema()).thenReturn("{\"type\":\"object\"}");

    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(assistantMessage.getContent()).thenReturn("will use tool");
    when(assistantMessage.getToolCalls()).thenReturn(null);

    var service = new ProviderService(Map.of("deepseek", deepseekModel), adapter, auditRepository);

    service.chat("s-1", profileUsing("deepseek"), promptWithTools(httpGetTool));

    var captor = ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
    verify(deepseekModel).call(captor.capture());

    var options = captor.getValue().getOptions();
    assertThat(options).isInstanceOf(FunctionCallingOptions.class);
    var fco = (FunctionCallingOptions) options;
    assertThat(fco.getProxyToolCalls()).isFalse();
    assertThat(fco.getFunctionCallbacks()).isNotEmpty();
  }

  @Test
  @DisplayName("调用成功_审计记录success为true")
  void callSuccessAuditRecordedWithSuccessTrue() {
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(assistantMessage.getContent()).thenReturn("response text");
    when(assistantMessage.getToolCalls()).thenReturn(null);
    when(chatResponse.getMetadata()).thenReturn(metadata);
    when(metadata.getUsage()).thenReturn(usage);
    when(usage.getPromptTokens()).thenReturn(10L);
    when(usage.getGenerationTokens()).thenReturn(5L);
    when(usage.getTotalTokens()).thenReturn(15L);

    var service = new ProviderService(Map.of("deepseek", deepseekModel), adapter, auditRepository);

    var response = service.chat("s-1", profileUsing("deepseek"), simplePrompt());

    assertThat(response.content()).isEqualTo("response text");

    var captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.isSuccess()).isTrue();
    assertThat(entity.getProvider()).isEqualTo("deepseek");
    assertThat(entity.getPromptTokens()).isEqualTo(10);
  }
}
