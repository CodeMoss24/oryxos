# Memory

`MemoryService` 统一门面，对 ReAct 循环暴露一个接口。

## 三层架构

| 层 | 作用 | 存储 | 特点 |
|----|------|------|------|
| 会话历史 | 当前对话上下文 | Session 对象 | 按 `maxHistoryTurns` 截断 |
| 核心记忆 | 永久关键信息 | `## 核心记忆` | 全量注入 system prompt，永不断、不参与检索 |
| 归档记忆 | 历史知识 | `## 归档记忆` | 关键词检索 + 截断 |

## 显式 scope

`save_memory(content, scope)` 的 `scope` 由 Agent 显式指定：

- `CORE`：核心记忆，全量注入、永不截断、不参与检索
- `ARCHIVAL`：归档记忆，截断 + 关键词检索

系统不猜 scope，Agent 必须明确指定。

## 三档后端

| 后端 | 配置值 | 说明 |
|------|--------|------|
| MarkdownMemoryStore | `markdown`（默认） | 读写 `MEMORY.md` 文件 |
| SqliteMemoryStore | `sqlite` | 嵌入式 SQLite |
| Mem0MemoryStore | `mem0` | 云端 Mem0 服务 |

切换后端只改配置，上层代码不动。
