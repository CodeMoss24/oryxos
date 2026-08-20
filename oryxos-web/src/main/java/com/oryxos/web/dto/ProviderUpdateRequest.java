package com.oryxos.web.dto;

/** 更新 Provider 请求:apiKey 提交值等于掩码值(****abcd)时视为未修改,保留原 key。 */
public record ProviderUpdateRequest(String apiKey, String baseUrl, String description) {}
