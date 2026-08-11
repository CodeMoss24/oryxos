# Contract: MemoryService(统一门面)

**Package**: `com.oryxos.core.memory`(oryxos-core)| **实现**: `com.oryxos.memory.MemoryServiceImpl`(oryxos-memory)

上层(PromptBuilder / MemoryTools / ReActLoop)只认本接口,不感知底层后端。

## 方法契约

### `String buildContext(Session session)`

- **语义**: 返回长期记忆上下文——核心区全量 + 归档区截断后,供组装 system prompt 注入。
- **契约**: 每次调用都实时取(底层后端不缓存);核心记忆必须完整在场。
- **边界**: `session` 为 null 时允许(仅取长期记忆,不读会话历史)。
- **注意**: 会话历史段由 PromptBuilder 的会话历史消息独立负责,本方法**不**拼接会话历史,避免重复注入。

### `void remember(String content, MemoryScope scope)`

- **语义**: 追加一条长期记忆到指定分区。
- **契约**: scope 由调用方显式指定(系统不猜);content 为空串时允许(写空行),不抛异常。
- **实现转发**: 直接转发给 `LongTermMemoryStore.append`。

### `List<String> recall(String keyword)`

- **语义**: 按关键词检索长期记忆,只在归档区匹配。
- **契约**: 关键词简单包含匹配;未命中返回空列表,不抛异常。
- **实现转发**: 转发给 `LongTermMemoryStore.recallByKeyword`。

## 调用方适配点(本节签名对齐)

| 调用方 | 改动 |
|---|---|
| `PromptBuilder.buildMemoryBlock(Profile, Session)` | 改为 `memoryService.buildContext(session)`,去掉 Profile 参数 |
| `ReActLoop` | 构造注入签名随接口变化,无逻辑改动 |
| `MemoryApiController.get()` | 改为 `memoryService.buildContext(null)`(仅取长期记忆) |
