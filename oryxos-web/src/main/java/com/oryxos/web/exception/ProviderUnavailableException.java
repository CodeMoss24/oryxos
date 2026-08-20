package com.oryxos.web.exception;

/** Provider 故障,统一异常出口映射 503。核心阶段预留抛出点,映射先行。 */
public class ProviderUnavailableException extends RuntimeException {

  public ProviderUnavailableException(String message) {
    super(message);
  }

  public ProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
