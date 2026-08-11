package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把长期记忆暴露给 Agent 调用,包含 save_memory 和 recall_memory 两个内置 Tool, 跟其他内置 Tool 一视同仁注册到 ToolRegistry(不引入
 * Spring AI 自动 tool 执行)。
 *
 * <p>工具只认 MemoryService,对底层是哪档后端完全无感——换后端不碰工具。
 */
@Component
public class MemoryTools {

  private final MemoryService memoryService;

  public MemoryTools(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  @Component("save_memory")
  public class SaveMemoryTool implements OryxTool {
    @Override
    public String getName() {
      return "save_memory";
    }

    @Override
    public String getDescription() {
      return "记住一件值得长期记住的事,写入长期记忆";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"},"
          + "\"scope\":{\"type\":\"string\",\"enum\":[\"CORE\",\"ARCHIVAL\"]}},"
          + "\"required\":[\"content\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String content = extractField(inputJson, "content");
      String scope = extractField(inputJson, "scope");
      // 契约三:scope 缺省或取值不规范一律落归档,不抛异常
      MemoryScope effective =
          "CORE".equalsIgnoreCase(scope) ? MemoryScope.CORE : MemoryScope.ARCHIVAL;
      memoryService.remember(content, effective);
      return ToolResult.success("已记住");
    }
  }

  @Component("recall_memory")
  public class RecallMemoryTool implements OryxTool {
    @Override
    public String getName() {
      return "recall_memory";
    }

    @Override
    public String getDescription() {
      return "按关键词检索长期记忆";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
          + "\"required\":[\"query\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String keyword = extractField(inputJson, "query");
      List<String> hits = memoryService.recall(keyword);
      // 未命中返回友好提示而不是抛异常,不打断对话
      return ToolResult.success(hits.isEmpty() ? "没有找到相关记忆" : String.join("\n", hits));
    }
  }

  /**
   * 极简 JSON 字段提取(避免引入 JSON 库)。与 FileTools.extractField 同构——工具层 inputJson 解析 统一用轻量字段提取,零新依赖。跨模块不直接复用
   * FileTools(oryxos-memory 不依赖 oryxos-tool)。
   */
  private static String extractField(String json, String field) {
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return "";
    int colon = json.indexOf(':', idx);
    int start = colon + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
    if (start >= json.length()) return "";
    char ch = json.charAt(start);
    if (ch == '"') {
      int end = json.indexOf('"', start + 1);
      return end > 0 ? json.substring(start + 1, end) : "";
    }
    int end = start;
    while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
    return json.substring(start, end).trim();
  }
}
