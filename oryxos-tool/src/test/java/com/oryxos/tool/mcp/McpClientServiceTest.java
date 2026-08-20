package com.oryxos.tool.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.mcp.McpServerConfig;
import com.oryxos.core.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MCP 客户端接线 harness:clientFactory 注入假 client(不碰真传输层)。 ①正常:listTools 返回的工具全部注册;②失联:factory 抛异常 →
 * connectAll() 不抛、坏 server 工具不在、 好 server 工具照常在(外部依赖可用性 ≠ 自身可用性);③connect/disconnect/status 运行时状态。
 */
class McpClientServiceTest {

  @TempDir Path dir;

  private McpConfigLoader loaderWith(String yaml) throws IOException {
    Path file = dir.resolve("mcp_servers.yaml");
    Files.writeString(file, yaml);
    return new McpConfigLoader(file);
  }

  private static McpSyncClient goodClient() {
    McpSyncClient client = mock(McpSyncClient.class);
    when(client.listTools())
        .thenReturn(
            new McpSchema.ListToolsResult(
                List.of(
                    new McpSchema.Tool(
                        "good_mcp_tool", "好工具", "{\"type\":\"object\",\"properties\":{}}")),
                null));
    return client;
  }

  @Test
  @DisplayName("坏 server 失联:connectAll 不抛、坏 server 工具不注册、好 server 照常注册")
  void unreachableServerIsIsolated() throws IOException {
    McpConfigLoader loader =
        loaderWith(
            """
            servers:
              - name: good-server
                transport: stdio
                command: good-cmd
              - name: bad-server
                transport: stdio
                command: bad-cmd
            """);
    Function<McpServerConfig, McpSyncClient> factory =
        config -> {
          if ("bad-server".equals(config.name())) {
            throw new IllegalStateException("Connection refused"); // 课件 ConnectException 语义
          }
          return goodClient();
        };
    ToolRegistry registry = new ToolRegistry();

    assertDoesNotThrow(
        () -> new McpClientService(registry, loader, factory).connectAll()); // 外部依赖的可用性不是自己的可用性

    assertTrue(registry.contains("good_mcp_tool")); // 好的 server 照常注册
    assertFalse(registry.contains("bad_mcp_tool"));
  }

  @Test
  @DisplayName("listTools 的每个工具都被包装注册")
  void allListedToolsAreRegistered() throws IOException {
    McpSyncClient client = mock(McpSyncClient.class);
    when(client.listTools())
        .thenReturn(
            new McpSchema.ListToolsResult(
                List.of(
                    new McpSchema.Tool("tool_a", "a", "{}"),
                    new McpSchema.Tool("tool_b", "b", "{}")),
                null));
    McpConfigLoader loader =
        loaderWith("servers:\n  - name: s\n    transport: stdio\n    command: c\n");
    ToolRegistry registry = new ToolRegistry();

    new McpClientService(registry, loader, config -> client).connectAll();

    assertTrue(registry.contains("tool_a"));
    assertTrue(registry.contains("tool_b"));
  }

  @Test
  @DisplayName("配置解析:command 拆分、env 占位、缺文件零 server")
  void configLoaderParsesEntriesAndHandlesMissingFile() throws IOException {
    McpConfigLoader loader =
        loaderWith(
            """
            servers:
              - name: github-mcp
                transport: stdio
                command: npx -y server-github
                env:
                  TOKEN: ${ORYX_TEST_UNSET_ENV}
            """);

    List<McpServerConfig> configs = loader.load();

    assertTrue(configs.get(0).command().startsWith("npx"));
    assertTrue(configs.get(0).env().get("TOKEN").contains("${ORYX_TEST_UNSET_ENV}"), "缺失占位保留原样");
    assertTrue(new McpConfigLoader(dir.resolve("nope.yaml")).load().isEmpty());
  }

  @Test
  @DisplayName("transport 不受支持跳过不注册")
  void unsupportedTransportIsSkipped() throws IOException {
    McpConfigLoader loader =
        loaderWith("servers:\n  - name: ws-server\n    transport: websocket\n    command: c\n");
    ToolRegistry registry = new ToolRegistry();

    new McpClientService(registry, loader, config -> goodClient()).connectAll();

    assertTrue(registry.list().isEmpty());
  }

  @Test
  @DisplayName("旧写法兼容:transport sse 归一到 http、args 并入 command")
  void legacySseAndArgsAreNormalized() throws IOException {
    McpConfigLoader loader =
        loaderWith(
            """
            servers:
              - name: legacy
                transport: sse
                command: python3
                args: ["/srv/echo.py"]
                url: https://mcp.example.com/sse
            """);

    List<McpServerConfig> configs = loader.loadRaw();

    assertTrue(configs.get(0).transport().equals("http"), "sse 应归一为 http");
    assertTrue(configs.get(0).command().equals("python3 /srv/echo.py"), "args 应并入 command");
  }

  @Test
  @DisplayName("connect/disconnect/status:注册后连上、断开后注销且状态回落")
  void connectDisconnectStatusRoundTrip() throws IOException {
    McpConfigLoader loader =
        loaderWith("servers:\n  - name: s\n    transport: stdio\n    command: c\n");
    ToolRegistry registry = new ToolRegistry();
    McpClientService service = new McpClientService(registry, loader, config -> goodClient());

    service.connectAll();
    assertTrue(service.status("s").connected());
    assertTrue(service.status("s").toolNames().contains("good_mcp_tool"));

    service.disconnect("s");
    assertFalse(registry.contains("good_mcp_tool"), "断开后工具应注销");
    assertFalse(service.status("s").connected(), "断开后状态回落为未连接");
  }
}
