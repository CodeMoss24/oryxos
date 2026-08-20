package com.oryxos.tool.adapter;

import com.oryxos.core.exception.SandboxViolationException;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * 把 Spring AI 的 FunctionCallback(带注解/装配声明的方法包装) 适配成 OryxTool。
 *
 * <p>Spring AI 1.0.0-M4 没有 {@code @Tool} 注解与 ToolCallback(该 API 是 M5+ 引入),统一的工具抽象载体是
 * FunctionCallback——schema 由装配处从方法签名自动生成。本项目"内置工具与方式三 Plugin Tool 共用一条管道"的设计 由此承载:任何
 * FunctionCallback Bean 启动时被包装成 OryxTool 注册,ReAct 循环不感知来源。
 *
 * <p>只做适配,不含任何执行逻辑——真正的工具方法由 FunctionCallback 委托,执行调度仍由 ReActLoop + ToolExecutor 控制。
 *
 * <p>异常映射与存量内置工具语义一致:越界(SandboxViolationException)失败且不可重试——重试只会再撞一次白名单; 其余异常可重试。反射调用会把 checked 异常包成
 * UndeclaredThrowableException,统一取根因消息保证错误可读。
 */
public class AnnotatedToolAdapter implements OryxTool {

  private final FunctionCallback functionCallback;

  public AnnotatedToolAdapter(FunctionCallback functionCallback) {
    this.functionCallback = functionCallback;
  }

  @Override
  public String getName() {
    return functionCallback.getName();
  }

  @Override
  public String getDescription() {
    return functionCallback.getDescription();
  }

  @Override
  public String getInputSchema() {
    return functionCallback.getInputTypeSchema();
  }

  @Override
  public ToolResult execute(String inputJson) {
    try {
      return ToolResult.success(functionCallback.call(inputJson));
    } catch (SandboxViolationException e) {
      return ToolResult.failure(e.getMessage(), false);
    } catch (Exception e) {
      return ToolResult.failure(rootMessage(e), true);
    }
  }

  private static String rootMessage(Exception e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root.getMessage() != null ? root.getMessage() : e.getMessage();
  }
}
