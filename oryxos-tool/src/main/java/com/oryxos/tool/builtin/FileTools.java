package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import org.springframework.stereotype.Component;

/**
 * 文件操作内置 Tool:read_file / write_file / list_dir。
 *
 * <p>三个方法都是普通方法,由 ToolConfiguration 用 FunctionCallback.builder().method(...) 装配成工具 (schema
 * 从方法签名自动生成,不再手写)。执行方法第一件事调 Sandbox.enforce 做路径白名单检查;越界抛 SandboxViolationException、IO 失败抛
 * IOException,由 AnnotatedToolAdapter 统一映射为 ToolResult.failure (越界不可重试,与存量语义一致)。
 */
@Component
public class FileTools {

  private final Sandbox sandbox;

  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  public String readFile(String path) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, path));
    return Files.readString(Path.of(path));
  }

  public String writeFile(String path, String content) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_WRITE, path));
    Files.writeString(Path.of(path), content);
    return "written";
  }

  public String listDir(String path) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, path));
    StringBuilder sb = new StringBuilder();
    try (var stream = Files.list(Path.of(path))) {
      stream.forEach(p -> sb.append(p.getFileName()).append("\n"));
    }
    return sb.toString();
  }

  /** 编辑文件:oldText 唯一匹配才替换为 newText 并写回;找不到或出现多次都抛异常、文件一字不动(不落盘)。 */
  public String editFile(String path, String oldText, String newText) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_WRITE, path));
    String content = Files.readString(Path.of(path));
    int count = countOccurrences(content, oldText);
    if (count == 0) {
      throw new IllegalArgumentException("oldText not found: " + oldText);
    }
    if (count > 1) {
      throw new IllegalArgumentException(
          "oldText matches " + count + " times, expected exactly once");
    }
    Files.writeString(Path.of(path), content.replace(oldText, newText));
    return "edited";
  }

  /** 递归按正则搜文件内容,返回 文件:行号:内容;严格 UTF-8 解码失败(二进制/非 UTF-8)的文件跳过不中断;上限 200 条截断注明。 */
  public String grep(String path, String pattern) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, path));
    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
    StringBuilder sb = new StringBuilder();
    int count = 0;
    boolean truncated = false;
    try (var walk = Files.walk(Path.of(path))) {
      for (Path p : walk.filter(Files::isRegularFile).toList()) {
        if (count >= 200) {
          truncated = true;
          break;
        }
        String content;
        try {
          content = Files.readString(p);
        } catch (IOException e) {
          continue; // 非 UTF-8/二进制文件,跳过不中断
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
          if (count >= 200) {
            truncated = true;
            break;
          }
          if (regex.matcher(lines[i]).find()) {
            sb.append(p).append(":").append(i + 1).append(":").append(lines[i]).append("\n");
            count++;
          }
        }
      }
    }
    // 截断标记统一收尾追加一次:单个文件内匹配满 200 条后,外层循环可能已无下一个文件
    if (truncated) {
      sb.append("(truncated at 200 matches)\n");
    }
    return sb.toString();
  }

  /** 按通配模式找路径(PathMatcher glob,如 /tmp/**&#47;*.txt);上限 200 条截断注明。通配符前的路径前缀作为扫描根。 */
  public String glob(String pattern) throws IOException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.FILE_READ, pattern));
    int star = pattern.indexOf('*');
    int question = pattern.indexOf('?');
    int cut = star < 0 ? question : (question < 0 ? star : Math.min(star, question));
    String rootStr = cut < 0 ? pattern : pattern.substring(0, cut);
    while (rootStr.endsWith("/") && rootStr.length() > 1) {
      rootStr = rootStr.substring(0, rootStr.length() - 1);
    }
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    StringBuilder sb = new StringBuilder();
    int count = 0;
    try (var walk = Files.walk(Path.of(rootStr))) {
      for (Path p : walk.toList()) {
        if (count >= 200) {
          sb.append("(truncated at 200 matches)\n");
          break;
        }
        if (matcher.matches(p)) {
          sb.append(p).append("\n");
          count++;
        }
      }
    }
    return sb.toString();
  }

  private static int countOccurrences(String content, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = content.indexOf(needle, idx)) >= 0) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  /** 极简 JSON 字段提取(避免引入 JSON 库)——19 节 NotifyTools 仍在使用,保留为静态工具方法 */
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
