package com.oryxos.core.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 统一管理所有 Tool。启动时通过 Spring 容器扫描所有 OryxTool Bean(内置 Tool 和方式三的 Plugin Tool), 加上 MCP Client
 * 注册的工具(方式二),全部包装成 OryxTool 实例。 Profile 启动 Agent 时按 tools 字段从 Registry 过滤出该 Profile 可用的 Tool 子集。
 */
@Component
public class ToolRegistry {

  private final Map<String, OryxTool> tools = new LinkedHashMap<>();

  public void register(OryxTool tool) {
    tools.put(tool.getName(), tool);
  }

  public Optional<OryxTool> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  public List<OryxTool> list() {
    return new ArrayList<>(tools.values());
  }

  public List<OryxTool> subset(Collection<String> names) {
    return tools.entrySet().stream()
        .filter(e -> names.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }
}
