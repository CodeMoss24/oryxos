package com.oryxos.tool.mcp;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP server 的连接维护和工具注册。OryxOS 启动时连接所有配置的 MCP server,
 * 调 tools/list 拿工具列表,把每个 MCP 工具包装成 OryxTool 注册到 ToolRegistry。
 *
 * <p>核心阶段骨架:解析 mcp_servers.yaml 配置,实际 stdio/SSE 连接待 MCP Java SDK 接入后补全。
 */
@Component
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final ToolRegistry toolRegistry;
    private final Path mcpConfigPath;

    public McpClientService(ToolRegistry toolRegistry,
                            @Value("${oryxos.workspace:.oryxos}") String workspace) {
        this.toolRegistry = toolRegistry;
        this.mcpConfigPath = Path.of(workspace, "mcp_servers.yaml");
    }

    @PostConstruct
    public void init() {
        if (!Files.exists(mcpConfigPath)) {
            log.info("No mcp_servers.yaml found at {}, skipping MCP client init", mcpConfigPath);
            return;
        }
        // TODO: 解析 mcp_servers.yaml,对每个 server 启动 stdio/SSE 连接,
        //   调 tools/list 拿工具列表,用 McpToolAdapter 包装成 OryxTool 注册。
        //   核心阶段骨架先记录日志,实际连接待 MCP Java SDK 接入。
        log.info("MCP client init (stub) — config path: {}", mcpConfigPath);
    }

    public List<OryxTool> listMcpTools() {
        return new ArrayList<>();
    }
}
