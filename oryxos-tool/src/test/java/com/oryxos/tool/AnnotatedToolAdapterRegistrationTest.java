package com.oryxos.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 方式三(业务方自有工具)端到端 harness:在测试上下文里声明一个自定义 FunctionCallback @Bean, ToolAutoRegistrar 自动扫描 →
 * AnnotatedToolAdapter 包装 → 注册进 ToolRegistry → 可调用。 M4 无 @Tool 注解,FunctionCallback Bean 即方式三的等价形态。
 */
class AnnotatedToolAdapterRegistrationTest {

  @TempDir static Path tempDir;

  @BeforeAll
  static void start() {
    ToolTestFixture.start(tempDir, CustomToolConfiguration.class);
  }

  @AfterAll
  static void stop() {
    ToolTestFixture.stop();
  }

  @Test
  @DisplayName("自定义 FunctionCallback Bean 自动扫描→包装→注册→可调")
  void customCallbackBeanRegisteredAndCallable() {
    OryxTool tool =
        ToolTestFixture.registry()
            .find("my_custom_tool")
            .orElseThrow(() -> new AssertionError("自定义工具未注册: my_custom_tool"));

    assertNotNull(tool.getDescription());
    assertNotNull(tool.getInputSchema());

    ToolResult r = tool.execute("{\"name\":\"world\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertEquals("hello world", r.content());
  }

  /** 业务方自有工具装配(方式三):普通类 + 普通方法 + FunctionCallback @Bean */
  @Configuration
  static class CustomToolConfiguration {

    @Bean
    public FunctionCallback myCustomTool() {
      return FunctionCallback.builder()
          .description("自定义问候工具")
          .method("greet", String.class)
          .name("my_custom_tool")
          .targetObject(new Greeter())
          .build();
    }
  }

  /** 反射从其他包调用,必须是 public */
  public static class Greeter {
    public String greet(String name) {
      return "hello " + name;
    }
  }
}
