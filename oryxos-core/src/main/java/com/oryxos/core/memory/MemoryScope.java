package com.oryxos.core.memory;

/**
 * 长期记忆分区。
 *
 * <ul>
 *   <li>CORE:核心记忆区,全量注入 system prompt,永不截断、不参与检索
 *   <li>ARCHIVAL:归档记忆区,截断 + 关键词检索
 * </ul>
 */
public enum MemoryScope {
  CORE,
  ARCHIVAL
}
