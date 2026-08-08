package com.oryxos.tool.sandbox;

/**
 * Sandbox 校验失败异常。任意校验失败抛本异常,Tool 执行终止; 异常信息复用 ToolExecutor 已有的失败审计路径写入
 * tool_invocations(success=false)。
 */
public class SandboxViolationException extends RuntimeException {

  public SandboxViolationException(String message) {
    super(message);
  }
}
