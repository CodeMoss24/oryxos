package com.oryxos.web.dto;

/** 发消息请求体(课件命名):单条消息内容,上限 32KB。 */
public record MessageRequest(String content) {}
