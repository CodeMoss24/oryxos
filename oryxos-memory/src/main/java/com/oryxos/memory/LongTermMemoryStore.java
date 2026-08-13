package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import java.util.List;

/**
 * 长期记忆后端接口(可插拔)。把"长期记忆的读写契约"和"具体存哪、怎么存"解耦。接口签名不携带任何 存储实现细节(不出现"文件""表""白名单"等字样)。
 *
 * <p>四条行为契约(三档实现共同遵守):
 *
 * <ol>
 *   <li>不缓存(每次重新读文件/查库/调 API),写入后下一轮立刻可见
 *   <li>核心记忆区永不被截断,截断只作用在归档区
 *   <li>写核心还是写归档由调用方经 scope 显式指定,系统不猜
 *   <li>recall 是关键词检索不做复杂化(行包含匹配 / LIKE / 服务自带检索)
 * </ol>
 *
 * <p>三档实现:MarkdownMemoryStore(默认) / SqliteMemoryStore / Mem0MemoryStore,靠配置 memory.backend
 * 选一个,换后端只改一行配置,门面以上零改动。
 */
public interface LongTermMemoryStore {

  /** 写入,按 scope 分区。 */
  void append(String content, MemoryScope scope);

  /** 返回核心记忆区全量 + 归档记忆区截断后的内容。核心区永远完整不截断。 */
  String load();

  /** 返回长期记忆完整数据(原样读取、不截断),供管理台等运维查看入口使用;不影响 load 的注入视图。 */
  String readAll();

  /** 按关键词检索,只在归档记忆区做匹配,核心区不参与检索(它本来就会被全量注入)。 */
  List<String> recallByKeyword(String keyword);
}
