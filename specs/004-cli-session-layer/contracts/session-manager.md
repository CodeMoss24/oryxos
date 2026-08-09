# 契约：SessionManager

接口放 `oryxos-core`（`com.oryxos.core.session.SessionManager`），实现 `JpaSessionManager` 放 `oryxos-storage`。所有入口（CLI / Web / 定时）只提供三元组，不自行拼接 session_id。

```java
public interface SessionManager {

  /** 按 channel+user+profile 获取或创建会话。幂等：同一三元组返回同一个 Session。id 拼接只在此处。 */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按 sessionId 获取会话。不存在返回 Optional.empty()。 */
  Optional<Session> get(String sessionId);

  /** 持久化会话（含对话历史 messages_json）。 */
  void save(Session session);
}
```

## 语义

| 操作 | 行为 |
|------|------|
| `getOrCreate("cli", "wang", "default")` | 已存在同 id → 返回现有（含历史）；不存在 → 新建（status=active）并返回 |
| `getOrCreate` 幂等性 | 同一三元组历次调用返回**同一个** Session（同 id、同历史） |
| 三元组隔离 | channel / user / profile 任一不同 → 不同 session_id（如 `web` vs `cli`） |
| `get(sessionId)` | 命中返回；未命中返回空 Optional |
| `save(session)` | upsert：按 session_id 写库，含 messages_json / status / 时间戳 |

## session_id 公式

`channel + ":" + user + ":" + profileName`（分隔符唯一，拼接只此一处；具体分隔符由实现定，外部不可依赖格式）。

## 调用方（本节）

- `CliChannel`（channel-cli）：`getOrCreate("cli", currentUser(), profileName)`。
- `SessionManagerTest`（storage 测试）：幂等 / 隔离 / 不存在 get 返回空。
