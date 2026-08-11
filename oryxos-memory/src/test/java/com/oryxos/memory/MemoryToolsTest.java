package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 工具测试:save_memory / recall_memory 对 Agent 暴露的读写形态——scope 缺省落归档、未命中返回友好 提示不抛异常。工具只认 MemoryService
 * 门面,不感知底层后端。
 */
@DisplayName("MemoryTools — save_memory/recall_memory 读写形态")
class MemoryToolsTest {

  private final MemoryService memoryService = mock(MemoryService.class);
  private final MemoryTools tools = new MemoryTools(memoryService);
  private final MemoryTools.SaveMemoryTool saveMemoryTool = tools.new SaveMemoryTool();
  private final MemoryTools.RecallMemoryTool recallMemoryTool = tools.new RecallMemoryTool();

  @Test
  @DisplayName("save_memory未带scope落归档并返回成功确认")
  void saveWithoutScopeDefaultsToArchival() {
    ToolResult result = saveMemoryTool.execute("{\"content\":\"用户偏好 Java\"}");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("已记住");
    verify(memoryService).remember("用户偏好 Java", MemoryScope.ARCHIVAL); // 契约三:缺省归档
  }

  @Test
  @DisplayName("save_memory显式scope=CORE落核心区")
  void saveWithExplicitCoreScope() {
    saveMemoryTool.execute("{\"content\":\"用户叫小王\",\"scope\":\"CORE\"}");

    verify(memoryService).remember("用户叫小王", MemoryScope.CORE);
  }

  @Test
  @DisplayName("save_memory的scope取值不规范按归档兜底_不抛异常")
  void saveWithInvalidScopeFallsBackToArchival() {
    saveMemoryTool.execute("{\"content\":\"x\",\"scope\":\"RANDOM\"}");

    verify(memoryService).remember("x", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("recall_memory未命中返回'没有找到相关记忆'且不抛异常")
  void recallMissReturnsFriendlyMessage() {
    when(memoryService.recall("航天")).thenReturn(List.of());

    ToolResult result = recallMemoryTool.execute("{\"query\":\"航天\"}");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("没有找到相关记忆");
  }

  @Test
  @DisplayName("recall_memory命中返回记忆内容")
  void recallHitReturnsContent() {
    when(memoryService.recall("Java")).thenReturn(List.of("偏好 Java", "写过 Java 项目"));

    ToolResult result = recallMemoryTool.execute("{\"query\":\"Java\"}");

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("偏好 Java\n写过 Java 项目");
  }
}
