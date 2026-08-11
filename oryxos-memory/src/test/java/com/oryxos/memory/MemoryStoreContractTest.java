package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 契约测试:同一套断言对三档后端参数化遍历——写后立读(契约一)、截断只裁归档核心区一字不动(契约二)、 scope 路由(契约三)、recall
 * 只搜归档区(契约四)。任何一档破契约,这里立刻红。
 *
 * <p>参数化列表:markdown(真文件)/ sqlite(真 SQLite,US2 加入)/ mem0(内存假替身,替掉 REST)。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 方法工厂需访问实例字段(@TempDir / @Autowired)
@ContextConfiguration(classes = MemoryTestApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("MemoryStoreContractTest — 同一套契约对三档后端参数化")
class MemoryStoreContractTest {

  @Autowired private MemoryEntryRepository repository;

  Stream<Arguments> allStores() throws IOException {
    // 每个参数化测试方法独立临时目录,消除跨方法文件状态依赖
    Path mdDir = Files.createTempDirectory("memory-contract-md-");
    return Stream.of(
        Arguments.of(new MarkdownMemoryStore(mdDir.toString())),
        Arguments.of(new SqliteMemoryStore(repository)), // 真 SQLite:contract 语义由 SQL 结构保证
        Arguments.of(new FakeMem0MemoryStore()));
  }

  @ParameterizedTest
  @MethodSource("allStores")
  @DisplayName("截断只裁归档区_核心记忆一字不能少")
  void truncateOnlyAffectsArchive_coreIntact(LongTermMemoryStore memory) {
    memory.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 500; i++) {
      memory.append("归档流水 " + i, MemoryScope.ARCHIVAL); // 灌到远超阈值
    }

    String loaded = memory.load();

    assertThat(loaded).contains("用户叫小王，偏好用 Java"); // 核心区完整——"始终在场"的底线
    assertThat(loaded).doesNotContain("归档流水 0"); // 归档区最早的被裁掉
    assertThat(loaded).contains("归档流水 499"); // 保留的是最近的
  }

  @ParameterizedTest
  @MethodSource("allStores")
  @DisplayName("写入后立刻可读_不允许有缓存")
  void writtenMemoryVisibleImmediately_noCache(LongTermMemoryStore memory) {
    memory.append("刚记的事", MemoryScope.ARCHIVAL);

    assertThat(memory.load().contains("刚记的事")).isTrue(); // 下一次 load 立即可见
    assertThat(memory.recallByKeyword("刚记的事")).isNotEmpty(); // 检索同样立即命中
  }

  @ParameterizedTest
  @MethodSource("allStores")
  @DisplayName("scope路由到正确分区_核心区内容不进检索结果")
  void scopeRoutesToCorrectSection_coreNotInRecall(LongTermMemoryStore memory) {
    memory.append("核心区独有内容", MemoryScope.CORE);
    memory.append("归档区内容", MemoryScope.ARCHIVAL);

    // 核心区全量在场,不需要检索——recall 只命中归档区
    assertThat(memory.recallByKeyword("核心区独有内容")).isEmpty();
    assertThat(memory.recallByKeyword("归档区内容")).isNotEmpty();
  }
}
