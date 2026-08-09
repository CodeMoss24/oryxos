package com.oryxos.tool.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MCP 客户端接线 harness:测试子类 override connect() 注入假 client(不碰真传输层)。 ①正常:listTools 返回的工具全部注册;②失联:connect
 * 抛异常 → connectAll() 不抛、坏 server 工具不在、 好 server 工具照常在(外部依赖可用性 ≠ 自身可用性)。
 */
class McpClientServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("正常 server:listTools 返回的工具全部注册为 OryxTool")
  void healthyServerRegistersAllTools() throws Exception {
    ToolRegistry registry = new ToolRegistry();
    McpSyncClient client = mock(McpSyncClient.class);
    when(client.listTools())
        .thenReturn(
            new McpSchema.ListToolsResult(
                List.of(tool("echo", "回显"), tool("random", "随机数")), null));

    FakeMcpClientService service =
        new FakeMcpClientService(registry, List.of(cfg("good-server")), client);

    service.connectAll();

    assertTrue(registry.find("echo").isPresent(), "echo 应注册");
    assertTrue(registry.find("random").isPresent(), "random 应注册");
    assertTrue(registry.find("echo").orElseThrow().getInputSchema().contains("properties"));
  }

  @Test
  @DisplayName("坏 server 失联:connectAll 不抛、坏 server 工具不注册、好 server 照常注册")
  void unreachableServerIsIsolated() throws Exception {
    ToolRegistry registry = new ToolRegistry();
    McpSyncClient goodClient = mock(McpSyncClient.class);
    when(goodClient.listTools())
        .thenReturn(new McpSchema.ListToolsResult(List.of(tool("good-tool", "好工具")), null));

    FakeMcpClientService service =
        new FakeMcpClientService(
            registry, List.of(cfg("bad-server"), cfg("good-server")), goodClient);

    service.connectAll(); // 不抛异常

    assertFalse(registry.find("bad-tool").isPresent(), "失联 server 的工具不得注册");
    assertTrue(registry.find("good-tool").isPresent(), "存活 server 的工具照常注册");
  }

  private static McpSchema.Tool tool(String name, String description) throws Exception {
    return MAPPER.readValue(
        "{\"name\":\""
            + name
            + "\",\"description\":\""
            + description
            + "\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},"
            + "\"required\":[\"x\"]}}",
        McpSchema.Tool.class);
  }

  private static McpServerConfig cfg(String name) {
    return new McpServerConfig(name, "stdio", "python3", List.of(), java.util.Map.of(), null);
  }

  /** 测试子类:固定配置 + 假 connect——好 server 返回 mock client,坏 server 抛连接异常。 */
  static class FakeMcpClientService extends McpClientService {

    private final List<McpServerConfig> configs;
    private final McpSyncClient goodClient;

    FakeMcpClientService(
        ToolRegistry registry, List<McpServerConfig> configs, McpSyncClient goodClient) {
      super(registry, ".");
      this.configs = configs;
      this.goodClient = goodClient;
    }

    @Override
    protected List<McpServerConfig> loadConfigs() {
      return configs;
    }

    @Override
    protected McpSyncClient connect(McpServerConfig cfg) {
      if ("good-server".equals(cfg.name())) {
        return goodClient;
      }
      throw new RuntimeException("connection refused");
    }
  }
}
