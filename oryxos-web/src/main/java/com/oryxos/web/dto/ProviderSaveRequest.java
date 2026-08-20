package com.oryxos.web.dto;

/** 创建 Provider 请求。名为 mock 的 provider 免 base-url;其余必须有 base-url。 */
public record ProviderSaveRequest(String name, String apiKey, String baseUrl, String description) {}
