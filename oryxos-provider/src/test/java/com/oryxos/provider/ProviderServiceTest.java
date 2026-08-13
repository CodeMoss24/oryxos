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
import org.springframework.web.client.RestClient;

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

  @Mock private RestClient.Builder restClientBuilder;

  @Mock private RestClient restClient;

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

  private ProviderService makeService(Map<String, ChatModel> models) {
    return makeService(models, Map.of());
  }

  private ProviderService makeService(
      Map<String, ChatModel> models, Map<String, ProviderProperties.ProviderEntry> configs) {
    return new ProviderService(models, configs, restClientBuilder, adapter, auditRepository);
  }

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routeByNameTwoProvidersNoCrossTalk() {
    when(kimiModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(assistantMessage.getContent()).thenReturn("ok from kimi");

    var service = makeService(Map.of("deepseek", deepseekModel, "kimi", kimiModel));

    service.chat("s-1", profileUsing("kimi"), simplePrompt());

    verify(kimiModel, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    verify(deepseekModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  @DisplayName("未知 provider 名抛异常")
  void unknownProviderThrowsException() {
    var service = makeService(Map.of("deepseek", deepseekModel));

    assertThatThrownBy(() -> service.chat("s-1", profileUsing("kimi"), simplePrompt()))
        .isInstanceOf(ProviderNotFoundException.class);

    verifyNoInteractions(deepseekModel);
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void callFailureAuditRecordedWithSuccessFalse() {
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("connect timeout"));

    var service = makeService(Map.of("deepseek", deepseekModel));

    assertThatThrownBy(() -> service.chat("s-1", profileUsing("deepseek"), simplePrompt()))
        .isInstanceOf(RuntimeException.class);

    var captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.isSuccess()).isFalse();
    assertThat(entity.getErrorMessage()).contains("timeout");
  }

  @Test
  @DisplayName("带工具schema调用_走直接API路径不通过ChatModel")
  void callWithToolSchemaGoesDirectApiNotChatModel() {
    when(httpGetTool.getName()).thenReturn("http_get");
    when(httpGetTool.getDescription()).thenReturn("HTTP GET request");
    when(httpGetTool.getInputSchema()).thenReturn("{\"type\":\"object\"}");

    // 带工具时 ProviderService 现在绕过 ChatModel 直接用 RestClient 调 API
    // 对于单元测试,我们验证它不会走 ChatModel.call() 路径
    var service = makeService(Map.of("deepseek", deepseekModel));

    // 由于 RestClient 未被完整 mock,此处会失败;但我们先验证不调 ChatModel
    assertThatThrownBy(
            () -> service.chat("s-1", profileUsing("deepseek"), promptWithTools(httpGetTool)))
        .isInstanceOf(RuntimeException.class);

    verify(deepseekModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  @DisplayName("调用成功_审计记录success为true")
  void callSuccessAuditRecordedWithSuccessTrue() {
    when(deepseekModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);
    when(chatResponse.getResult()).thenReturn(generation);
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(assistantMessage.getContent()).thenReturn("response text");
    when(chatResponse.getMetadata()).thenReturn(metadata);
    when(metadata.getUsage()).thenReturn(usage);
    when(usage.getPromptTokens()).thenReturn(10L);
    when(usage.getGenerationTokens()).thenReturn(5L);
    when(usage.getTotalTokens()).thenReturn(15L);

    var service = makeService(Map.of("deepseek", deepseekModel));

    var response = service.chat("s-1", profileUsing("deepseek"), simplePrompt());

    assertThat(response.content()).isEqualTo("response text");

    var captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.isSuccess()).isTrue();
    assertThat(entity.getProvider()).isEqualTo("deepseek");
    assertThat(entity.getPromptTokens()).isEqualTo(10);
  }

  @Test
  @DisplayName("connectivity_探测成功返回true")
  void connectivityProbeSucceedsReturnsTrue() {
    var configs =
        Map.of(
            "deepseek",
            new ProviderProperties.ProviderEntry("deepseek", "k", "https://api.deepseek.com"));
    stubProbeChain();

    var service = makeService(Map.of("deepseek", deepseekModel), configs);

    assertThat(service.connectivity()).containsEntry("deepseek", true);
  }

  @Test
  @DisplayName("connectivity_4xx响应也算地址可达")
  void connectivity4xxResponseCountsReachable() {
    var configs =
        Map.of(
            "deepseek",
            new ProviderProperties.ProviderEntry("deepseek", "k", "https://api.deepseek.com"));
    RestClient.ResponseSpec responseSpec = stubProbeChain();
    org.mockito.Mockito.doThrow(
            new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.NOT_FOUND))
        .when(responseSpec)
        .toBodilessEntity();

    var service = makeService(Map.of("deepseek", deepseekModel), configs);

    assertThat(service.connectivity()).containsEntry("deepseek", true);
  }

  @Test
  @DisplayName("connectivity_连接失败返回false不抛异常")
  void connectivityConnectionFailureReturnsFalse() {
    var configs =
        Map.of(
            "deepseek",
            new ProviderProperties.ProviderEntry("deepseek", "k", "https://api.deepseek.com"));
    RestClient.ResponseSpec responseSpec = stubProbeChain();
    when(responseSpec.toBodilessEntity())
        .thenThrow(new org.springframework.web.client.ResourceAccessException("connect timeout"));

    var service = makeService(Map.of("deepseek", deepseekModel), configs);

    assertThat(service.connectivity()).containsEntry("deepseek", false);
  }

  @Test
  @DisplayName("connectivity_未配置任何provider返回空Map不抛异常")
  void connectivityNoProvidersReturnsEmptyMap() {
    var service = makeService(Map.of("deepseek", deepseekModel));

    assertThat(service.connectivity()).isEmpty();
    verifyNoInteractions(restClientBuilder);
  }

  /**
   * 把探测用的 builder 链(clone→requestFactory→baseUrl→build→get→retrieve)stub 成返回 200。
   *
   * @return ResponseSpec,供用例覆写 toBodilessEntity 的行为
   */
  private RestClient.ResponseSpec stubProbeChain() {
    RestClient.Builder probeBuilder = org.mockito.Mockito.mock(RestClient.Builder.class);
    RestClient.RequestHeadersUriSpec<?> spec =
        org.mockito.Mockito.mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
    when(restClientBuilder.clone()).thenReturn(probeBuilder);
    when(probeBuilder.requestFactory(any())).thenReturn(probeBuilder);
    when(probeBuilder.baseUrl(anyString())).thenReturn(probeBuilder);
    when(probeBuilder.build()).thenReturn(restClient);
    org.mockito.Mockito.doReturn(spec).when(restClient).get(); // doReturn 规避通配符捕获检查
    when(spec.retrieve()).thenReturn(responseSpec);
    // lenient:个别用例会用 doThrow 覆写这条 stub
    lenient()
        .when(responseSpec.toBodilessEntity())
        .thenReturn(org.springframework.http.ResponseEntity.ok().build());
    return responseSpec;
  }
}
