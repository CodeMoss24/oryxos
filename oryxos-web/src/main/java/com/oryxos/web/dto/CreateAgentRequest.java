package com.oryxos.web.dto;

/** 创建 Agent 请求体:{name, description},只填这两个,目录结构由脚手架生成。 */
public record CreateAgentRequest(String name, String description) {}
