# Contract: MemoryTools(内置工具)

**Package**: `com.oryxos.memory`(oryxos-memory)| **注册**: 与内置 Tool 一视同仁进入 ToolRegistry(沿既有 `OryxTool` 接口实现形态,不引入 Spring AI 自动执行)

## save_memory

- **输入 JSON**: `{"content": "...", "scope": "CORE|ARCHIVAL"}`
- **scope 缺省/不规范**: 一律落 ARCHIVAL(契约三缺省值,不抛异常)
- **输出**: 成功确认(写入经 MemoryService.remember → 三档后端共用,工具对后端无感知)

## recall_memory

- **输入 JSON**: `{"query": "..."}`
- **命中**: 返回命中的归档记忆行
- **未命中**: 返回"没有找到相关记忆",不抛异常

## 实现同构

inputJson 解析复用 `FileTools.extractField` 轻量字段提取(与 NotifyTools 一致,零新依赖)。

## 测试观测点

- `MemoryToolsTest`: scope 缺省写归档;未命中返回"没有找到相关记忆"而不抛异常
