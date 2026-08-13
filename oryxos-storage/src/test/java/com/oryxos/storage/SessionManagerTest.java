package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.session.Message;
import com.oryxos.core.session.SessionManager;
import com.oryxos.storage.session.JpaSessionManager;
import com.oryxos.storage.session.SessionCodec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = StorageTestApplication.class)
@Import({JpaSessionManager.class, SessionCodec.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SessionManager — 会话幂等/隔离/id 拼接唯一出口")
class SessionManagerTest {

  @Autowired private SessionManager sessionManager;

  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void sameTripleAlwaysReturnsSameSession() {
    var first = sessionManager.getOrCreate("cli", "wang", "default");
    var second = sessionManager.getOrCreate("cli", "wang", "default");
    assertThat(second.getSessionId()).isEqualTo(first.getSessionId());

    var other = sessionManager.getOrCreate("web", "wang", "default");
    assertThat(other.getSessionId()).isNotEqualTo(first.getSessionId());
  }

  @Test
  @DisplayName("user不同_则是不同会话")
  void differentUserGivesDifferentSession() {
    var first = sessionManager.getOrCreate("cli", "wang", "default");
    var second = sessionManager.getOrCreate("cli", "zhang", "default");
    assertThat(second.getSessionId()).isNotEqualTo(first.getSessionId());
  }

  @Test
  @DisplayName("profile不同_则是不同会话")
  void differentProfileGivesDifferentSession() {
    var first = sessionManager.getOrCreate("cli", "wang", "default");
    var second = sessionManager.getOrCreate("cli", "wang", "weather");
    assertThat(second.getSessionId()).isNotEqualTo(first.getSessionId());
  }

  @Test
  @DisplayName("getOrCreate幂等_且带回已持久化的历史")
  void getOrCreateBringsBackPersistedHistory() {
    var session = sessionManager.getOrCreate("cli", "wang", "default");
    session.append(Message.user("你好"));
    sessionManager.save(session);

    var again = sessionManager.getOrCreate("cli", "wang", "default");
    assertThat(again.getSessionId()).isEqualTo(session.getSessionId());
    assertThat(again.getMessages()).hasSize(1);
    assertThat(again.getMessages().get(0).role()).isEqualTo("user");
    assertThat(again.getMessages().get(0).content()).isEqualTo("你好");
  }

  @Test
  @DisplayName("get未命中_返回空Optional")
  void getMissingReturnsEmpty() {
    assertThat(sessionManager.get("cli:no-such-user:default")).isEmpty();
  }

  @Test
  @DisplayName("listAll_空库返回空列表")
  void listAllReturnsEmptyOnEmptyDatabase() {
    assertThat(sessionManager.listAll()).isEmpty();
  }

  @Test
  @DisplayName("listAll_按lastActiveAt倒序返回全部会话")
  void listAllReturnsSessionsOrderedByLastActiveAtDesc() {
    var older = sessionManager.getOrCreate("cli", "wang", "default");
    var newer = sessionManager.getOrCreate("web", "li", "weather");
    // 显式钉死时间,不依赖创建时序的时钟精度
    older.setLastActiveAt(java.time.Instant.parse("2026-08-13T00:00:00Z"));
    newer.setLastActiveAt(java.time.Instant.parse("2026-08-13T01:00:00Z"));
    sessionManager.save(older);
    sessionManager.save(newer);

    var list = sessionManager.listAll();
    assertThat(list).hasSize(2);
    assertThat(list.get(0).getSessionId()).isEqualTo(newer.getSessionId());
    assertThat(list.get(1).getSessionId()).isEqualTo(older.getSessionId());
  }

  @Test
  @DisplayName("工具调用轮次持久化保真_toolCalls与toolCallId会话恢复后不丢")
  void toolCallsSurvivePersistenceRoundTrip() {
    var session = sessionManager.getOrCreate("cli", "wang", "default");
    session.append(Message.user("现在几点"));
    var call = new com.oryxos.core.react.ToolCall("call-1", "clock", "{}");
    session.append(Message.assistant("", List.of(call)));
    session.append(Message.tool("16:00", "call-1"));
    session.append(Message.assistant("现在是 16:00"));
    sessionManager.save(session);

    var restored = sessionManager.getOrCreate("cli", "wang", "default");
    assertThat(restored.getMessages()).hasSize(4);
    var assistantWithCalls = restored.getMessages().get(1);
    assertThat(assistantWithCalls.toolCalls()).hasSize(1);
    assertThat(assistantWithCalls.toolCalls().get(0).id()).isEqualTo("call-1");
    assertThat(assistantWithCalls.toolCalls().get(0).name()).isEqualTo("clock");
    assertThat(assistantWithCalls.toolCalls().get(0).argumentsJson()).isEqualTo("{}");
    assertThat(restored.getMessages().get(2).toolCallId()).isEqualTo("call-1");
  }
}
