package com.oryxos.memory;

import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import org.springframework.stereotype.Component;

/** 把长期记忆暴露给 Agent 调用,包含 save_memory 和 recall_memory 两个内置 Tool, 跟其他内置 Tool 一视同仁注册到 ToolRegistry。 */
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
      return "把内容追加到长期记忆(MEMORY.md)";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"},"
          + "\"scope\":{\"type\":\"string\",\"enum\":[\"CORE\",\"ARCHIVAL\"]}},"
          + "\"required\":[\"content\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      // TODO: 解析 inputJson 拿 content 和 scope。骨架先直接存。
      memoryService.append(inputJson, "ARCHIVAL");
      return ToolResult.success("saved");
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
      String result = memoryService.recallByKeyword(inputJson);
      return ToolResult.success(result == null ? "" : result);
    }
  }
}
