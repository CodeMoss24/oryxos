# Implementation Plan: Memory 长期记忆实现

**Branch**: `022-lesson22-memory-impl` | **Date**: 2026-08-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-memory-impl/spec.md`

## Summary

第 22 节交付 Memory 核心能力:把现有早期雏形(第 16/17 节初始提交带入)按第 21 节评审定死的接口签名重写补齐——`MemoryService` 门面 + `LongTermMemoryStore` 可插拔后端接口 + 三档实现(Markdown 默认 / SQLite / Mem0 外部集成),`memory.backend` 一行配置切换;`MemoryTools` 两个内置工具暴露给 Agent;`PromptBuilder` 集成点已存在、签名适配。同一套契约测试对三档后端参数化运行,"接口不变、实现随便换"有自动化保障。

## Technical Context

**Language/Version**: JDK 21

**Primary Dependencies**:
- `oryxos-core`(既有,门面接口与领域枚举的宿主,依赖方向:memory → core)
- `oryxos-storage`(**新增依赖**:复用 JPA + SQLite;sqlite-jdbc 3.46.1.3、spring-data-jpa 3.3.5 已在 BOM 锁定)
- `spring-web`(**新增依赖**:RestClient,6.1.14 已在 BOM 锁定,本地可解析)
- spring-boot-starter(既有)

**Storage**: SQLite(手工建表脚本 `oryxos-storage/src/main/resources/schema.sql` 追加 `memory_entries`,`ddl-auto=none`) + `.oryxos/memory/MEMORY.md`(Markdown 档)

**Testing**: spring-boot-starter-test(JUnit 5 + AssertJ + Mockito);`@DataJpaTest` + `AutoConfigureTestDatabase(NONE)` + `jdbc:sqlite::memory:` 真 SQLite(沿 storage 模块既有模式);契约测试参数化遍历三档;Mem0 契约用内存假替身、真实 REST 用 mock RestClient;不碰网络

**Target Platform**: Linux(WSL2)

**Project Type**: Maven 多模块单体(9 模块)

**Performance Goals**: 无硬指标;契约一"不缓存"保证写入后下一轮立即可见

**Constraints**: 宪法——手工建表不依赖 ddl-auto 演进;凭证走环境变量不落明文;不引入 Spring AI 自动 tool 执行;避开 P3C/ASM 解析不了的 Java 18+ 语法形态(构建门禁)

**Scale/Scope**: 核心阶段单实例;记忆量默认 Markdown 档,归档区截断阈值 4000 字符、SQLite 档 100 条

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪法原则 | 本节落实 | 状态 |
|---|---|---|
| I. JDK21+SB3 单体 | 沿用现有 Maven 多模块 | ✅ |
| II. 五大能力优先 | Memory 正是五大能力之一;治理层零引入 | ✅ |
| III. 自实现 ReAct Loop | 本节不触碰 ReActLoop 循环逻辑(仅构造注入签名随接口对齐适配) | ✅ |
| IV. Spring AI 半用 | 本节不引入任何 Spring AI 自动 tool 执行路径;MemoryTools 维持既有 `OryxTool` 接口实现形态,不改成 `@Tool` Bean | ✅ |
| VI. SQLite + MEMORY.md | 三档后端正是该原则的完整落地;手工建表脚本 | ✅ |
| VII. 审计 day one | memory 读写经 ToolExecutor 路径,已有 `tool_invocations` 落库不受影响 | ✅ |
| VIII. 接口先行 | `LongTermMemoryStore` 接口签名不携带存储实现细节;三档实现各写各的 | ✅ |
| 配置/密钥 | Mem0 地址与凭证走 `${MEM0_BASE_URL}` 等环境变量占位 | ✅ |

## Project Structure

### Documentation (this feature)

```text
specs/007-memory-impl/
├── plan.md              # 本文档(/speckit-plan 输出)
├── research.md          # Phase 0 输出:设计决策与依据
├── data-model.md        # Phase 1 输出:memory_entries 表与领域模型
├── quickstart.md        # Phase 1 输出:验证指南
├── contracts/           # Phase 1 输出:接口契约
├── checklists/          # spec 质量清单
└── tasks.md             # Phase 2 输出(/speckit-tasks 生成)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── memory/
│   ├── MemoryService.java      # [修改] 签名对齐:buildContext(Session)/remember(content,scope)/recall(keyword)
│   └── MemoryScope.java        # [移动] 从 oryxos-memory 移入 core(领域枚举,供接口签名引用,避免 core→memory 反向依赖)

oryxos-memory/src/main/java/com/oryxos/memory/
├── LongTermMemoryStore.java    # [修改] recallByKeyword 返回 List<String>
├── MarkdownMemoryStore.java    # [修改] 补 MAX_ARCHIVE_CHARS 截断(只裁归档段)/load 每次重读/recall 返回列表
├── SqliteMemoryStore.java      # [新增] @ConditionalOnProperty(oryxos.memory.backend=sqlite),注入 MemoryEntryRepository
├── Mem0MemoryStore.java        # [新增] @ConditionalOnProperty(oryxos.memory.backend=mem0),RestClient + 环境变量
├── MemoryServiceImpl.java      # [修改] 按新签名实现:buildContext=store.load();remember/recall 转发
└── MemoryTools.java            # [修改] extractField 同构解析 inputJson;scope 缺省/不规范→ARCHIVAL;未命中友好提示

oryxos-memory/src/test/java/com/oryxos/memory/
├── MemoryStoreContractTest.java   # [新增] @DataJpaTest 挂真 SQLite;参数化遍历三档
├── MarkdownMemoryStoreTest.java   # [新增]
├── SqliteMemoryStoreTest.java     # [新增]
├── Mem0MemoryStoreTest.java       # [新增] mock RestClient
├── MemoryToolsTest.java           # [新增]
└── MemoryServiceImplTest.java     # [新增]

oryxos-memory/src/test/resources/
└── application.properties          # [新增] sqlite::memory: + SQLiteDialect + ddl-auto=none + schema.sql(沿 storage 模式)

oryxos-storage/src/main/java/com/oryxos/storage/
├── entity/MemoryEntryEntity.java   # [新增] memory_entries 实体(扫描包固定)
└── repository/MemoryEntryRepository.java  # [新增] findByScope*/LIMIT/LIKE 查询

oryxos-storage/src/main/resources/
└── schema.sql                      # [修改] 追加 memory_entries 表 + idx_memory_scope 索引(手工建表)

oryxos-memory/pom.xml               # [修改] 新增 oryxos-storage + spring-web 依赖

oryxos-core/src/main/java/com/oryxos/core/react/
├── PromptBuilder.java              # [修改] buildMemoryBlock 调 memoryService.buildContext(session)
└── ReActLoop.java                  # [修改] 仅构造注入签名随 MemoryService 对齐(无逻辑改动)

oryxos-web/src/main/java/com/oryxos/web/controller/
└── MemoryApiController.java        # [修改] 适配新签名(buildContext)

oryxos-boot/src/main/resources/
└── application.yaml                # [修改] memory.backend 注释说明(markdown|sqlite|mem0),默认 markdown 不变
```

**Structure Decision**: 沿用既有 9 模块结构。契约测试放 oryxos-memory 模块(与实现同模块),JPA 实体/仓库按 `OryxOsApplication` 的 `@EntityScan("com.oryxos.storage.entity")` / `@EnableJpaRepositories("com.oryxos.storage.repository")` 扫描范围落 oryxos-storage。

## Complexity Tracking

无宪法违规,不需要证明项。
