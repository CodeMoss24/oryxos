package com.oryxos.provider;

import com.oryxos.core.provider.ProviderRegistry;
import com.oryxos.storage.repository.LlmCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provider 自动配置:暴露 ProviderProperties(oryxos.providers 列表)与 ProviderService Bean。
 *
 * <p>Provider 动态化后不再构造期构建 ChatModel 映射——配置唯一事实源是 {@link ProviderRegistry}(DB,启动播种
 * YAML),ProviderService 按 (apiKey, baseUrl) 指纹缓存并动态重建 ChatModel(改配置免重启生效)。构建逻辑收进 {@link
 * ProviderChatModelFactory}(@Component 由组件扫描装配)。
 */
@Configuration
public class ProviderAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderAutoConfiguration.class);

  @Bean
  @ConfigurationProperties("oryxos")
  public ProviderProperties providerProperties() {
    return new ProviderProperties();
  }

  @Bean
  public ToolSchemaAdapter toolSchemaAdapter() {
    return new ToolSchemaAdapter();
  }

  @Bean
  public ProviderService providerService(
      ProviderRegistry registry,
      ProviderChatModelFactory modelFactory,
      RestClient.Builder restClientBuilder,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    return new ProviderService(
        registry, modelFactory, restClientBuilder, toolSchemaAdapter, llmCallRepository);
  }
}
