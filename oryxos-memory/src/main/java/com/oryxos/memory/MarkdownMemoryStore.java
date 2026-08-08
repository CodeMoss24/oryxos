package com.oryxos.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认后端。底层操作 .oryxos/memory/MEMORY.md 一个 Markdown 文件, 按 "## 核心记忆" / "## 归档记忆" 两个 header 分区。
 *
 * <p>零依赖、人可读、git 可跟踪,记忆量不大时的首选。
 */
@Component
@ConditionalOnProperty(
    name = "oryxos.memory.backend",
    havingValue = "markdown",
    matchIfMissing = true)
public class MarkdownMemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVAL_HEADER = "## 归档记忆";

  private final Path memoryFile;

  public MarkdownMemoryStore(@Value("${oryxos.workspace:.oryxos}") String workspace) {
    this.memoryFile = Path.of(workspace, "memory", "MEMORY.md");
  }

  @Override
  public synchronized void append(String content, MemoryScope scope) {
    try {
      ensureFile();
      String existing = Files.readString(memoryFile);
      String header = scope == MemoryScope.CORE ? CORE_HEADER : ARCHIVAL_HEADER;
      String updated = ensureSection(existing, header, content);
      Files.writeString(memoryFile, updated);
    } catch (IOException e) {
      throw new RuntimeException("Failed to append memory", e);
    }
  }

  @Override
  public synchronized String load() {
    try {
      if (!Files.exists(memoryFile)) return "";
      return Files.readString(memoryFile);
    } catch (IOException e) {
      return "";
    }
  }

  @Override
  public synchronized String recallByKeyword(String query) {
    String archival = extractSection(load(), ARCHIVAL_HEADER);
    if (archival == null || archival.isBlank()) return "";
    return archival
        .lines()
        .filter(line -> line.toLowerCase().contains(query.toLowerCase()))
        .collect(Collectors.joining("\n"));
  }

  private void ensureFile() throws IOException {
    Files.createDirectories(memoryFile.getParent());
    if (!Files.exists(memoryFile)) {
      String initial = "# Memory\n\n" + CORE_HEADER + "\n\n" + ARCHIVAL_HEADER + "\n";
      Files.writeString(memoryFile, initial);
    }
  }

  private String ensureSection(String content, String header, String newLine) {
    int idx = content.indexOf(header);
    if (idx < 0) {
      return content + "\n" + header + "\n" + newLine + "\n";
    }
    int insertAt = idx + header.length();
    return content.substring(0, insertAt) + "\n" + newLine + content.substring(insertAt);
  }

  private String extractSection(String content, String header) {
    int idx = content.indexOf(header);
    if (idx < 0) return "";
    int start = idx + header.length();
    int nextHeader = content.indexOf("\n## ", start);
    return nextHeader < 0 ? content.substring(start) : content.substring(start, nextHeader);
  }
}
