package com.oryxos.provider;

import java.util.ArrayList;
import java.util.List;

public class ProviderProperties {

  private List<ProviderEntry> providers = new ArrayList<>();

  public List<ProviderEntry> getProviders() {
    return providers;
  }

  public void setProviders(List<ProviderEntry> providers) {
    this.providers = providers;
  }

  public record ProviderEntry(String name, String apiKey, String baseUrl) {}
}
