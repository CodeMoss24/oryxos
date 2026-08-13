package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.session.Session;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * MemoryService 统一门面实现。实现很薄:buildContext 取长期记忆(核心区全量 + 归档区截断后),remember / recall 直接转发给
 * LongTermMemoryStore。真正可换的是 LongTermMemoryStore 这一层。
 *
 * <p>会话历史段由 PromptBuilder 的会话历史消息独立负责,本实现不拼接,避免重复注入。
 */
@Service
public class MemoryServiceImpl implements MemoryService {

  private final LongTermMemoryStore longTermMemoryStore;

  public MemoryServiceImpl(LongTermMemoryStore longTermMemoryStore) {
    this.longTermMemoryStore = longTermMemoryStore;
  }

  @Override
  public String buildContext(Session session) {
    return longTermMemoryStore.load();
  }

  @Override
  public String readAll() {
    return longTermMemoryStore.readAll();
  }

  @Override
  public void remember(String content, MemoryScope scope) {
    MemoryScope effective = scope == null ? MemoryScope.ARCHIVAL : scope; // 缺省写归档——契约三
    longTermMemoryStore.append(content, effective);
  }

  @Override
  public List<String> recall(String keyword) {
    return longTermMemoryStore.recallByKeyword(keyword);
  }
}
