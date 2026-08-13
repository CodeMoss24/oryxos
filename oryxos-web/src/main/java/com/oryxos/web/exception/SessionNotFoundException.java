package com.oryxos.web.exception;

/** 会话不存在(发消息/查历史/归档时),统一异常出口映射 404。 */
public class SessionNotFoundException extends RuntimeException {

  public SessionNotFoundException(String message) {
    super(message);
  }
}
