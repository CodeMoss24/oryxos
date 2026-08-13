package com.oryxos.web.exception;

/** 领域资源不存在(如 Agent 名不存在),统一异常出口映射 404。 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
