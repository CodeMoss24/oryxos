package com.oryxos.tool.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.ToolTestFixture;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ask_user harness:拿得到回答原样回传、拿不到抛异常绝不静默—— ③ 走真实 fixture 链路(ask_user 工具经 adapter
 * 执行,UnsupportedUserInteraction 抛异常映射为 failure 含原因,模型能看到失败而不卡死)。
 */
class InteractionToolsTest {

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
  @DisplayName("ask_user:mock 回答原样回传")
  void answerReturnedVerbatim() {
    UserInteraction interaction = mock(UserInteraction.class);
    when(interaction.ask("继续吗?")).thenReturn("继续");
    InteractionTools tools = new InteractionTools(interaction);
    assertEquals("继续", tools.askUser("继续吗?"), "拿到回答必须原样回传");
  }

  @Test
  @DisplayName("ask_user:拿不到回答抛异常,绝不静默")
  void failureThrowsInsteadOfSilence() {
    UserInteraction interaction = mock(UserInteraction.class);
    when(interaction.ask("继续吗?")).thenThrow(new RuntimeException("no one to ask"));
    InteractionTools tools = new InteractionTools(interaction);
    RuntimeException e = assertThrows(RuntimeException.class, () -> tools.askUser("继续吗?"), "必须抛异常");
    assertTrue(e.getMessage().contains("no one to ask"), () -> "got: " + e.getMessage());
  }

  @Test
  @DisplayName("ask_user:无人值守环境(真实链路)→ failure 含'不支持交互'原因")
  void unsupportedEnvironmentFailsWithReason() {
    OryxTool tool =
        ToolTestFixture.registry()
            .find("ask_user")
            .orElseThrow(() -> new AssertionError("tool not registered: ask_user"));
    ToolResult r = tool.execute("{\"question\":\"继续吗?\"}");
    assertFalse(r.success(), "无人值守环境必须失败,不能静默");
    assertTrue(r.errorMessage().contains("不支持用户交互"), () -> "got: " + r.errorMessage());
  }
}
