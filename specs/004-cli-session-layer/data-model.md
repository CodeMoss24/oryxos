# 数据模型：Session 会话层

## sessions 表（本节交付）

手工建表脚本 DDL（补进 `oryxos-storage/src/main/resources/schema.sql`）：

```sql
CREATE TABLE IF NOT EXISTS sessions (
    session_id     TEXT PRIMARY KEY,        -- channel + user + profile 联合生成，仅 SessionManager 内部拼接
    profile_name   TEXT NOT NULL,
    channel        TEXT NOT NULL,
    user_id        TEXT NOT NULL,
    messages_json  TEXT,                    -- JSON 序列化的对话历史（role/content 列表），整体一列
    status         TEXT NOT NULL DEFAULT 'active',   -- active / archived
    created_at     TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at    TEXT
);
```

与 `SessionEntity`（`oryxos-storage/entity/SessionEntity.java`）字段一一对应，JPA 映射已存在，DDL 补齐后测试环境（`ddl-auto=none` + `spring.sql.init.mode=always`）走手工脚本；生产环境首次建表由 `ddl-auto=update` 兜底（技术方案允许首次 update，演进不依赖）。

## 实体与关系

### Session（core，已有）

- 会话上下文容器，POJO：`sessionId` / `profileName` / `channel` / `userId` / `messages`（`List<Message>`）/ `status` / `createdAt` / `lastActiveAt`。
- `append(Message)` 追加消息并刷新 `lastActiveAt`。

### Message（core，已有）

- record `(role, content)`；role ∈ system / user / assistant / tool。

### SessionEntity（storage，已有）

- `sessions` 表 JPA 映射：`sessionId`（@Id）/ `profileName` / `channel` / `userId` / `messagesJson` / `status` / `createdAt` / `lastActiveAt` / `archivedAt`。

## 关键约束

1. **id 拼接只此一处**：`session_id = channel + user + profile` 的拼接只发生在 `SessionManager` 实现（`JpaSessionManager`）内部；所有入口（CLI 传 `cli`、Web 传 `web`、定时传 `scheduler`）只提供三元组。
2. **幂等**：同一三元组 `getOrCreate` 返回同一个 Session（先查后建）。
3. **隔离**：三元组任一不同 → 不同 Session。
4. **持久化**：对话历史整体 JSON 序列化存 `messages_json` 一列；核心阶段不做按条拆表。
5. **写库时机**：`AgentService.process` 末尾经 `SessionPersistencePort.save`（17 节既有链路）落库；`SessionManager.save` 提供显式写入口（upsert）。

## SessionCodec（storage，本节新建）

`Session ↔ SessionEntity` 转换与 messages_json 序列化/反序列化的唯一实现：

- `SessionEntity toEntity(Session)` —— 序列化 `messages` 为 JSON 数组字符串（`[{"role":"user","content":"..."}, ...]`，escape `\ " \n \r \t`）。
- `Session fromEntity(SessionEntity)` —— 反序列化回 `Session` + `List<Message>`。
- 现有 `SessionPersistenceAdapter` 手写序列化逻辑迁移到此处复用（行为不变），消除两处 JSON 实现。

## 测试数据要点

- `SessionRepositoryTest`：长消息（含引号/换行/反斜杠）写入回读完整——escape 正确性回归。
- 模拟重启：同库新 `@DataJpaTest` context（或同连接第二次 `findById`）历史仍在。
