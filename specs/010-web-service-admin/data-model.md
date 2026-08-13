# 数据模型:Web Service 与第一版管理平台

本节**不新增数据表、不改既有表结构**。全部端点复用前序节的持久化成果;本节只新增 Web 层的请求/响应视图类型与三个接口的增量方法。

## 既有实体(复用,不修改)

### Session(18 节交付,oryxos-core)
- `sessionId`: channel + user + profile 联合拼接,**只在 SessionManager 实现内拼接**(H4 ④)
- `messages`: 对话历史;`GET /sessions/{id}` 只取末尾 100 条
- `status`: `active` / `archived`(DELETE 端点置 archived 后落库)
- 持久化: `sessions` 表(SQLite,手工建表脚本,18 节已交付)

### 审计(16/17 节交付,不触碰)
- `llm_calls` / `tool_invocations`:Web 触发与 CLI 触发走同一 `AgentService.process` 链路,审计照记,本节不新增写入路径。

## 接口增量方法(前序接口扩展,已逐项用户确认)

| 接口 | 新增方法 | 语义 | 实现 |
|---|---|---|---|
| `ProviderPort`(core) | `Map<String, Boolean> connectivity()` | 各 provider 连通/断开 | `ProviderService`(provider):短超时 RestClient 探测 base-url |
| `MemoryService`(core) | `String readAll()` | 记忆完整数据,不截断 | 转发 `LongTermMemoryStore.readAll()` |
| `LongTermMemoryStore`(memory) | `String readAll()` | 原样读取,不截断 | Markdown=文件原文;SQLite=全量 entries;Mem0=远端全量 |
| `SessionManager`(core) | `List<Session> listAll()` | 全部会话,last_active 倒序 | `JpaSessionManager`(storage) |

> 既有方法(`chat`/`buildContext`/`load`/`getOrCreate`/`get`/`save`)契约一律不动。

## 新增 Web 层视图类型(oryxos-web)

### 统一信封(既有,复用不动)
```
ApiResponse<T> { int code; String message; T data; Instant timestamp; }
```
- 成功:`ApiResponse.ok(data)` → code=200
- 错误:code 取 HTTP 状态语义值(400/404/500/503/504);message 除 500 外取异常信息,500 统一话术"内部错误"
- `ErrorCode.INTERNAL_ERROR` 的 message 字面量改"内部错误"(本节唯一既有字面量修改,经用户确认)

### 请求/响应记录(课件命名)
| 类型 | 字段 | 用途 |
|---|---|---|
| `MessageRequest` | `String content` | `POST /sessions/{id}/messages` 请求体 |
| `MessageResponse` | `String reply` | 该端点响应 data |

其余端点沿用既有 Map 形态(创建会话返回 `{session_id}`、invoke 返回 `{reply}`、历史返回 role/content 列表、查询端点返回各自结构),不新增公开类型。

### 会话列表视图(第 11 个端点)
`GET /api/v1/sessions` → 会话概要数组,每项含:`session_id`、`profile_name`、`channel`、`user_id`、`status`、`last_active_at`。来源 `SessionManager.listAll()`。

### 领域异常(oryxos-web/exception,课件草图命名)
| 异常 | 映射 | 抛出点 |
|---|---|---|
| `InvalidRequestException` | 400 | 消息空/超 32KB、profile_name 缺失 |
| `SessionNotFoundException` | 404 | 会话不存在(发消息/查历史/归档) |
| `ResourceNotFoundException` | 404 | Agent 名不存在(invoke 前校验) |
| `ProviderUnavailableException` | 503 | Provider 故障(预留,本期无抛出点,映射先行) |
| `AgentTimeoutException` | 504 | invoke 超 60 秒(超时包装层) |

## 状态流转

```
Session: (getOrCreate) → active ──(DELETE /sessions/{id})──→ archived
```

归档后:不再参与新对话;`GET /sessions/{id}` 与列表仍可见(管理台只读展示)。
