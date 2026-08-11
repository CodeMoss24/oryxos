# Data Model: Memory 长期记忆

**Date**: 2026-08-11 | **Branch**: `022-lesson22-memory-impl`

## 1. SQLite 表:memory_entries(新增,手工建表脚本)

```sql
-- memory_entries:长期记忆条目(手工建表,与 sessions/llm_calls 同口径)
CREATE TABLE IF NOT EXISTS memory_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scope VARCHAR(16) NOT NULL,          -- CORE / ARCHIVAL
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_memory_scope ON memory_entries (scope);
```

- 追加进 `oryxos-storage/src/main/resources/schema.sql`(storage 测试内存库与 boot 文件库共用 `spring.sql.init` 自动执行)
- 实体 `MemoryEntryEntity`(com.oryxos.storage.entity)+ 仓库 `MemoryEntryRepository`(com.oryxos.storage.repository)
- 查询映射(契约四:LIKE 检索;契约二:LIMIT 只加归档):
  - `findByScopeOrderByCreatedAtAsc(scope)` — 核心区全量取(CORE),归档区全量(内部再取最近 N)
  - `findTop100ByScopeOrderByCreatedAtDesc("ARCHIVAL")` — 归档截断 = LIMIT 100
  - `findByScopeAndContentContainingIgnoreCase("ARCHIVAL", keyword)` — LIKE '%keyword%'

## 2. 领域模型

### 长期记忆分区 MemoryScope

| 值 | 语义 | 行为 |
|---|---|---|
| CORE | 核心记忆区 | 全量注入每次上下文,永不截断,不参与检索 |
| ARCHIVAL | 归档记忆区 | 允许截断(阈值内),参与关键词检索 |

### 后端接口语义(三档实现共同遵守)

| 操作 | 语义 | 契约 |
|---|---|---|
| `append(content, scope)` | 追加到指定分区 | 契约三:scope 由调用方显式指定;契约一:直写不缓存 |
| `load()` | 核心区全量 + 归档区截断后 | 契约二:截断物理上碰不到核心区 |
| `recallByKeyword(keyword)` | 只在归档区关键词匹配 | 契约四:不做复杂化 |

### 三档实现形态

| 后端 | 存储形态 | 截断实现 | 检索实现 |
|---|---|---|---|
| Markdown | `.oryxos/memory/MEMORY.md`,`## 核心记忆`/`## 归档记忆` 两区块 | 字符串裁归档段(4000 字符) | 行 contains |
| SQLite | `memory_entries` 表 | 归档 `LIMIT 100` | SQL LIKE |
| Mem0 | 自托管外部服务 | 交给 Mem0(信任其作用域机制) | 语义检索(加强版) |

## 3. 状态流转

- 无生命周期状态机;记忆条目只增不改(append-only),截断只影响读取视图,不删除存储。
- 换后端 = 改 `oryxos.memory.backend` 一行配置,重启装配对应实现,数据形态随后端而变(分区语义不变)。
