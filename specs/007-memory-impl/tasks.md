# Tasks: Memory 长期记忆实现(Lesson 22)

**Input**: Design documents from `/specs/007-memory-impl/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/

**Tests**: 课件"验收 harness"明确要求 6 个测试类(契约测试参数化遍历三档后端),harness 先行——每个 story 的测试任务先于或伴随实现任务。

**Organization**: 按 user story 分组(US1 记住偏好 P1 → US2 切后端 P1 → US3 scope 路由 P2 → US4 工具读写 P2),Setup 与 Foundational 先行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行(不同文件、无依赖)
- **[Story]**: 所属 user story(US1~US4);Setup/Foundational/Polish 阶段不带
- 路径均为精确文件路径

---

## Phase 1: Setup(共享基建)

**Purpose**: 模块依赖与测试基础设施,US 之前必须就位

- [X] T001 在 oryxos-memory/pom.xml 新增依赖:oryxos-storage(复用 JPA + SQLite 能力)与 spring-web(RestClient 供 Mem0 档),版本走父 BOM 锁定
- [X] T002 新建 oryxos-memory/src/test/resources/application.properties,沿 oryxos-storage 测试模式:jdbc:sqlite::memory: + SQLiteDialect + ddl-auto=none + spring.sql.init 执行 schema.sql(契约测试挂真 SQLite 的前提)

**Checkpoint**: 依赖就绪,Foundational 可开工

---

## Phase 2: Foundational(阻塞所有 user story)

**Purpose**: 接口墙焊死——门面签名、后端接口、领域枚举、存储实体,所有 story 的前提

- [X] T003 将 MemoryScope 枚举从 oryxos-memory/src/main/java/com/oryxos/memory/MemoryScope.java 移入 oryxos-core/src/main/java/com/oryxos/core/memory/(门面接口签名引用它,避免 core→memory 反向依赖),更新 oryxos-memory 内引用并删除原文件
- [X] T004 对齐 MemoryService 接口签名(oryxos-core/src/main/java/com/oryxos/core/memory/MemoryService.java):`String buildContext(Session session)`(长期记忆上下文,不拼会话历史)/ `void remember(String content, MemoryScope scope)` / `List<String> recall(String keyword)` 返回列表
- [X] T005 定死 LongTermMemoryStore 接口(oryxos-memory/src/main/java/com/oryxos/memory/LongTermMemoryStore.java):`append(content, scope)` / `load()`(核心全量+归档截断)/ `recallByKeyword(keyword)` 返回 `List<String>`;签名不携带存储实现细节
- [X] T006 存储层落地 memory_entries:oryxos-storage/src/main/resources/schema.sql 追加建表语句(scope VARCHAR(16) NOT NULL / content TEXT NOT NULL / created_at TIMESTAMP NOT NULL + idx_memory_scope 索引,与 sessions 同口径手工建表);新建 MemoryEntryEntity(com.oryxos.storage.entity)与 MemoryEntryRepository(com.oryxos.storage.repository,落在 boot 固定扫描包内,含 findByScope / 归档 LIMIT / LIKE 检索查询)
- [X] T007 [P] 适配 PromptBuilder(oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java):buildMemoryBlock 改为调 memoryService.buildContext(session),去掉 Profile 参数
- [X] T008 [P] 适配 ReActLoop 构造注入(oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java,仅签名随接口对齐、无逻辑改动)与 MemoryApiController(oryxos-web/src/main/java/com/oryxos/web/controller/MemoryApiController.java,改为 buildContext(null) 仅取长期记忆)

**Checkpoint**: 接口墙焊死、编译通过,user story 可开工

---

## Phase 3: User Story 1 - Agent 记住用户偏好并在后续对话自动带上 (Priority: P1) 🎯 MVP

**Goal**: 写入一条记忆后下一次读取上下文立即可见;核心区永不截断、归档区超阈值截断保留最近

**Independent Test**: 契约测试(Markdown 档):写入后立即 load/recall 命中;归档灌 500 条后核心一字不少、归档保留最近

### Tests for User Story 1(harness 先行)⚠️

- [X] T009 [P] [US1] 新建 MarkdownMemoryStoreTest(oryxos-memory/src/test/java/com/oryxos/memory/):字符串截断边界、两区块 header 解析、文件不存在时读写不抛异常正常初始化
- [X] T010 [P] [US1] 新建 MemoryServiceImplTest:buildContext 返回核心区全量 + 归档区截断后,核心记忆完整在内;remember/recall 转发 store;session 为 null 时仅取长期记忆

### Implementation for User Story 1

- [X] T011 [US1] 重写 MarkdownMemoryStore(oryxos-memory/src/main/java/com/oryxos/memory/MarkdownMemoryStore.java):append 按 scope 写入对应区块(带日期列表行)、load 每次 Files.readString 不缓存(契约一)、truncateIfNeeded 只裁归档段(契约二,MAX_ARCHIVE_CHARS=4000)、recallByKeyword 只搜归档区行包含匹配(契约四)、文件不存在自动初始化
- [X] T012 [US1] 重写 MemoryServiceImpl(oryxos-memory/src/main/java/com/oryxos/memory/MemoryServiceImpl.java):buildContext 调 store.load();remember/recall 直接转发;content 空串允许不抛异常
- [X] T013 [US1] 新建 MemoryStoreContractTest(oryxos-memory/src/test/java/com/oryxos/memory/):@DataJpaTest 挂真 SQLite,参数化先行挂 Markdown 档,落地课件两条关键回归——"截断只裁归档区_核心记忆一字不能少"(append CORE + 灌 500 条归档流水 → load 含核心、不含"归档流水 0"、含"归档流水 499")与"写入后立刻可读_不允许有缓存"(append 后 load 立见、recall 立命中);方法名译英文,@DisplayName 保留课件原文

**Checkpoint**: US1 独立可测——Markdown 档写入→下一轮上下文立即可见

---

## Phase 4: User Story 2 - 记忆量增长时只改一行配置切换后端 (Priority: P1)

**Goal**: SQLite 档(LIMIT/LIKE)与 Mem0 档(外部服务)实现,memory.backend 一行切换;同一套契约测试对三档参数化全过

**Independent Test**: 契约测试参数化遍历三档后端(markdown / sqlite / 内存假 mem0)同一套断言全过;SQLite 档真库验证 LIMIT/LIKE

### Tests for User Story 2(harness 先行)⚠️

- [X] T014 [P] [US2] 新建 SqliteMemoryStoreTest:手工建表脚本建出的 memory_entries 能存能读、归档 LIMIT 生效(超 100 条只留最近)、LIKE 检索命中
- [X] T015 [P] [US2] 新建 Mem0MemoryStoreTest:mock RestClient——append 发出的请求体带 scope 进 metadata、recall 转发 search;不碰真 server

### Implementation for User Story 2

- [X] T016 [US2] 新建 SqliteMemoryStore(oryxos-memory/src/main/java/com/oryxos/memory/SqliteMemoryStore.java):注入 MemoryEntryRepository;append 直插、load 核心 WHERE scope='CORE' 全量 + 归档 LIMIT 100、recallByKeyword 走 LIKE(契约四)
- [X] T017 [US2] 新建 Mem0MemoryStore(oryxos-memory/src/main/java/com/oryxos/memory/Mem0MemoryStore.java):注入 RestClient,baseUrl 取 ${MEM0_BASE_URL}、userId 取 ${MEM0_USER_ID:default} 环境变量占位不落明文;append 走 POST /v1/memories/ 带 messages/user_id/metadata.scope、load 按 scope 取、recallByKeyword 转发 search(语义检索加强版);同时提供供契约测试使用的内存假 Mem0 替身(行为满足契约)
- [X] T018 [US2] 装配三档:三个 store 以 @ConditionalOnProperty(name = "oryxos.memory.backend", havingValue = "markdown|sqlite|mem0") 声明,matchIfMissing=true 归 Markdown 档;MemoryServiceImpl 注入 LongTermMemoryStore(换后端只改一行配置)
- [X] T019 [US2] 扩展 MemoryStoreContractTest 为参数化遍历三档(同一 @MethodSource 供三档实例),任何一档破契约立刻红

**Checkpoint**: US1+US2 独立可测——同一套契约三档全过,"接口不变、实现随便换"有自动化保障

---

## Phase 5: User Story 3 - Agent 显式决定记忆归核心还是归档 (Priority: P2)

**Goal**: scope 显式指定(缺省 archival)、系统不猜;recall 只搜归档区、核心区永远完整在场不参与检索

**Independent Test**: 契约测试 scope 路由断言:core 落核心区永不截断、缺省落归档、recall 不命中核心区内容

### Tests for User Story 3(harness 先行)⚠️

- [X] T020 [US3] 在 MemoryStoreContractTest 补 scope 路由断言(三档参数化):CORE 写入后 load 中核心区完整;recallByKeyword 检索核心区独有内容返回空(核心区不参与检索);scope 缺省语义在门面层验证(见 T021)

### Implementation for User Story 3

- [X] T021 [US3] MemoryServiceImpl.remember 缺省值处理:scope 为 null 时按 ARCHIVAL 落(系统不猜的兜底);配合 T020 断言验证

**Checkpoint**: scope 路由契约三档统一验证通过

---

## Phase 6: User Story 4 - Agent 通过工具读写长期记忆,未命中不报错 (Priority: P2)

**Goal**: save_memory / recall_memory 两个内置工具暴露给 Agent,对底层后端无感知;未命中返回友好提示不抛异常

**Independent Test**: MemoryToolsTest:scope 缺省写归档;查询未命中返回"没有找到相关记忆"

### Tests for User Story 4(harness 先行)⚠️

- [X] T022 [P] [US4] 新建 MemoryToolsTest:save_memory 未带 scope 落归档并返回成功确认;recall_memory 未命中返回"没有找到相关记忆"且不抛异常;命中返回内容

### Implementation for User Story 4

- [X] T023 [US4] 重写 MemoryTools(oryxos-memory/src/main/java/com/oryxos/memory/MemoryTools.java):沿用 OryxTool 接口实现形态(不进 ToolRegistry 自动执行路径之外,不引入 Spring AI 自动 tool 执行);inputJson 复用 FileTools.extractField 解析;scope 缺省/不规范一律 ARCHIVAL(不抛异常);recall 未命中返回"没有找到相关记忆"

**Checkpoint**: US4 独立可测——Agent 可经工具读写记忆,未命中不炸对话

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 配置注释、全量门禁与回归

- [X] T024 在 oryxos-boot/src/main/resources/application.yaml 的 memory 段补注释说明(memory.backend 取值 markdown | sqlite | mem0,默认 markdown 不变)
- [X] T025 运行 quickstart 验证:mvn clean verify 全绿(含 P3C/SpotBugs/FindSecBugs/PMD),前序节全部测试回归绿

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup(Phase 1)**: 无依赖,最先
- **Foundational(Phase 2)**: 依赖 Setup,阻塞所有 US
- **US1(Phase 3)**: 依赖 Foundational
- **US2(Phase 4)**: 依赖 Foundational(契约测试扩展依赖 US1 的 T013)
- **US3(Phase 5)**: 依赖 US1(核心/归档实现)+ US2(三档参数化)
- **US4(Phase 6)**: 依赖 US1(MemoryService 门面)
- **Polish(Phase 7)**: 依赖全部 US

### User Story Dependencies

- **US1(P1)**: Foundational 后即可开工(MVP)
- **US2(P1)**: 可在 US1 测试落定后并行推进(不同文件:store 实现 vs 测试)
- **US3(P2)**: 依赖 US1/US2 的契约测试就位
- **US4(P2)**: 依赖 US1 门面

### Within Each User Story

- 测试先于/伴随实现(harness 先行):T009→T011、T010→T012、T014→T016、T015→T017、T022→T023
- 实现完成的定义:该模块 mvn test 全绿(任务级 DoD)

### Parallel Opportunities

- T007/T008(Foundational 适配):不同文件可并行
- T009/T010、T014/T015、T022:同 story 测试类不同文件可并行
- US2 的测试(T014/T015)与实现(T016/T017)流水推进

---

## Parallel Example

```bash
# Foundational 适配并行:
Task: "适配 PromptBuilder 调 buildContext(session)" (T007)
Task: "适配 ReActLoop 注入 + MemoryApiController" (T008)

# US1 测试并行:
Task: "MarkdownMemoryStoreTest 截断边界/header 解析/初始化" (T009)
Task: "MemoryServiceImplTest buildContext 组装" (T010)
```

---

## Implementation Strategy

### MVP First(US1 Only)

1. Setup:依赖 + test properties
2. Foundational:接口墙 + 实体 + 调用方适配
3. US1:Markdown 档全链路(store → service → buildContext 注入)+ 契约测试先行落地
4. STOP and VALIDATE:US1 独立测试全绿(Markdown 档"写入后立读、截断保核心")

### Incremental Delivery

1. US1 完成 → Markdown 档可演示(默认后端)
2. US2 完成 → 契约测试三档参数化全过,换后端一行配置
3. US3/US4 完成 → scope 显式路由 + 工具暴露给 Agent
4. Polish:mvn clean verify 全绿收尾

---

## Notes

- [P] 任务 = 不同文件、无依赖
- 反作弊:不得删断言/@Disabled/放宽阈值;测试红了修实现,认为测试错停下报告
- 软门禁:创建交付物清单外对外概念 / 改已定字面量 / 课件与技术方案冲突 / 改前序公共接口 / 第三方 API 核实不到 / 新增 plan 外依赖 → 停下问用户
- 关键回归测试方法名英文化,@DisplayName 保留课件原文(如"截断只裁归档区_核心记忆一字不能少"→`truncateOnlyAffectsArchive_coreIntact` + @DisplayName)
- 全程不自动 commit / push
