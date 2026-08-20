package com.oryxos.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 单个 provider 定义 → ChatModel 的构建工厂(Provider 动态化的另一半:注册表管配置,本类管构建)。
 *
 * <p>从 ProviderAutoConfiguration 提取:mock 是独立 mock provider(无 key、不联网); 其余按 name 建 OpenAI 兼容
 * ChatModel。baseUrl 约定不含 /v1(与 ProviderModelsService 的 modelsUrl 规则一致,OpenAiApi 内部自行补
 * /v1/chat/completions)。
 */
@Component
public class ProviderChatModelFactory {

  private static final Logger log = LoggerFactory.getLogger(ProviderChatModelFactory.class);

  private final RestClient.Builder restClientBuilder;
  private final WebClient.Builder webClientBuilder;

  public ProviderChatModelFactory(
      RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
    this.restClientBuilder = restClientBuilder;
    this.webClientBuilder = webClientBuilder;
  }

  /** 按 provider 定义构建 ChatModel;每次调用都新建(调用方负责缓存,配置变更即重建)。 */
  public ChatModel buildOne(String name, String apiKey, String baseUrl) {
    if ("mock".equals(name)) {
      return new MockChatModel();
    }
    var api = new OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder);
    log.info("Built ChatModel for provider: {} ({})", name, baseUrl);
    return new OpenAiChatModel(api, OpenAiChatOptions.builder().build());
  }
}
