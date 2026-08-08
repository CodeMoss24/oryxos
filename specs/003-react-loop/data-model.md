# 数据模型：ReAct 循环引擎

**日期**: 2026-08-08 | **关联计划**: [plan.md](./plan.md)

## 新增表: tool_invocations

`tool_invocations` 表是 OryxOS 审计体系的核心表之一（与 16 节的 `llm_calls` 配对），记录每次工具调用的成败。

### DDL（追加到 schema.sql）

```sql
CREATE TABLE IF NOT EXISTS tool_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    input_json TEXT,
    result_json TEXT,
    success INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_ms BIGINT NOT NULL,
    created_at TEXT NOT NULL
);
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键，自增 |
| `session_id` | TEXT | 关联 Session，标识一次对话 |
| `tool_name` | TEXT | 被调用的工具名称 |
| `input_json` | TEXT | 工具入参（JSON 字符串） |
| `result_json` | TEXT | 工具执行结果（JSON 字符串，成功时填充） |
| `success` | INTEGER | 执行是否成功（0=失败, 1=成功） |
| `error_message` | TEXT | 失败原因（成功时为空） |
| `duration_ms` | BIGINT | 执行耗时（毫秒） |
| `created_at` | TEXT | 创建时间（ISO-8601） |

### 关键约束

- `session_id` 不可为空（每次调用必须关联到一个 Session）
- `success` 默认为 0（失败），强制调用方显式标记成功
- `duration_ms` 不可为空（每次调用必须记录耗时）

## 既有表（本节引用不修改）

### llm_calls（16 节）

已有建表脚本，本节不修改。ProviderService.chat() 已经写入。

### sessions（18 节）

`SessionRepository` 和 `SessionEntity` 已在 oryxos-storage 中定义。本节 AgentService 使用 SessionRepository.save() 持久化 Session，详细的 Session 管理接口（SessionManager）推迟到 18 节。

## JPA 实体映射

### ToolInvocationEntity → tool_invocations

已在 `oryxos-storage/.../entity/ToolInvocationEntity.java` 中定义，无需修改：
- `@Entity` + `@Table(name = "tool_invocations")`
- 主键策略 `GenerationType.IDENTITY` 对应 SQLite AUTOINCREMENT
- `success` 字段使用 `Boolean` 类型（JPA 自动转换 INTEGER 0/1）

### ToolInvocationRepository

已在 `oryxos-storage/.../repository/ToolInvocationRepository.java` 中定义，提供：
- `save(ToolInvocationEntity)` — 继承自 JpaRepository
- `findBySessionId(String)` — 按 Session 查询工具调用历史