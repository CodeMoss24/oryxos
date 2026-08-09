package com.oryxos.tool.mcp;

import com.oryxos.core.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * MCP server 的连接维护和工具注册(Plugin Tool 方式二)。启动时读 mcp_servers.yaml,逐个 server: connect → initialize →
 * tools/list,把每个 MCP 工具包成 McpToolAdapter 注册进 ToolRegistry。
 *
 * <p>失联隔离:单个 server 连接/注册失败只记 WARN 跳过,不拖垮自身启动,其余 server 照常注册 (外部依赖可用性 ≠ 自身可用性)。配置缺失/文件不存在时安全跳过。
 */
@Component
public class McpClientService {

  private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

  private final ToolRegistry toolRegistry;
  private final Path mcpConfigPath;

  public McpClientService(
      ToolRegistry toolRegistry, @Value("${oryxos.workspace:.oryxos}") String workspace) {
    this.toolRegistry = toolRegistry;
    this.mcpConfigPath = Path.of(workspace, "mcp_servers.yaml");
  }

  @PostConstruct
  public void init() {
    connectAll();
  }

  /** 逐个 server 连接注册;单点失败只 WARN,继续下一个。测试可直接调用验证失联隔离。 */
  public void connectAll() {
    List<McpServerConfig> configs = loadConfigs();
    for (McpServerConfig cfg : configs) {
      try {
        connectAndRegister(cfg);
      } catch (Exception e) {
        log.warn("MCP server [{}] 连接/注册失败,跳过: {}", cfg.name(), e.getMessage());
      }
    }
  }

  /** 读 mcp_servers.yaml → 顶层兼容 servers: 列表与直接列表 → 容错解析(protected 测试缝:子类可注入固定配置)。 */
  protected List<McpServerConfig> loadConfigs() {
    if (!Files.exists(mcpConfigPath)) {
      log.info("No mcp_servers.yaml found at {}, skipping MCP client init", mcpConfigPath);
      return List.of();
    }
    try {
      Object loaded = new Yaml().load(Files.readString(mcpConfigPath));
      if (loaded == null) {
        return List.of();
      }
      Object rawList = loaded;
      if (loaded instanceof Map<?, ?> map && map.containsKey("servers")) {
        rawList = map.get("servers");
      }
      if (!(rawList instanceof List<?> list)) {
        log.warn("mcp_servers.yaml 顶层既不是列表也不是 servers: 列表,跳过 MCP 注册");
        return List.of();
      }
      List<Map<String, Object>> items = new ArrayList<>();
      for (Object o : list) {
        if (o instanceof Map<?, ?> m) {
          Map<String, Object> item = new LinkedHashMap<>();
          m.forEach((k, v) -> item.put(String.valueOf(k), v));
          items.add(item);
        }
      }
      return McpServerConfig.parse(items);
    } catch (Exception e) {
      log.warn("mcp_servers.yaml 解析失败,跳过 MCP 注册: {}", e.getMessage());
      return List.of();
    }
  }

  private void connectAndRegister(McpServerConfig cfg) {
    McpSyncClient client = connect(cfg);
    client.initialize();
    List<McpSchema.Tool> tools = client.listTools().tools();
    if (tools == null || tools.isEmpty()) {
      log.info("MCP server [{}] 无可用工具", cfg.name());
      return;
    }
    for (McpSchema.Tool tool : tools) {
      McpToolAdapter adapter = new McpToolAdapter(client, tool);
      toolRegistry.register(adapter);
      log.info("Registered MCP tool: {} from server [{}]", adapter.getName(), cfg.name());
    }
  }

  /** 按配置建传输层连接:stdio → 本地进程;sse → 远程 HTTP 端点。(protected 测试缝:测试子类注入假 client。) */
  protected McpSyncClient connect(McpServerConfig cfg) {
    if ("stdio".equals(cfg.transport())) {
      ServerParameters params =
          ServerParameters.builder(cfg.command()).args(cfg.args()).env(cfg.env()).build();
      return McpClient.sync(new StdioClientTransport(params)).build();
    }
    return McpClient.sync(new HttpClientSseClientTransport(cfg.url())).build();
  }
}
