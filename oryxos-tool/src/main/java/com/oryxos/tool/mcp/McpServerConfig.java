package com.oryxos.tool.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单个 MCP server 的声明配置(mcp_servers.yaml 一条)。
 *
 * <p>parse 容错:缺 name/transport、transport 非法、stdio 缺 command、sse 缺 url 的项只记 WARN 跳过—— 声明坏一项不影响其余
 * server 接入(失联隔离的配置侧)。
 */
public record McpServerConfig(
    String name,
    String transport,
    String command,
    List<String> args,
    Map<String, String> env,
    String url) {

  public McpServerConfig {
    // 防御性拷贝:外部传入的 List/Map 不可变,防止调用方事后改配置影响已注册连接
    args = args == null ? List.of() : List.copyOf(args);
    env = env == null ? Map.of() : Map.copyOf(env);
  }

  private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

  public static List<McpServerConfig> parse(List<Map<String, Object>> raw) {
    List<McpServerConfig> result = new ArrayList<>();
    if (raw == null) {
      return result;
    }
    for (Map<String, Object> item : raw) {
      if (item == null) continue;
      String name = asString(item.get("name"));
      String transport = asString(item.get("transport"));
      if (name.isBlank() || transport.isBlank()) {
        log.warn("MCP server 配置缺 name/transport,跳过: {}", item);
        continue;
      }
      if ("stdio".equals(transport)) {
        String command = asString(item.get("command"));
        if (command.isBlank()) {
          log.warn("MCP stdio server [{}] 缺 command,跳过", name);
          continue;
        }
        result.add(
            new McpServerConfig(
                name,
                transport,
                command,
                asStringList(item.get("args")),
                asStringMap(item.get("env")),
                null));
      } else if ("sse".equals(transport)) {
        String url = asString(item.get("url"));
        if (url.isBlank()) {
          log.warn("MCP sse server [{}] 缺 url,跳过", name);
          continue;
        }
        result.add(new McpServerConfig(name, transport, null, List.of(), Map.of(), url));
      } else {
        log.warn("MCP server [{}] transport 非法: {},仅支持 stdio/sse,跳过", name, transport);
      }
    }
    return result;
  }

  private static String asString(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  private static List<String> asStringList(Object o) {
    if (!(o instanceof List<?> list)) return List.of();
    List<String> out = new ArrayList<>();
    for (Object e : list) out.add(String.valueOf(e));
    return out;
  }

  private static Map<String, String> asStringMap(Object o) {
    if (!(o instanceof Map<?, ?> map)) return Map.of();
    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : map.entrySet()) {
      out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
    }
    return out;
  }
}
