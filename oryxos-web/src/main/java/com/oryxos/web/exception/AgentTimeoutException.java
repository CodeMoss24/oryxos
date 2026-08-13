package com.oryxos.web.exception;

/** Agent 调用超过 60 秒上限,统一异常出口映射 504。 */
public class AgentTimeoutException extends RuntimeException {

  public AgentTimeoutException(String message) {
    super(message);
  }
}
