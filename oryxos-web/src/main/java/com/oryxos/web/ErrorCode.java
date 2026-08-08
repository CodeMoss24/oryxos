package com.oryxos.web;

/** 错误码枚举。 */
public enum ErrorCode {
  BAD_REQUEST(400, "请求参数错误"),
  NOT_FOUND(404, "资源不存在"),
  INTERNAL_ERROR(500, "服务器内部错误"),
  SERVICE_UNAVAILABLE(503, "服务暂不可用"),
  SANDBOX_VIOLATION(4031, "沙箱安全限制"),
  TOOL_EXECUTION_ERROR(5001, "工具执行失败"),
  LLM_CALL_ERROR(5002, "LLM 调用失败");

  public final int code;
  public final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
