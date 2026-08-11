package com.oryxos.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.ToolTestFixture;
import com.oryxos.tool.adapter.AnnotatedToolAdapter;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.nio.file.Path;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * HTTP 工具 harness:正常能跑通 + 越界会被拦。
 *
 * <p>正常路径经 MockWebServer 假端点(不碰真网)。fixture 的域名白名单固定为外部域名(T005),因此此处自建 一个放行 localhost 的
 * WhitelistSandbox + HttpTools,走与生产一致的 FunctionCallback → AnnotatedToolAdapter 管道注册进独立
 * ToolRegistry——管道形态与越界路径完全相同,只是域名白名单不同。越界路径走 fixture 真实注册表。
 */
class HttpToolsTest {

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
  @DisplayName("http_get:正常能跑通(MockWebServer 假端点)")
  void httpGetWorks() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
      server.start();
      ToolResult r =
          executeAgainstMock(server, "http_get", "{\"url\":\"" + server.url("/weather") + "\"}");
      assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
      assertTrue(r.content().contains("\"ok\":true"));
    }
  }

  @Test
  @DisplayName("http_post:正常能跑通(MockWebServer 假端点)")
  void httpPostWorks() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("accepted"));
      server.start();
      ToolResult r =
          executeAgainstMock(
              server, "http_post", "{\"url\":\"" + server.url("/report") + "\",\"body\":\"{}\"}");
      assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
      assertTrue(r.content().contains("accepted"));
    }
  }

  @Test
  @DisplayName("http_get:越界会被拦——请求从未发出(MockWebServer 计数为 0)")
  void httpGetBlocked() throws Exception {
    // fixture 沙箱域名白名单不含 localhost;URL 指向 MockWebServer——若校验失效请求会到达 server,计数即 >0
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("secret"));
      server.start();
      ToolResult r =
          ToolTestFixture.registry()
              .find("http_get")
              .orElseThrow(() -> new AssertionError("tool not registered: http_get"))
              .execute("{\"url\":\"" + server.url("/data") + "\"}");
      assertFalse(r.success(), "白名单外域名必须失败");
      assertTrue(r.errorMessage().contains("不在白名单内"), () -> "错误信息应含拦截说明, got: " + r.errorMessage());
      assertEquals(0, server.getRequestCount(), "白名单校验失败后不得发出任何请求");
    }
  }

  /** 自建放行 localhost 的沙箱 + 工具,走 FunctionCallback 管道注册,再经注册表执行——与生产路径同构。 */
  private static ToolResult executeAgainstMock(
      MockWebServer server, String name, String inputJson) {
    WhitelistSandbox sandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of(tempDir.toString())),
            new ShellSandboxProperties(List.of("echo", "ls")),
            new HttpSandboxProperties(List.of("localhost")));
    HttpTools tools = new HttpTools(sandbox);
    FunctionCallback fc;
    if (name.equals("http_get")) {
      fc =
          FunctionCallback.builder()
              .description("发起 HTTP GET 请求(受域名白名单限制)")
              .method("httpGet", String.class)
              .name("http_get")
              .targetObject(tools)
              .build();
    } else {
      fc =
          FunctionCallback.builder()
              .description("发起 HTTP POST 请求(受域名白名单限制)")
              .method("httpPost", String.class, String.class)
              .name("http_post")
              .targetObject(tools)
              .build();
    }
    ToolRegistry registry = new ToolRegistry();
    registry.register(new AnnotatedToolAdapter(fc));
    return registry.find(name).orElseThrow().execute(inputJson);
  }
}
