package com.oryxos.tool.config;

import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.builtin.WebSearchTools;
import com.oryxos.tool.demo.DemoCompanyTool;
import com.oryxos.tool.interaction.ConsoleUserInteraction;
import com.oryxos.tool.interaction.InteractionTools;
import com.oryxos.tool.interaction.UnsupportedUserInteraction;
import com.oryxos.tool.interaction.UserInteraction;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.search.DuckDuckGoSearchProvider;
import java.net.http.HttpClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内置工具的统一装配点:每个工具一个 FunctionCallback @Bean,经 builder().method(Java 方法名, 参数类型) 从方法签名自动生成 schema(M4
 * 无 @Tool 注解,这是内置工具与方式三 Plugin Tool 的公共管道)。
 *
 * <p>description 与存量手写工具逐字一致;工具名(暴露给 LLM)与 Java 方法名解耦:方法名驼峰,工具名 保持 read_file
 * 等存量字面量。装配一处可见全部工具,对齐课件"在装配处注册"。
 *
 * <p>UserInteraction 按交互环境条件装配(两实现都不加 @Component,避免歧义):交互式 CLI 用 Console, 其余(Web Service /
 * 定时任务等无人值守)用 Unsupported——ask_user 在任何环境都注册,拿不到回答抛异常 映射为 failure,绝不静默。
 */
@Configuration
@EnableConfigurationProperties({
  FileSandboxProperties.class,
  ShellSandboxProperties.class,
  HttpSandboxProperties.class
})
public class ToolConfiguration {

  @Bean
  public FunctionCallback readFileTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("读取文件内容")
        .method("readFile", String.class)
        .name("read_file")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback writeFileTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("写入文件内容")
        .method("writeFile", String.class, String.class)
        .name("write_file")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback listDirTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("列出目录内容")
        .method("listDir", String.class)
        .name("list_dir")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback shellTool(ShellTools shellTools) {
    return FunctionCallback.builder()
        .description("执行 bash 命令(受命令白名单限制)")
        .method("shell", String.class)
        .name("shell")
        .targetObject(shellTools)
        .build();
  }

  @Bean
  public FunctionCallback httpGetTool(HttpTools httpTools) {
    return FunctionCallback.builder()
        .description("发起 HTTP GET 请求(受域名白名单限制)")
        .method("httpGet", String.class)
        .name("http_get")
        .targetObject(httpTools)
        .build();
  }

  @Bean
  public FunctionCallback httpPostTool(HttpTools httpTools) {
    return FunctionCallback.builder()
        .description("发起 HTTP POST 请求(受域名白名单限制)")
        .method("httpPost", String.class, String.class)
        .name("http_post")
        .targetObject(httpTools)
        .build();
  }

  @Bean
  public FunctionCallback editFileTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("编辑文件:把唯一匹配的旧文本替换为新文本;找不到或出现多次都不改动")
        .method("editFile", String.class, String.class, String.class)
        .name("edit_file")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback grepTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("在目录内按正则搜索文件内容,返回 文件:行号:内容,上限 200 条")
        .method("grep", String.class, String.class)
        .name("grep")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback globTool(FileTools fileTools) {
    return FunctionCallback.builder()
        .description("按通配模式查找路径(如 /tmp/**/*.txt),上限 200 条")
        .method("glob", String.class)
        .name("glob")
        .targetObject(fileTools)
        .build();
  }

  @Bean
  public FunctionCallback askUserTool(InteractionTools interactionTools) {
    return FunctionCallback.builder()
        .description("中途向用户提问并等待回答(human-in-the-loop);当前环境不支持交互时会失败并说明")
        .method("askUser", String.class)
        .name("ask_user")
        .targetObject(interactionTools)
        .build();
  }

  @Bean
  public FunctionCallback webSearchTool(WebSearchTools webSearchTools) {
    return FunctionCallback.builder()
        .description("联网搜索网页,返回标题/URL/摘要")
        .method("webSearch", String.class)
        .name("web_search")
        .targetObject(webSearchTools)
        .build();
  }

  // ---- 方式三演示(业务方自研工具):加一个 Bean + 两行装配 = 工具立即可用 ----
  @Bean
  public FunctionCallback demoHelloTool(DemoCompanyTool demoCompanyTool) {
    return FunctionCallback.builder()
        .description("方式三示例:问候用户(业务方 Java 方法直接注册的工具)")
        .method("hello", String.class)
        .name("demo_hello")
        .targetObject(demoCompanyTool)
        .build();
  }

  @Bean
  public FunctionCallback demoQuoteStockTool(DemoCompanyTool demoCompanyTool) {
    return FunctionCallback.builder()
        .description("方式三示例:查询股票行情(mock 数据,演示接入链路)")
        .method("quoteStock", String.class)
        .name("demo_quote_stock")
        .targetObject(demoCompanyTool)
        .build();
  }

  @Bean
  public DuckDuckGoSearchProvider duckDuckGoSearchProvider(
      Sandbox sandbox,
      @Value("${oryxos.search.duckduckgo.base-url:https://api.duckduckgo.com}") String baseUrl) {
    return new DuckDuckGoSearchProvider(sandbox, baseUrl, HttpClient.newHttpClient());
  }

  @Bean
  @ConditionalOnProperty(name = "oryxos.cli.interactive", havingValue = "true")
  public UserInteraction consoleUserInteraction() {
    return new ConsoleUserInteraction();
  }

  @Bean
  @ConditionalOnProperty(
      name = "oryxos.cli.interactive",
      havingValue = "false",
      matchIfMissing = true)
  public UserInteraction unsupportedUserInteraction() {
    return new UnsupportedUserInteraction();
  }
}
