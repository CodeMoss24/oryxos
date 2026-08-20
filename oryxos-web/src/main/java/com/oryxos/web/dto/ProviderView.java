package com.oryxos.web.dto;

import com.oryxos.core.provider.ProviderRegistry.ProviderDef;

/** Provider 视图:apiKey 掩码(只留末 4 位,如 ****abcd),避免明文 key 出网。 */
public record ProviderView(String name, String apiKey, String baseUrl, String description) {

  public static ProviderView from(ProviderDef def) {
    return new ProviderView(def.name(), mask(def.apiKey()), def.baseUrl(), def.description());
  }

  /** 掩码:空 key → 空串;≤4 位 → ****;否则 **** + 末 4 位。 */
  public static String mask(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return "";
    }
    if (apiKey.length() <= 4) {
      return "****";
    }
    return "****" + apiKey.substring(apiKey.length() - 4);
  }
}
