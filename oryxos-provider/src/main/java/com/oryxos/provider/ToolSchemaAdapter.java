package com.oryxos.provider;

import com.oryxos.core.tool.OryxTool;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * 把 OryxOS 内部的 OryxTool 的输入 schema 翻译成 Spring AI 的工具描述格式。 只翻译,不执行——产物只含 schema(name / description /
 * inputTypeSchema),不含任何执行逻辑。
 */
public class ToolSchemaAdapter {

  /**
   * 将 OryxTool 列表翻译成 Spring AI FunctionCallback 列表。 每个 FunctionCallback 的 call() 返回空字符串——实际执行由
   * ToolExecutor 负责, 与 constitution 原则四对齐(禁用 Spring AI 自动 tool 执行)。
   */
  public List<FunctionCallback> toSpringAiTools(List<OryxTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    return tools.stream()
        .map(
            t ->
                FunctionCallback.builder()
                    .description(t.getDescription())
                    .inputTypeSchema(t.getInputSchema())
                    .function(t.getName(), (Map input, ToolContext context) -> "")
                    .inputType(Map.class)
                    .build())
        .toList();
  }
}
