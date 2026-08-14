package com.oryxos.provider;

import com.oryxos.storage.repository.LlmCallRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Provider 自动配置:按 oryxos.providers 列表显式构建 provider name → ChatModel 映射, 并暴露 ProviderService Bean。
 */
@Configuration
public class ProviderAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderAutoConfiguration.class);

  @Bean
  @ConfigurationProperties("oryxos")
  public ProviderProperties providerProperties() {
    return new ProviderProperties();
  }

  /** 显式映射表:provider name → ChatModel。 不靠类型扫描,每个 provider 条目独立创建 OpenAiChatModel 实例。 */
  @Bean
  public Map<String, ChatModel> providerModelMap(
      ProviderProperties props,
      RestClient.Builder restClientBuilder,
      WebClient.Builder webClientBuilder) {
    Map<String, ChatModel> map = new HashMap<>();
    if (props.getProviders() != null) {
      for (var entry : props.getProviders()) {
        // 第 27 节:mock 是独立 mock provider——无 key、不联网,按脚本驱动 ReAct,不进 OpenAiApi 构造
        if ("mock".equals(entry.name())) {
          map.put(entry.name(), new MockChatModel());
          log.info("Registered LLM provider: mock (no-key deterministic script)");
          continue;
        }
        var api =
            new OpenAiApi(entry.baseUrl(), entry.apiKey(), restClientBuilder, webClientBuilder);
        var chatModel = new OpenAiChatModel(api, OpenAiChatOptions.builder().build());
        map.put(entry.name(), chatModel);
        log.info("Registered LLM provider: {} ({})", entry.name(), entry.baseUrl());
      }
    }
    return map;
  }

  @Bean
  public ToolSchemaAdapter toolSchemaAdapter() {
    return new ToolSchemaAdapter();
  }

  @Bean
  public Map<String, ProviderProperties.ProviderEntry> providerConfigMap(ProviderProperties props) {
    Map<String, ProviderProperties.ProviderEntry> map = new HashMap<>();
    if (props.getProviders() != null) {
      for (var entry : props.getProviders()) {
        map.put(entry.name(), entry);
      }
    }
    return map;
  }

  @Bean
  public ProviderService providerService(
      Map<String, ChatModel> providerMap,
      Map<String, ProviderProperties.ProviderEntry> providerConfigs,
      RestClient.Builder restClientBuilder,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    return new ProviderService(
        providerMap, providerConfigs, restClientBuilder, toolSchemaAdapter, llmCallRepository);
  }
}
