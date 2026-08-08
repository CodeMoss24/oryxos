package com.oryxos.memory;

/**
 * 长期记忆后端接口(可插拔)。把"长期记忆的读写契约"和"具体存哪、怎么存"解耦。
 *
 * <p>四条行为契约:
 *
 * <ol>
 *   <li>不缓存(每次重新读文件/查库/调 API)
 *   <li>核心记忆区永不被截断,截断只作用在归档区
 *   <li>写核心还是写归档由 Agent 经 scope 显式指定,系统不猜
 *   <li>recall 是关键词检索不做复杂化
 * </ol>
 *
 * <p>三档实现:MarkdownMemoryStore(默认) / SqliteMemoryStore / Mem0MemoryStore, 靠配置 memory.backend
 * 选一个,换后端只改一行配置,上层不动。
 */
public interface LongTermMemoryStore {

  void append(String content, MemoryScope scope);

  /** 返回核心记忆区全量 + 归档记忆区截断后的内容。核心区永远完整不截断。 */
  String load();

  /** 按关键词检索,只在归档记忆区做匹配,核心区不参与检索(它本来就会被全量注入)。 */
  String recallByKeyword(String query);
}
