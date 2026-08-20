package com.oryxos.memory;

import com.oryxos.core.agent.ToolExecutionContext;
import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.session.Session;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * MemoryService 统一门面实现。实现很薄:buildContext 取长期记忆(核心区全量 + 归档区截断后),remember / recall 直接转发给
 * LongTermMemoryStore。真正可换的是 LongTermMemoryStore 这一层。
 *
 * <p>会话历史段由 PromptBuilder 的会话历史消息独立负责,本实现不拼接,避免重复注入。
 *
 * <p>第 30 节 per-agent 记忆:读路径(buildContext/readAll)不经 ToolExecutor,本实现代理 store 前后临时置入 Agent 名
 * (buildContext 取 session.profileName()、readAll 取入参)再复原;写路径经工具执行,由 ToolExecutor 置入。
 */
@Service
public class MemoryServiceImpl implements MemoryService {

  private final LongTermMemoryStore longTermMemoryStore;

  public MemoryServiceImpl(LongTermMemoryStore longTermMemoryStore) {
    this.longTermMemoryStore = longTermMemoryStore;
  }

  @Override
  public String buildContext(Session session) {
    return withAgentContext(session.getProfileName(), longTermMemoryStore::load);
  }

  @Override
  public String readAll(String agentName) {
    return withAgentContext(agentName, longTermMemoryStore::readAll);
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

  /** 置入 Agent 上下文执行操作,finally 复原现场(不污染同线程的后续调用)。 */
  private String withAgentContext(String agentName, Supplier<String> op) {
    String previous = ToolExecutionContext.get();
    try {
      ToolExecutionContext.set(agentName);
      return op.get();
    } finally {
      if (previous == null) {
        ToolExecutionContext.clear();
      } else {
        ToolExecutionContext.set(previous);
      }
    }
  }
}
