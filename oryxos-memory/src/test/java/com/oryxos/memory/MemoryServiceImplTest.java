package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.memory.MemoryScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 门面测试:buildContext 返回长期记忆(核心区全量 + 归档区截断后),会话历史段由 PromptBuilder 的 会话历史消息独立负责,门面不拼接(避免重复注入)。 */
@DisplayName("MemoryServiceImpl — 门面三方法转发与缺省值")
class MemoryServiceImplTest {

  private final LongTermMemoryStore store = mock(LongTermMemoryStore.class);
  private final MemoryServiceImpl service = new MemoryServiceImpl(store);

  @Test
  @DisplayName("buildContext返回store.load内容_不拼会话历史")
  void buildContextDelegatesToStoreLoad() {
    when(store.load()).thenReturn("## 核心记忆\n用户叫小王\n## 归档记忆\n偏好 Java");

    String context = service.buildContext(null);

    assertThat(context).contains("用户叫小王").contains("偏好 Java");
  }

  @Test
  @DisplayName("remember按指定scope转发store")
  void rememberForwardsWithExplicitScope() {
    service.remember("用户叫小王", MemoryScope.CORE);
    verify(store).append("用户叫小王", MemoryScope.CORE);

    service.remember("今天写了代码", MemoryScope.ARCHIVAL);
    verify(store).append("今天写了代码", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("remember的scope为null时缺省落归档_系统不猜")
  void rememberWithNullScopeDefaultsToArchival() {
    service.remember("缺省记忆", null);
    verify(store).append("缺省记忆", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("recall转发store_未命中返回空列表不抛异常")
  void recallForwardsAndReturnsEmptyListOnMiss() {
    when(store.recallByKeyword("航天")).thenReturn(List.of());
    assertThat(service.recall("航天")).isEmpty();

    when(store.recallByKeyword("Java")).thenReturn(List.of("偏好 Java"));
    assertThat(service.recall("Java")).containsExactly("偏好 Java");
    verify(store).recallByKeyword("Java");
  }
}
