package com.oryxos.web.dto;

/** 修改 Agent 基本信息(只改 AGENT.md frontmatter 的 description / provider.name / provider.model)。 */
public record UpdateAgentBasicRequest(String description, String provider, String model) {}
