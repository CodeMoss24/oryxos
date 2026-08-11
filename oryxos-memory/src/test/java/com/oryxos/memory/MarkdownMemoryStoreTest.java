package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Markdown 档特有测试:字符串截断的边界、区块 header 解析、文件不存在初始化。 */
@DisplayName("MarkdownMemoryStore — 截断边界/区块解析/文件不存在初始化")
class MarkdownMemoryStoreTest {

  @TempDir Path tempDir;

  private MarkdownMemoryStore newStore() {
    return new MarkdownMemoryStore(tempDir.toString());
  }

  @Test
  @DisplayName("文件不存在时append自动建目录建文件并写入")
  void appendWhenFileMissingCreatesFileAndWrites() throws Exception {
    var store = newStore();
    store.append("第一条", MemoryScope.ARCHIVAL);

    Path memoryFile = tempDir.resolve("memory").resolve("MEMORY.md");
    assertThat(memoryFile).exists();
    String content = Files.readString(memoryFile);
    assertThat(content).contains("## 核心记忆").contains("## 归档记忆").contains("第一条");
  }

  @Test
  @DisplayName("文件不存在时load返回空串不抛异常")
  void loadWhenFileMissingReturnsEmpty() {
    var store = newStore();
    assertThat(store.load()).isEmpty();
    assertThat(store.recallByKeyword("任何")).isEmpty();
  }

  @Test
  @DisplayName("归档区超4000字符_字符串截断只裁归档段_核心一字不少")
  void archiveExceedsThreshold_truncatesOnlyArchiveAndKeepsCoreIntact() {
    var store = newStore();
    store.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    String longLine = "a".repeat(5000); // 单行超阈值,截断后整条被裁
    store.append(longLine, MemoryScope.ARCHIVAL);
    store.append("保留的最近一条", MemoryScope.ARCHIVAL);

    String loaded = store.load();

    assertThat(loaded).contains("用户叫小王，偏好用 Java"); // 核心区完整——契约二
    assertThat(loaded).contains("保留的最近一条"); // 保留的是最近的
    assertThat(loaded).doesNotContain("a".repeat(4000)); // 最早的部分被裁掉
  }

  @Test
  @DisplayName("归档区未超阈值_完整保留不截断")
  void archiveUnderThresholdKeptUntruncated() {
    var store = newStore();
    store.append("短记忆", MemoryScope.ARCHIVAL);
    assertThat(store.load()).contains("短记忆");
  }

  @Test
  @DisplayName("记忆内容含分区header字样_按header定位不受影响")
  void contentMentioningHeaderDoesNotBreakSectionParsing() {
    var store = newStore();
    store.append("提到 ## 归档记忆 字样", MemoryScope.CORE);
    store.append("提到 ## 核心记忆 字样", MemoryScope.ARCHIVAL);

    String loaded = store.load();
    assertThat(loaded).contains("提到 ## 归档记忆 字样").contains("提到 ## 核心记忆 字样");

    // recall 只搜归档区:核心区内容不参与检索,归档区内容正常命中(整行含日期前缀)
    assertThat(store.recallByKeyword("提到 ## 归档记忆")).isEmpty();
    assertThat(store.recallByKeyword("提到 ## 核心记忆"))
        .hasSize(1)
        .allMatch(line -> line.contains("提到 ## 核心记忆 字样"));
  }

  @Test
  @DisplayName("检索为简单包含匹配_区分大小写")
  void recallIsCaseSensitiveContainsMatch() {
    var store = newStore();
    store.append("偏好 Java", MemoryScope.ARCHIVAL);
    assertThat(store.recallByKeyword("Java")).hasSize(1).allMatch(line -> line.contains("偏好 Java"));
    assertThat(store.recallByKeyword("java")).isEmpty();
  }
}
