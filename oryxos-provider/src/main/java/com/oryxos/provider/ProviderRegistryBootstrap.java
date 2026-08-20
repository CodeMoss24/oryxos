package com.oryxos.provider;

import com.oryxos.core.provider.ProviderRegistry;
import com.oryxos.core.provider.ProviderRegistry.ProviderDef;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 启动播种:把 application.yaml 的 oryxos.providers 里注册表缺失的有效条目补进 provider_configs(只补缺失,不覆写)。
 *
 * <p>之后注册表(DB)是唯一事实源——管理台增删改直接落库,重启不丢也不回退 YAML。
 */
@Component
public class ProviderRegistryBootstrap {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistryBootstrap.class);

  private final ProviderRegistry registry;
  private final ProviderProperties properties;

  public ProviderRegistryBootstrap(ProviderRegistry registry, ProviderProperties properties) {
    this.registry = registry;
    this.properties = properties;
  }

  @PostConstruct
  public void seedMissing() {
    if (properties.getProviders() == null) {
      return;
    }
    for (ProviderProperties.ProviderEntry entry : properties.getProviders()) {
      if (entry.name() == null || entry.name().isBlank()) {
        continue;
      }
      if (!registry.exists(entry.name())) {
        registry.save(new ProviderDef(entry.name(), entry.apiKey(), entry.baseUrl(), null));
        log.info("Seeded provider from YAML: {}", entry.name());
      }
    }
  }
}
