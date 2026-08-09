package com.oryxos.tool;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.oryxos.core.tool.OryxTool;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 契约三件套 harness:参数化测试遍历 ToolTestFixture 真实 ToolRegistry 的每个工具, name/description/inputSchema
 * 都非空——任何一个工具漏实现 getInputSchema 立刻红。 新注册的工具自动纳入(不需要改测试)。
 */
class OryxToolContractTest {

  @TempDir static Path tempDir;

  @BeforeAll
  static void start() {
    ToolTestFixture.start(tempDir);
  }

  @AfterAll
  static void stop() {
    ToolTestFixture.stop();
  }

  @ParameterizedTest(name = "contract of {0}")
  @MethodSource("allRegisteredTools")
  @DisplayName("每个注册工具契约三件套非空")
  void contractTripletNonNull(OryxTool tool) {
    assertNotNull(tool.getName(), "tool name 不能为空");
    assertNotNull(tool.getDescription(), "tool description 不能为空");
    assertNotNull(
        tool.getInputSchema(), "tool inputSchema 不能为空——缺了 Provider 翻译 Function Calling 直接卡死");
  }

  static List<OryxTool> allRegisteredTools() {
    return ToolTestFixture.registry().list();
  }
}
