package com.oryxos.web.exception;

/** 请求参数非法(消息为空/超 32KB、必填字段缺失),统一异常出口映射 400。 */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
