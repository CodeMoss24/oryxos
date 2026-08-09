package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.session.Message;
import com.oryxos.core.session.SessionManager;
import com.oryxos.storage.session.JpaSessionManager;
import com.oryxos.storage.session.SessionCodec;
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
}
