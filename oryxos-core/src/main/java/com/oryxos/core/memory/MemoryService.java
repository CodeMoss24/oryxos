package com.oryxos.core.memory;

import com.oryxos.core.session.Session;
import java.util.List;

/**
 * Memory 统一门面。对上层(PromptBuilder / MemoryTools / ReActLoop)暴露三个固定方法,上层只认 本接口,不感知底层后端(Markdown /
 * SQLite / Mem0,靠 memory.backend 一行配置切换)。
 *
 * <p>会话历史由 PromptBuilder 的会话历史段独立负责;本门面只管长期记忆,避免重复注入。
 */
public interface MemoryService {

  /** 返回长期记忆上下文:核心区全量 + 归档区截断后,供组装 system prompt 注入。 */
  String buildContext(Session session);

  /** 追加一条长期记忆到指定分区。scope 由调用方显式指定,系统不猜。 */
  void remember(String content, MemoryScope scope);

  /** 按关键词检索长期记忆,只在归档区匹配;未命中返回空列表,不抛异常。 */
  List<String> recall(String keyword);
}
