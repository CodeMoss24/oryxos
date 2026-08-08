package com.oryxos.tool.builtin;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** 文件操作内置 Tool:read_file / write_file / list_dir。 执行前调用 Sandbox.enforce(...) 做路径白名单检查。 */
@Component
public class FileTools {

  private final Sandbox sandbox;

  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  @Component("read_file")
  public class ReadFileTool implements OryxTool {
    @Override
    public String getName() {
      return "read_file";
    }

    @Override
    public String getDescription() {
      return "读取文件内容";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = extractField(inputJson, "path");
      try {
        sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, path));
        return ToolResult.success(Files.readString(Path.of(path)));
      } catch (SandboxViolationException | IOException e) {
        return ToolResult.failure(e.getMessage(), false);
      }
    }
  }

  @Component("write_file")
  public class WriteFileTool implements OryxTool {
    @Override
    public String getName() {
      return "write_file";
    }

    @Override
    public String getDescription() {
      return "写入文件内容";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},"
          + "\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = extractField(inputJson, "path");
      String content = extractField(inputJson, "content");
      try {
        sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_WRITE, path));
        Files.writeString(Path.of(path), content);
        return ToolResult.success("written");
      } catch (SandboxViolationException | IOException e) {
        return ToolResult.failure(e.getMessage(), false);
      }
    }
  }

  @Component("list_dir")
  public class ListDirTool implements OryxTool {
    @Override
    public String getName() {
      return "list_dir";
    }

    @Override
    public String getDescription() {
      return "列出目录内容";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = extractField(inputJson, "path");
      try {
        sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, path));
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.list(Path.of(path))) {
          stream.forEach(p -> sb.append(p.getFileName()).append("\n"));
        }
        return ToolResult.success(sb.toString());
      } catch (SandboxViolationException | IOException e) {
        return ToolResult.failure(e.getMessage(), false);
      }
    }
  }

  /** 极简 JSON 字段提取(避免引入 JSON 库,核心阶段骨架够用) */
  public static String extractField(String json, String field) {
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
