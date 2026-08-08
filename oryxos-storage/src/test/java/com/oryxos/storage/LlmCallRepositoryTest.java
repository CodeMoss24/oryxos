package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = StorageTestApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LlmCallRepository — llm_calls 表手工建表脚本验证")
class LlmCallRepositoryTest {

  @Autowired private LlmCallRepository repository;

  @Test
  @DisplayName("手工建表脚本建出的 llm_calls 能存能读")
  void schemaBasedTableCanSaveAndLoad() {
    var entity = new LlmCallEntity();
    entity.setSessionId("s-1");
    entity.setProvider("deepseek");
    entity.setModel("deepseek-chat");
    entity.setPromptTokens(100);
    entity.setCompletionTokens(50);
    entity.setTotalTokens(150);
    entity.setDurationMs(1234L);
    entity.setSuccess(true);
    entity.setCreatedAt(Instant.now());

    var saved = repository.save(entity);
    assertThat(saved.getId()).isNotNull();

    var found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSessionId()).isEqualTo("s-1");
    assertThat(found.getProvider()).isEqualTo("deepseek");
    assertThat(found.getModel()).isEqualTo("deepseek-chat");
    assertThat(found.getPromptTokens()).isEqualTo(100);
    assertThat(found.getCompletionTokens()).isEqualTo(50);
    assertThat(found.getTotalTokens()).isEqualTo(150);
    assertThat(found.getDurationMs()).isEqualTo(1234L);
  }

  @Test
  @DisplayName("success 列真实存在——成功记录")
  void successColumnPresentTrue() {
    var entity = new LlmCallEntity();
    entity.setSessionId("s-2");
    entity.setProvider("kimi");
    entity.setModel("moonshot-v1");
    entity.setDurationMs(500L);
    entity.setSuccess(true);
    entity.setCreatedAt(Instant.now());

    var saved = repository.save(entity);
    assertThat(saved.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("success 列真实存在——失败记录")
  void successColumnPresentFalse() {
    var entity = new LlmCallEntity();
    entity.setSessionId("s-3");
    entity.setProvider("deepseek");
    entity.setModel("deepseek-chat");
    entity.setDurationMs(2000L);
    entity.setSuccess(false);
    entity.setErrorMessage("connect timeout");
    entity.setCreatedAt(Instant.now());

    var saved = repository.save(entity);
    assertThat(saved.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("error_message 列真实存在")
  void errorMessageColumnPresent() {
    var entity = new LlmCallEntity();
    entity.setSessionId("s-4");
    entity.setProvider("deepseek");
    entity.setModel("deepseek-chat");
    entity.setDurationMs(1000L);
    entity.setSuccess(false);
    entity.setErrorMessage("rate limit exceeded");
    entity.setCreatedAt(Instant.now());

    var saved = repository.save(entity);
    assertThat(saved.getErrorMessage()).isEqualTo("rate limit exceeded");
  }

  @Test
  @DisplayName("按 sessionId 查询")
  void findBySessionId() {
    var e1 = new LlmCallEntity();
    e1.setSessionId("s-find");
    e1.setProvider("deepseek");
    e1.setModel("deepseek-chat");
    e1.setDurationMs(100L);
    e1.setSuccess(true);
    e1.setCreatedAt(Instant.now());
    repository.save(e1);

    var found = repository.findBySessionId("s-find");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).getSessionId()).isEqualTo("s-find");
  }
}
