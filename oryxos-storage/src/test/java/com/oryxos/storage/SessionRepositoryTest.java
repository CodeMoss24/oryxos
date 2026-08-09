package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = StorageTestApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SessionRepository — sessions 表手工建表脚本验证")
class SessionRepositoryTest {

  @Autowired private SessionRepository repository;

  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("手工建表脚本建出的 sessions 表能存能读")
  void schemaBasedTableCanSaveAndLoad() {
    var entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("wang");
    entity.setMessagesJson("[{\"role\":\"user\",\"content\":\"hi\"}]");
    entity.setStatus("active");
    entity.setCreatedAt(Instant.parse("2026-08-09T10:00:00Z"));
    entity.setLastActiveAt(Instant.parse("2026-08-09T10:01:00Z"));

    var saved = repository.save(entity);
    assertThat(saved.getSessionId()).isEqualTo("cli:wang:default");

    var found = repository.findById("cli:wang:default").orElseThrow();
    assertThat(found.getProfileName()).isEqualTo("default");
    assertThat(found.getChannel()).isEqualTo("cli");
    assertThat(found.getUserId()).isEqualTo("wang");
    assertThat(found.getMessagesJson()).isEqualTo("[{\"role\":\"user\",\"content\":\"hi\"}]");
    assertThat(found.getStatus()).isEqualTo("active");
    assertThat(found.getCreatedAt()).isEqualTo(Instant.parse("2026-08-09T10:00:00Z"));
    assertThat(found.getLastActiveAt()).isEqualTo(Instant.parse("2026-08-09T10:01:00Z"));
  }

  @Test
  @DisplayName("messages_json 含引号换行反斜杠的长消息回读完整")
  void messagesJsonWithSpecialCharsRoundTrips() {
    var entity = new SessionEntity();
    entity.setSessionId("cli:li:default");
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("li");
    String payload =
        "[{\"role\":\"user\",\"content\":\"他说:\\\"你好\\\"\\n第二行\\\\路径\"},"
            + "{\"role\":\"assistant\",\"content\":\"ok\"}]";
    entity.setMessagesJson(payload);
    entity.setStatus("active");
    entity.setCreatedAt(Instant.now());
    entity.setLastActiveAt(Instant.now());

    repository.save(entity);

    var found = repository.findById("cli:li:default").orElseThrow();
    assertThat(found.getMessagesJson()).isEqualTo(payload);
  }

  @Test
  @DisplayName("模拟重启_同库新上下文重查历史还在")
  void historySurvivesRestart() {
    var entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("wang");
    entity.setMessagesJson("[{\"role\":\"user\",\"content\":\"早上好\"}]");
    entity.setStatus("active");
    entity.setCreatedAt(Instant.now());
    entity.setLastActiveAt(Instant.now());

    repository.save(entity);
    // 清空持久化上下文,模拟进程重启后从磁盘重新读
    entityManager.flush();
    entityManager.clear();

    var found = repository.findById("cli:wang:default").orElseThrow();
    assertThat(found.getMessagesJson()).contains("早上好");
  }
}
