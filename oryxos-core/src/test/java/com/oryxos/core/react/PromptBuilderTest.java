package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

  @Mock private ContextLoader contextLoader;
  @Mock private MemoryService memoryService;
  @Mock private ToolRegistry toolRegistry;

  private PromptBuilder builder;
  private Profile profile;

  @BeforeEach
  void setUp() {
    builder = new PromptBuilder(contextLoader, memoryService, toolRegistry);
    profile = new Profile();
    profile.setName("test");
    profile.getSettings().setMaxHistoryTurns(20);
  }

  @Test
  @DisplayName("历史超N轮被截断")
  void truncatesHistoryWhenExceedsMaxTurns() {
    Session session = new Session("s-1", "test", "cli", "u1");
    for (int i = 0; i < 30; i++) {
      session.append(Message.user("msg" + i));
      session.append(Message.assistant("reply" + i));
    }
    assertEquals(60, session.getMessages().size());

    List<Message> result = builder.truncateHistory(session, 20);

    assertEquals(20, result.size());
  }

  @Test
  @DisplayName("历史不足N轮时全部保留")
  void preservesAllHistoryWhenUnderLimit() {
    Session session = new Session("s-1", "test", "cli", "u1");
    session.append(Message.user("hello"));
    session.append(Message.assistant("hi"));

    List<Message> result = builder.truncateHistory(session, 20);

    assertEquals(2, result.size());
  }

  @Test
  @DisplayName("system prompt末尾含当前日期时间")
  void systemPromptContainsCurrentDateTime() {
    when(contextLoader.loadSystemPrompt(profile, null))
        .thenReturn("system prompt\n当前时间: 2026-08-08T10:30:00");

    String result = builder.buildSystemPrompt(profile, null);

    assertTrue(result.contains("当前时间"));
  }

  @Test
  @DisplayName("assembleMessages 按 system → memory → tools → history 顺序组装")
  void assembleMessagesCorrectOrder() {
    List<Message> history = List.of(Message.user("hello"));

    List<Message> messages =
        builder.assembleMessages(
            "system\n包含当前时间:2026-01-01", "core memories", "可用工具:\n- http_get", history);

    assertEquals(2, messages.size());
    Message systemMsg = messages.get(0);
    assertEquals("system", systemMsg.role());
    assertTrue(systemMsg.content().contains("system"));
    assertTrue(systemMsg.content().contains("core memories"));
    assertTrue(systemMsg.content().contains("http_get"));
    assertTrue(systemMsg.content().contains("当前时间"));
    assertEquals(history.get(0), messages.get(1));
  }
}
