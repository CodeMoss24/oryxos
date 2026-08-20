package com.oryxos.web.dto;

/** 覆写 AGENT.md 请求体:content 为 AGENT.md 全文(frontmatter + 正文)。 */
public record UpdateAgentRequest(String content) {}
