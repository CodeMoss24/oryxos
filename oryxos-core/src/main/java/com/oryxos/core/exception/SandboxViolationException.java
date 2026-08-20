package com.oryxos.core.exception;

/**
 * Sandbox 校验失败异常。任意校验失败抛本异常,Tool 执行终止; 异常信息复用 ToolExecutor 已有的失败审计路径写入
 * tool_invocations(success=false)。
 *
 * <p>作为契约放 core(web 模块只依赖 core,GlobalExceptionHandler 按 4031 映射),实现位于 tool 模块的 WhitelistSandbox。
 */
public class SandboxViolationException extends RuntimeException {

  public SandboxViolationException(String message) {
    super(message);
  }
}
