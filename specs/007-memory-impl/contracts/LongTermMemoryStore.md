# Contract: LongTermMemoryStore(可插拔后端接口)

**Package**: `com.oryxos.memory`(oryxos-memory)| **实现**: `MarkdownMemoryStore`(默认)/ `SqliteMemoryStore` / `Mem0MemoryStore`

接口签名不携带任何存储实现细节(不出现"文件""表""白名单"字样)。换后端只改一行配置,门面以上零改动。

## 四条行为契约(三档实现共同遵守)

| # | 契约 | 验证方式 |
|---|---|---|
| 一 | **不缓存**:每次读取都重新读文件/查库/调 API,写入后下一轮立刻可见 | 契约测试:写入后立即 load 与 recall 均命中 |
| 二 | **核心记忆永不被截断**:截断只作用归档区 | 契约测试:归档灌 500 条后,核心区一字不少 |
| 三 | **写核心还是写归档由调用方经 scope 显式指定**,系统不猜 | 契约测试:不同 scope 落不同区块 |
| 四 | **recall 是关键词检索**,不做复杂化 | 契约测试:recall 只搜归档区,核心区不参与 |

## 方法签名

```java
void append(String content, MemoryScope scope);
String load();                                  // 核心区全量 + 归档区截断后
List<String> recallByKeyword(String keyword);   // 只在归档区匹配
```

## 装配(配置键)

`oryxos.memory.backend`: `markdown`(默认,缺省即 markdown)| `sqlite` | `mem0`

三档均以 `@ConditionalOnProperty(name = "oryxos.memory.backend", ...)` 装配,`matchIfMissing = true` 归 Markdown 档(沿现有写法)。

## 测试观测点

- `MemoryStoreContractTest`: 参数化遍历三档(各实现同一套断言,破契约即红)
- `MarkdownMemoryStoreTest`: 字符串截断边界、区块 header 解析、文件不存在初始化
- `SqliteMemoryStoreTest`: 建表能建能读、LIMIT 生效、LIKE 检索
- `Mem0MemoryStoreTest`: mock RestClient——append 请求体带 scope、recall 转发;契约测试用内存假 Mem0 替身
