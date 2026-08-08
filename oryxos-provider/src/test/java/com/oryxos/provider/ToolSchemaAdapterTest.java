package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToolSchemaAdapter — 工具 schema 翻译验收")
class ToolSchemaAdapterTest {

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  private static OryxTool mockTool(String name, String description, String schema) {
    return new OryxTool() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDescription() {
        return description;
      }

      @Override
      public String getInputSchema() {
        return schema;
      }

      @Override
      public ToolResult execute(String inputJson) {
        return ToolResult.success("ok");
      }
    };
  }

  @Test
  @DisplayName("OryxTool schema 翻译成 Spring AI FunctionCallback 后字段一一对齐")
  void schemaFieldsAlignedAfterTranslation() {
    var tool =
        mockTool(
            "http_get",
            "HTTP GET request",
            "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}");

    var result = adapter.toSpringAiTools(List.of(tool));

    assertThat(result).hasSize(1);
    var callback = result.get(0);
    assertThat(callback.getName()).isEqualTo("http_get");
    assertThat(callback.getDescription()).isEqualTo("HTTP GET request");
    assertThat(callback.getInputTypeSchema()).contains("url");
  }

  @Test
  @DisplayName("产物只含 schema 不含执行逻辑")
  void outputContainsNoExecutionLogic() {
    var tool =
        mockTool(
            "shell",
            "Execute shell command",
            "{\"type\":\"object\",\"properties\":{\"cmd\":{\"type\":\"string\"}}}");

    var result = adapter.toSpringAiTools(List.of(tool));

    assertThat(result).hasSize(1);
    var callback = result.get(0);
    // call() should return empty — no real execution
    assertThat(callback.call("{\"cmd\":\"ls\"}")).isEmpty();
  }

  @Test
  @DisplayName("空工具列表返回空回调列表")
  void emptyToolsReturnsEmptyList() {
    var result = adapter.toSpringAiTools(List.of());
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("多个工具逐一翻译，数量一致")
  void multipleToolsTranslateOneToOne() {
    var tools =
        List.of(
            mockTool("a", "Tool A", "{}"),
            mockTool("b", "Tool B", "{}"),
            mockTool("c", "Tool C", "{}"));

    var result = adapter.toSpringAiTools(tools);
    assertThat(result).hasSize(3);
    assertThat(result.stream().map(org.springframework.ai.model.function.FunctionCallback::getName))
        .containsExactly("a", "b", "c");
  }
}
