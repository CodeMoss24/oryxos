# Quickstart: Memory 长期记忆验证指南

**Branch**: `022-lesson22-memory-impl`

## 自动化验证(harness 判卷,机器可判的都在这里)

### 1. 全量门禁

```bash
mvn clean verify
```

预期:全部模块 BUILD SUCCESS(含 P3C/SpotBugs/FindSecBugs/PMD 静态门禁),oryxos-memory 六个测试类全绿。

### 2. 只跑本节测试

```bash
mvn test -pl oryxos-memory -am
```

预期:6 个测试类全绿——契约测试对三档统一跑是核心守点:

| 测试类 | 守点 |
|---|---|
| `MemoryStoreContractTest` | 截断只裁归档核心一字不能少 / 写入后立刻可读不允许缓存 / scope 路由 / recall 只搜归档区(三档统一) |
| `MarkdownMemoryStoreTest` | 字符串截断边界、区块 header 解析 |
| `SqliteMemoryStoreTest` | 建表能建能读、LIMIT 生效、LIKE 检索 |
| `Mem0MemoryStoreTest` | append 请求体带 scope、recall 转发(mock,不碰真 server) |
| `MemoryToolsTest` | scope 缺省写归档;未命中"没有找到相关记忆"不抛异常 |
| `MemoryServiceImplTest` | buildContext 核心记忆完整在场;会话历史独立负责不重复 |

### 3. 三档切换验证(自动化面)

契约测试的 `@MethodSource("allStores")` 同时喂 markdown/sqlite/mem0(假替身)三档实例,同一套断言全过 = "换后端不破坏行为契约"的机器证据。

## 人工验证项(harness 判不了,留给你过)

### 4. 三档切换体感验证

`oryxos-boot/src/main/resources/application.yaml` 中 `oryxos.memory.backend` 分别设 `markdown` / `sqlite`,各跑一次对话:

```bash
mvn spring-boot:run -pl oryxos-boot   # 另开终端跑 oryxos chat 或 POST /api/v1/agents/{name}/invoke
```

预期:同一段对话体感一致——`save_memory` 写入、下一轮 `buildContext` 带上。切换只改一行配置。

### 5. Mem0 档真连一次(可选)

部署自托管 Mem0,`memory.backend: mem0`,设 `MEM0_BASE_URL` / `MEM0_USER_ID` 环境变量,验证 `save_memory` 真的进入 Mem0、`recall` 语义召回。依赖真 server,测不了,人工过。

### 6. 真模型完整走一遍

对话里说一句值得记的话 → Agent 主动调 `save_memory` → 开新会话 → 系统提示里带着核心记忆("始终在场"在真实链路里的体感)。

### 7. USER.md 只读确认

code review 确认没有任何代码路径写 `USER.md`;`MEMORY.md`(或 SQLite/Mem0)是唯一可被 Agent 写入的记忆文件。
