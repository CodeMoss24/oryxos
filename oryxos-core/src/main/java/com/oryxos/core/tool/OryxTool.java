package com.oryxos.core.tool;

/**
 * OryxOS 统一的 Tool 抽象接口。 内置 Tool、@Tool 注解的 Plugin Tool、MCP Tool 都被包装成 OryxTool 实例注册到 ToolRegistry,
 * ReAct 循环不感知具体 Tool 的来源。
 *
 * <p>约定四个核心方法:getName / getDescription / getInputSchema / execute。
 */
public interface OryxTool {

  String getName();

  String getDescription();

  /** 返回 JSON Schema 描述输入参数。Spring AI 的 @Tool 注解会自动生成 schema, 但通过本接口统一暴露给 ReAct 循环。 */
  String getInputSchema();

  /** 执行 Tool。接收 JSON 输入,返回 ToolResult。 实现内部应先做 Sandbox 校验(如需),再执行真正的 IO。 */
  ToolResult execute(String inputJson);
}
