package com.oryxos.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.adapter.AnnotatedToolAdapter;
import com.oryxos.tool.mcp.McpToolAdapter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * 注册表 harness:三种来源(内置/FunctionCallback 包装/MCP 适配器)都注册为 OryxTool; subset
 * 过滤结果恰好等于声明列表——多一个(没过滤干净)和少一个(过滤过头)都是错。
 */
class ToolRegistryTest {

  @TempDir static Path tempDir;

  @BeforeAll
  static void start() {
    ToolTestFixture.start(tempDir);
  }

  @AfterAll
  static void stop() {
    ToolTestFixture.stop();
  }

  @Test
  @DisplayName("三种来源的工具都以 OryxTool 身份注册")
  void threeSourcesRegisterAsOryxTool() {
    ToolRegistry registry = new ToolRegistry();

    // 来源一:内置工具(经 FunctionCallback 管道注册进 fixture 注册表,取出来再注册)
    OryxTool builtin =
        ToolTestFixture.registry()
            .find("read_file")
            .orElseThrow(() -> new AssertionError("内置工具未注册: read_file"));
    registry.register(builtin);

    // 来源二:自定义 FunctionCallback Bean,经 AnnotatedToolAdapter 包装注册(方式三管道)
    registry.register(new AnnotatedToolAdapter(customFunctionCallback()));

    // 来源三:MCP 适配器(方式二)——client mock 掉,只验证注册身份
    McpSchema.Tool mcpTool;
    try {
      mcpTool =
          new ObjectMapper()
              .readValue(
                  "{\"name\":\"mcp_demo\",\"description\":\"演示用 MCP 工具\","
                      + "\"inputSchema\":{\"type\":\"object\"}}",
                  McpSchema.Tool.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    registry.register(new McpToolAdapter(mock(McpSyncClient.class), mcpTool));

    assertTrue(registry.find("read_file").isPresent());
    assertTrue(registry.find("custom_echo").isPresent());
    assertTrue(registry.find("mcp_demo").isPresent());
    ToolResult customResult = registry.find("custom_echo").orElseThrow().execute("{}");
    assertTrue(
        customResult.success(), () -> "expected success but got: " + customResult.errorMessage());
    assertEquals("custom-ok", customResult.content());
  }

  @Test
  @DisplayName("subset 过滤结果恰好等于声明列表")
  void subsetMatchesExactly() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(
        ToolTestFixture.registry()
            .find("read_file")
            .orElseThrow(() -> new AssertionError("内置工具未注册: read_file")));
    registry.register(
        ToolTestFixture.registry()
            .find("shell")
            .orElseThrow(() -> new AssertionError("内置工具未注册: shell")));
    registry.register(
        ToolTestFixture.registry()
            .find("http_get")
            .orElseThrow(() -> new AssertionError("内置工具未注册: http_get")));

    List<OryxTool> subset = registry.subset(List.of("read_file", "shell"));
    assertEquals(2, subset.size(), "子集大小必须恰好等于声明列表");
    List<String> names = subset.stream().map(OryxTool::getName).toList();
    assertTrue(names.contains("read_file"));
    assertTrue(names.contains("shell"));

    // 声明里存在但注册表没有的名字 → 只返回命中的部分,不多不少
    List<OryxTool> partial = registry.subset(List.of("read_file", "no_such_tool"));
    assertEquals(1, partial.size());
    assertEquals("read_file", partial.get(0).getName());
  }

  private static FunctionCallback customFunctionCallback() {
    return FunctionCallback.builder()
        .description("自定义工具")
        .method("customMethod")
        .name("custom_echo")
        .targetObject(new CustomToolHost())
        .build();
  }

  /** 方式三演示宿主:普通类 + 普通方法,由 FunctionCallback 反射调用。必须是 public——Spring 反射从其他包调用 */
  public static class CustomToolHost {
    public String customMethod() {
      return "custom-ok";
    }
  }
}
