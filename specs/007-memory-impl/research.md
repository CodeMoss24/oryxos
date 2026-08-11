# Research: Memory 长期记忆实现设计决策

**Date**: 2026-08-11 | **Branch**: `022-lesson22-memory-impl`

## 1. 接口签名对齐(第 21 节评审定死)

- **Decision**: `MemoryService` 按课件定死签名——`buildContext(Session)` / `remember(String content, MemoryScope scope)` / `List<String> recall(String keyword)`;`LongTermMemoryStore.recallByKeyword` 返回 `List<String>`。
- **Rationale**: 课件第 22 节"接口墙焊死"+"对上层的方法签名现在定死";技术方案 §5.1 同口径。现有雏形签名(`loadContext(Profile, Session)` 的 Profile 参数是死参数、scope 用 String 类型不安全、recall 返回单串)是第 16/17 节初始提交的骨架,不在任何前序节"本节交付物"清单中,属本节交付范畴。
- **Alternatives considered**: 保留现有签名仅补实现——被否:签名与课件/技术方案不一致,scope 字符串不类型安全,recall 无法区分"无命中"与"命中多条的合并"。
- **波及面**: `PromptBuilder.buildMemoryBlock`(去 Profile 参数)、`ReActLoop`(仅构造注入)、`MemoryApiController`(适配)、`MemoryServiceImpl`/`MemoryTools`(重写)。

## 2. MemoryScope 枚举的归属

- **Decision**: `MemoryScope` 从 `com.oryxos.memory` **移入** `com.oryxos.core.memory`。
- **Rationale**: 门面接口 `MemoryService` 在 oryxos-core(被 PromptBuilder 依赖),其签名引用 `MemoryScope`;若枚举留在 oryxos-memory 则 core → memory 反向依赖成环。枚举是纯领域概念,归 core 合理(参考 `Session`/`Message` 同处 core.session 包)。
- **Alternatives considered**: 接口签名用 String——被否(见 #1);接口搬去 oryxos-memory——被否(core 的 PromptBuilder 无法依赖 memory)。
- **引用面核实**: `MemoryScope` 仅被 oryxos-memory 内部 4 个文件引用,移动安全。

## 3. SqliteMemoryStore 的数据访问形态

- **Decision**: 走 Spring Data JPA——`MemoryEntryEntity` + `MemoryEntryRepository` 落 `com.oryxos.storage.entity` / `com.oryxos.storage.repository`(boot 的 `@EntityScan`/`@EnableJpaRepositories` 固定扫描范围);`SqliteMemoryStore` 注入 repository。
- **Rationale**: 技术方案 §5.1"复用已有 SQLite"、与 sessions/审计表同口径;现有 `LlmCallEntity`/`SessionEntity` 即此模式;SQLite 方言 `SQLiteDialect` 与 `ddl-auto=none` + `schema.sql` 手工建表已在 storage 模块落地。
- **Alternatives considered**: JdbcTemplate 直连——被否:与既有实体/仓库模式不一致;memory 模块自建 datasource——被否:重复基建。

## 4. 契约测试怎么跑 SQLite 档

- **Decision**: `MemoryStoreContractTest` 用 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `jdbc:sqlite::memory:` 真 SQLite(沿 `SessionManagerTest` 模式),`@MethodSource` 把三档实例统一喂给同一套断言;mem0 档用"内存假 Mem0"替身(行为满足契约,记在 Map)。
- **Rationale**: 课件明文"契约测的是'这一档有没有守规矩',真实 REST 交互由 Mem0MemoryStoreTest 单独 mock 验证";LIMIT/LIKE 行为必须真 SQLite 才能验;storage 测试已证明该模式可跑。
- **注意**: memory 模块 test resources 需一份与 storage 相同的 `application.properties`(datasource/dialect/ddl/init)。

## 5. Mem0 档的 REST 集成与凭证

- **Decision**: `Mem0MemoryStore` 注入 `RestClient.Builder`(与 `ProviderService` 同构),baseUrl 取 `${MEM0_BASE_URL}`、userId 取 `${MEM0_USER_ID:default}` 环境变量占位;`append` 走 POST /v1/memories/ 携带 messages/user_id/metadata.scope;`recallByKeyword` 转发 search。
- **Rationale**: 课件明确"Java 侧走 REST 集成,凭证和地址走环境变量(宪法:不落明文)";端点形态以部署的 Mem0 版本为准,自动化测试不碰真 server(软门禁 #5 场景,本地无第三方 SDK 可核实,测试全部 mock)。
- **Alternatives considered**: Mem0 官方 Java SDK——被否:课件定 REST 集成,且 SDK 本地无法核实。

## 6. MemoryTools 的 inputJson 解析

- **Decision**: 复用 `FileTools.extractField` 轻量 JSON 字段提取(既有同构模式,零新依赖);scope 缺省/不规范一律落 ARCHIVAL;recall 未命中返回"没有找到相关记忆"。
- **Rationale**: 与 NotifyTools/FileTools 现有解析方式一致;课件契约三"缺省 archival"。
- **Alternatives considered**: 引入 Jackson——被否:现有工具均无该依赖,extractField 已够用。

## 7. 依赖核实结果(固定技术栈句要求)

- `spring-web:6.1.14`(RestClient):BOM 锁定,本地 `dependency:get` 解析成功 ✅
- `spring-data-jpa:3.3.5` + `sqlite-jdbc:3.46.1.3` + `hibernate-core:6.5.3.Final`:经 oryxos-storage 传递,已在依赖树 ✅
- `oryxos-memory` 现有 compile 依赖:spring-boot-starter、oryxos-core、snakeyaml、spring-ai-core(经 core 传递)——新增 `oryxos-storage` 与 `spring-web` 两项
