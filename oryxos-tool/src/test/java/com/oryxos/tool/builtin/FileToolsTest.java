package com.oryxos.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.ToolTestFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件工具 harness:read_file / write_file / list_dir 各两条——正常能跑通 + 越界会被拦。 工具经 ToolTestFixture
 * 的真实注册表查找执行,沙箱为真实 WhitelistSandbox(路径白名单 = @TempDir)。
 */
class FileToolsTest {

  @TempDir static Path tempDir;

  @BeforeAll
  static void start() {
    ToolTestFixture.start(tempDir);
  }

  @AfterAll
  static void stop() {
    ToolTestFixture.stop();
  }

  @Test
  @DisplayName("read_file:正常能跑通")
  void readFileWorks() throws Exception {
    Path f = tempDir.resolve("a.txt");
    Files.writeString(f, "hello world");
    ToolResult r = execute("read_file", "{\"path\":\"" + f + "\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertEquals("hello world", r.content());
  }

  @Test
  @DisplayName("read_file:越界会被拦")
  void readFileBlocked() {
    ToolResult r = execute("read_file", "{\"path\":\"/etc/passwd\"}");
    assertBlocked(r);
  }

  @Test
  @DisplayName("write_file:正常能跑通")
  void writeFileWorks() throws Exception {
    Path f = tempDir.resolve("b.txt");
    ToolResult r = execute("write_file", "{\"path\":\"" + f + "\",\"content\":\"data\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertEquals("data", Files.readString(f));
  }

  @Test
  @DisplayName("write_file:越界会被拦")
  void writeFileBlocked() {
    ToolResult r = execute("write_file", "{\"path\":\"/tmp/evil-outside.txt\",\"content\":\"x\"}");
    assertBlocked(r);
    assertFalse(Files.exists(Path.of("/tmp/evil-outside.txt")));
  }

  @Test
  @DisplayName("list_dir:正常能跑通")
  void listDirWorks() throws Exception {
    Files.writeString(tempDir.resolve("a.txt"), "x");
    Files.writeString(tempDir.resolve("b.txt"), "y");
    ToolResult r = execute("list_dir", "{\"path\":\"" + tempDir + "\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(r.content().contains("a.txt"));
    assertTrue(r.content().contains("b.txt"));
  }

  @Test
  @DisplayName("list_dir:越界会被拦")
  void listDirBlocked() {
    ToolResult r = execute("list_dir", "{\"path\":\"/\"}");
    assertBlocked(r);
  }

  @Test
  @DisplayName("edit_file:唯一匹配替换成功,文件其余内容不动")
  void editFileUniqueMatchWorks() throws Exception {
    Path f = tempDir.resolve("edit-a.txt");
    Files.writeString(f, "hello world");
    ToolResult r =
        execute("edit_file", "{\"path\":\"" + f + "\",\"oldText\":\"world\",\"newText\":\"java\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertEquals("hello java", Files.readString(f), "仅替换目标文本,其余内容不动");
  }

  @Test
  @DisplayName("edit_file:找不到 oldText → failure 且文件一字不动")
  void editFileNotFoundLeavesFileUntouched() throws Exception {
    Path f = tempDir.resolve("edit-b.txt");
    Files.writeString(f, "hello world");
    ToolResult r =
        execute(
            "edit_file", "{\"path\":\"" + f + "\",\"oldText\":\"nonexistent\",\"newText\":\"x\"}");
    assertFalse(r.success(), "找不到必须失败");
    assertEquals("hello world", Files.readString(f), "失败时文件一字不动");
  }

  @Test
  @DisplayName("edit_file:出现多次 → failure 且文件一字不动")
  void editFileMultipleMatchesLeaveFileUntouched() throws Exception {
    Path f = tempDir.resolve("edit-c.txt");
    Files.writeString(f, "a a b");
    ToolResult r =
        execute("edit_file", "{\"path\":\"" + f + "\",\"oldText\":\"a\",\"newText\":\"z\"}");
    assertFalse(r.success(), "多次匹配必须失败");
    assertEquals("a a b", Files.readString(f), "失败时文件一字不动");
  }

  @Test
  @DisplayName("grep:文件:行号:内容 格式,命中正确")
  void grepFindsMatchesWithLineNumbers() throws Exception {
    Path src = tempDir.resolve("grep-src");
    Files.createDirectories(src);
    Files.writeString(src.resolve("a.txt"), "alpha beta\nbeta gamma");
    ToolResult r = execute("grep", "{\"path\":\"" + src + "\",\"pattern\":\"beta\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(r.content().contains("a.txt:1:alpha beta"), () -> "got: " + r.content());
    assertTrue(r.content().contains("a.txt:2:beta gamma"), () -> "got: " + r.content());
  }

  @Test
  @DisplayName("grep:超 200 条截断注明")
  void grepTruncatesAt200() throws Exception {
    Path src = tempDir.resolve("grep-many");
    Files.createDirectories(src);
    StringBuilder big = new StringBuilder();
    for (int i = 0; i < 250; i++) big.append("hit line ").append(i).append("\n");
    Files.writeString(src.resolve("big.txt"), big.toString());
    ToolResult r = execute("grep", "{\"path\":\"" + src + "\",\"pattern\":\"hit\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(
        r.content().contains("(truncated at 200 matches)"), () -> "应注明截断, got: " + r.content());
  }

  @Test
  @DisplayName("grep:二进制/非 UTF-8 文件跳过不中断")
  void grepSkipsBinaryFiles() throws Exception {
    Path src = tempDir.resolve("grep-binary");
    Files.createDirectories(src);
    Files.write(src.resolve("binary.dat"), new byte[] {(byte) 0xff, (byte) 0xfe, 0x41});
    Files.writeString(src.resolve("text.txt"), "needle in text");
    ToolResult r = execute("grep", "{\"path\":\"" + src + "\",\"pattern\":\"needle\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(r.content().contains("text.txt:1:needle in text"), "正常文件仍命中");
    assertFalse(r.content().contains("binary.dat"), "二进制文件被跳过");
  }

  @Test
  @DisplayName("glob:通配命中正确")
  void globMatchesPattern() throws Exception {
    Path root = tempDir.resolve("glob-src");
    Files.createDirectories(root.resolve("a/b"));
    Files.writeString(root.resolve("a/b/c.txt"), "x");
    Files.writeString(root.resolve("a/d.java"), "y");
    String pattern = root + "/**/*.txt";
    ToolResult r = execute("glob", "{\"pattern\":\"" + pattern + "\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(r.content().contains("c.txt"), () -> "got: " + r.content());
    assertFalse(r.content().contains("d.java"), () -> "got: " + r.content());
  }

  @Test
  @DisplayName("glob:超 200 条截断注明")
  void globTruncatesAt200() throws Exception {
    Path root = tempDir.resolve("glob-many");
    // Java PathMatcher 的 glob **/ 语义要求至少一层目录,250 个文件放子目录下
    Files.createDirectories(root.resolve("sub"));
    for (int i = 0; i < 250; i++) {
      Files.writeString(root.resolve("sub/f" + i + ".txt"), "x");
    }
    String pattern = root + "/**/*.txt";
    ToolResult r = execute("glob", "{\"pattern\":\"" + pattern + "\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(
        r.content().contains("(truncated at 200 matches)"), () -> "应注明截断, got: " + r.content());
  }

  private static ToolResult execute(String name, String inputJson) {
    OryxTool tool =
        ToolTestFixture.registry()
            .find(name)
            .orElseThrow(() -> new AssertionError("tool not registered: " + name));
    return tool.execute(inputJson);
  }

  private static void assertBlocked(ToolResult r) {
    assertFalse(r.success(), "越界输入必须失败");
    assertTrue(
        r.errorMessage().contains("not allowed"), () -> "错误信息应含拦截说明, got: " + r.errorMessage());
  }
}
