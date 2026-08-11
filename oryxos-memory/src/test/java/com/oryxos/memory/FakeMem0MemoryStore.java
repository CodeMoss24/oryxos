package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import java.util.ArrayList;
import java.util.List;

/**
 * 契约测试用的"内存假 Mem0"替身:记在 Map/List 里、行为满足四条契约,替掉真实 REST 调用。
 *
 * <p>契约测的是"这一档有没有守规矩",真实 REST 交互由 Mem0MemoryStoreTest 单独 mock 验证(课件口径)。
 */
class FakeMem0MemoryStore implements LongTermMemoryStore {

  private static final int MAX_ARCHIVE_ROWS = 100; // 模拟 Mem0 归档区只带最近若干

  private final List<String> core = new ArrayList<>();
  private final List<String> archival = new ArrayList<>();

  @Override
  public void append(String content, MemoryScope scope) {
    (scope == MemoryScope.CORE ? core : archival).add(content);
  }

  @Override
  public String load() {
    String coreBlock = String.join("\n", core); // 核心区全量——契约二
    List<String> recent =
        archival.size() > MAX_ARCHIVE_ROWS
            ? archival.subList(archival.size() - MAX_ARCHIVE_ROWS, archival.size())
            : archival;
    return (coreBlock + "\n" + String.join("\n", recent)).trim();
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    return archival.stream().filter(line -> line.contains(keyword)).toList(); // 契约四
  }
}
