package com.oryxos.tool;

import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.builtin.WebSearchTools;
import com.oryxos.tool.config.ToolConfiguration;
import com.oryxos.tool.demo.DemoCompanyTool;
import com.oryxos.tool.interaction.InteractionTools;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 全部 harness 测试共用的装配基座:用 AnnotationConfigApplicationContext 手动装配—— 真实
 * WhitelistSandbox(白名单受控:路径=JUnit @TempDir,命令=[echo,ls],域名=[api.weather.com, api.duckduckgo.com])+
 * 内置工具类 + ToolConfiguration + ToolAutoRegistrar + ToolRegistry。
 *
 * <p>安全校验用真实实现,不 mock 掉——越界测试断言的就是真实白名单拦截。
 */
public final class ToolTestFixture {

  private static AnnotationConfigApplicationContext context;

  private ToolTestFixture() {}

  /** 每个测试类在 @BeforeAll 调用,传入各自的 @TempDir;多次调用各自建独立上下文互不影响。 */
  public static void start(Path tempDir) {
    start(tempDir, new Class<?>[0]);
  }

  /** 同 start(tempDir),额外注册测试自定义配置类(如方式三演示用的 FunctionCallback @Bean)。 */
  public static void start(Path tempDir, Class<?>... extraConfigs) {
    if (context != null) {
      throw new IllegalStateException("ToolTestFixture already started, call stop() first");
    }
    context = new AnnotationConfigApplicationContext();
    context.registerBean(Path.class, () -> tempDir);
    context.registerBean(
        WhitelistSandbox.class,
        () ->
            new WhitelistSandbox(
                new FileSandboxProperties(List.of(tempDir.toString())),
                new ShellSandboxProperties(List.of("echo", "ls")),
                new HttpSandboxProperties(List.of("api.weather.com", "api.duckduckgo.com"))));
    context.register(FileTools.class, ShellTools.class, HttpTools.class);
    context.register(InteractionTools.class, WebSearchTools.class, DemoCompanyTool.class);
    context.register(ToolConfiguration.class);
    context.register(ToolAutoRegistrar.class, ToolRegistry.class);
    if (extraConfigs != null) {
      for (Class<?> config : extraConfigs) {
        context.register(config);
      }
    }
    context.refresh();
  }

  public static void stop() {
    if (context != null) {
      context.close();
      context = null;
    }
  }

  public static ToolRegistry registry() {
    return context.getBean(ToolRegistry.class);
  }

  public static Sandbox sandbox() {
    return context.getBean(Sandbox.class);
  }

  public static Path tempDir() {
    return context.getBean(Path.class);
  }
}
