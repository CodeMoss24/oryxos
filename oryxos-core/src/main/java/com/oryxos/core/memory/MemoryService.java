package com.oryxos.core.memory;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Session;

/**
 * Memory 统一门面。对 ReAct 循环暴露统一的记忆读写接口。 内部把会话记忆委托给 SessionManager(底层 SQLite),把长期记忆委托给
 * LongTermMemory(底层 MEMORY.md)。
 *
 * <p>ReAct 循环组装 prompt 时只调 MemoryService 一个接口拿到完整上下文。
 */
public interface MemoryService {

  /** 加载上下文:会话历史 + 长期记忆。 */
  String loadContext(Profile profile, Session session);

  /**
   * 追加长期记忆。
   *
   * @param scope "CORE" 或 "ARCHIVAL",由 Agent 显式指定
   */
  void append(String content, String scope);

  /** 按关键词检索(只在归档记忆区做匹配)。 */
  String recallByKeyword(String query);
}
