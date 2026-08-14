package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认后端。底层操作 .oryxos/memory/MEMORY.md 一个 Markdown 文件, 按 "## 核心记忆" / "## 归档记忆" 两个 header 分区。
 *
 * <p>零依赖、人可读、git 可跟踪,记忆量不大时的首选。
 *
 * <p>契约落实:load() 每次 Files.readString 不缓存(契约一);truncateIfNeeded 只接收归档区那段文本,
 * 物理上碰不到核心区(契约二);recallByKeyword 只搜归档区,核心记忆本来就永远在场不需要"检索"(契约四)。
 */
@Component
@ConditionalOnProperty(
    name = "oryxos.memory.backend",
    havingValue = "markdown",
    matchIfMissing = true)
public class MarkdownMemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVAL_HEADER = "## 归档记忆";
  private static final int MAX_ARCHIVE_CHARS = 4000; // 阈值只管归档区

  private final Path memoryFile;

  /**
   * 工作区根:优先 oryxos.root 系统属性(27 节 OryxOsRuntime 同口径),回落到 oryxos.workspace, 再默认 ".oryxos"。
   * 嵌套占位符让整机测试只需设一个 oryxos.root 就同时覆盖记忆与其它工作区路径。
   */
  public MarkdownMemoryStore(
      @Value("${oryxos.root:${oryxos.workspace:.oryxos}}") String workspace) {
    this.memoryFile = Path.of(workspace, "memory", "MEMORY.md");
  }

  @Override
  public synchronized void append(String content, MemoryScope scope) {
    try {
      ensureFile();
      String existing = Files.readString(memoryFile);
      String header = scope == MemoryScope.CORE ? CORE_HEADER : ARCHIVAL_HEADER;
      String line = "\n- [" + LocalDate.now() + "] " + content;
      String updated = insertLine(existing, header, line);
      Files.writeString(memoryFile, updated);
    } catch (IOException e) {
      throw new RuntimeException("Failed to append memory", e);
    }
  }

  @Override
  public synchronized String load() {
    try {
      if (!Files.exists(memoryFile)) return "";
      String raw = Files.readString(memoryFile); // 每次重新读——契约一
      String core = extractSection(raw, CORE_HEADER); // 核心区:完整返回
      String archive = truncateIfNeeded(extractSection(raw, ARCHIVAL_HEADER));
      String result = (core + "\n" + archive).trim();
      return result.isEmpty() ? "" : result;
    } catch (IOException e) {
      return "";
    }
  }

  @Override
  public synchronized String readAll() {
    try {
      if (!Files.exists(memoryFile)) return "";
      return Files.readString(memoryFile); // 原样全文,不做分区提取与截断
    } catch (IOException e) {
      return "";
    }
  }

  @Override
  public synchronized List<String> recallByKeyword(String keyword) {
    try {
      if (!Files.exists(memoryFile)) return List.of();
      String archive = extractSection(Files.readString(memoryFile), ARCHIVAL_HEADER);
      return archive.lines().filter(line -> line.contains(keyword)).toList(); // 契约四
    } catch (IOException e) {
      return List.of();
    }
  }

  private void ensureFile() throws IOException {
    Files.createDirectories(memoryFile.getParent());
    if (!Files.exists(memoryFile)) {
      String initial = "# Memory\n\n" + CORE_HEADER + "\n\n" + ARCHIVAL_HEADER + "\n";
      Files.writeString(memoryFile, initial);
    }
  }

  /** 把新行追加到对应区块末尾(时间从上到下,最新在最后;截断保留尾部 = 保留最近——契约二)。 */
  private String insertLine(String content, String header, String line) {
    int idx = content.indexOf(header);
    if (idx < 0) {
      return content + "\n" + header + "\n" + line + "\n";
    }
    int start = idx + header.length();
    int nextHeader = content.indexOf("\n## ", start);
    int end = nextHeader < 0 ? content.length() : nextHeader;
    return content.substring(0, end) + line + content.substring(end);
  }

  private String extractSection(String content, String header) {
    int idx = content.indexOf(header);
    if (idx < 0) return "";
    int start = idx + header.length();
    int nextHeader = content.indexOf("\n## ", start);
    return nextHeader < 0 ? content.substring(start) : content.substring(start, nextHeader);
  }

  /** 只裁归档段——契约二。 */
  private String truncateIfNeeded(String archive) {
    if (archive.length() <= MAX_ARCHIVE_CHARS) return archive;
    return archive.substring(archive.length() - MAX_ARCHIVE_CHARS);
  }
}
