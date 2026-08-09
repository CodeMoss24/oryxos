package com.oryxos.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.ToolTestFixture;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shell 工具 harness:正常能跑通(白名单内命令)+ 越界会被拦(白名单外命令)。 沙箱为真实 WhitelistSandbox(命令白名单 = [echo, ls])。 */
class ShellToolsTest {

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
  @DisplayName("shell:正常能跑通")
  void shellWorks() {
    ToolResult r = execute("{\"command\":\"echo hello\"}");
    assertTrue(r.success(), () -> "expected success but got: " + r.errorMessage());
    assertTrue(r.content().contains("hello"));
  }

  @Test
  @DisplayName("shell:越界会被拦")
  void shellBlocked() {
    ToolResult r = execute("{\"command\":\"rm -rf /\"}");
    assertFalse(r.success(), "白名单外命令必须失败");
    assertTrue(
        r.errorMessage().contains("not allowed"), () -> "错误信息应含拦截说明, got: " + r.errorMessage());
  }

  private static ToolResult execute(String inputJson) {
    OryxTool tool =
        ToolTestFixture.registry()
            .find("shell")
            .orElseThrow(() -> new AssertionError("tool not registered: shell"));
    return tool.execute(inputJson);
  }
}
