package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.storage.repository.MemoryEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/** SQLite 档特有测试:手工建表脚本能建能读、归档 LIMIT 生效、LIKE 检索。 */
@DataJpaTest
@ContextConfiguration(classes = MemoryTestApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SqliteMemoryStore — 建表能建能读/LIMIT/LIKE")
class SqliteMemoryStoreTest {

  @Autowired private MemoryEntryRepository repository;

  private SqliteMemoryStore newStore() {
    return new SqliteMemoryStore(repository);
  }

  @Test
  @DisplayName("手工建表脚本建出的memory_entries能存能读")
  void schemaBasedTableCanSaveAndLoad() {
    var store = newStore();
    store.append("第一条", MemoryScope.ARCHIVAL);

    assertThat(store.load()).contains("第一条");
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("归档区超过100条_LIMIT只留最近_核心区全量")
  void archiveOverLimitKeepsRecentOnly_coreAlwaysFull() {
    var store = newStore();
    store.append("核心记忆内容", MemoryScope.CORE);
    for (int i = 0; i < 120; i++) {
      store.append("归档流水 " + i, MemoryScope.ARCHIVAL);
    }

    String loaded = store.load();

    assertThat(loaded).contains("核心记忆内容"); // 核心区全量——契约二
    assertThat(loaded).contains("归档流水 119"); // 保留最近
    assertThat(loaded).doesNotContain("归档流水 0"); // LIMIT 裁掉最早的
    assertThat(loaded).doesNotContain("归档流水 19");
  }

  @Test
  @DisplayName("LIKE检索命中包含关键词的归档条目")
  void likeSearchFindsArchivalMatches() {
    var store = newStore();
    store.append("偏好 Java", MemoryScope.ARCHIVAL);
    store.append("偏好 Python", MemoryScope.ARCHIVAL);
    store.append("用户叫小王", MemoryScope.CORE);

    assertThat(store.recallByKeyword("Java")).containsExactly("偏好 Java");
    assertThat(store.recallByKeyword("偏好")).containsExactlyInAnyOrder("偏好 Java", "偏好 Python");
    // 核心区不参与检索
    assertThat(store.recallByKeyword("小王")).isEmpty();
    // 未命中返回空列表不抛异常
    assertThat(store.recallByKeyword("航天")).isEmpty();
  }
}
