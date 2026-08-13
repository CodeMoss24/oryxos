# Tasks: Web Service 与第一版管理平台

**Input**: Design documents from `/specs/010-web-service-admin/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/

**Tests**: 本特性明确要求测试(课件验收 harness):测试任务先于对应实现任务落地,先红后绿。

**Organization**: 按 user story 分组,每个 story 可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行(不同文件、无依赖)
- **[Story]**: 所属 user story(US1~US5)
- 描述含精确文件路径

---

## Phase 1: Setup(共享基础设施)

**Purpose**: 依赖与工具链核实(本节无新依赖、无新表、无新模块)

- [X] T001 核实依赖锁定:`mvn dependency:tree -pl oryxos-web,oryxos-boot` 确认 springdoc-openapi-starter-webmvc-ui 2.6.0、spring-ai 1.0.0-M4、spring-boot 3.3.5 均在 BOM(无需新增任何依赖)
- [X] T002 [P] 核实前端工具链:node ≥20、npm registry 连通(`npm ping`),产出记录进 research.md 已核实项

---

## Phase 2: Foundational(阻塞性前提)

**Purpose**: 统一异常出口先行——所有 user story 的错误路径都依赖它

**⚠️ CRITICAL**: 异常映射是全部端点的公共前提,先于任何 story 实现。

- [X] T003 [P] 先写 GlobalExceptionHandlerTest(harness 关键回归):每类异常映射到约定状态码、响应体都是统一 ApiResponse 信封、**500 兜底不泄漏**(课件关键回归测试断言逻辑原样落地:抛未映射 RuntimeException 断言 500 + `$.code`=500 + `$.message`="内部错误" + 响应体不含 `jdbc:sqlite` 等内部细节;方法名英文、课件原文进 @DisplayName)——oryxos-web/src/test/java/com/oryxos/web/GlobalExceptionHandlerTest.java
- [X] T004 [P] 五个领域异常类:SessionNotFoundException、ResourceNotFoundException、ProviderUnavailableException、AgentTimeoutException、InvalidRequestException(extends RuntimeException,课件草图命名)——oryxos-web/src/main/java/com/oryxos/web/exception/
- [X] T005 ErrorCode.INTERNAL_ERROR 话术字面量改"内部错误"(用户确认的既有字面量修改,全部 500 一致)——oryxos-web/src/main/java/com/oryxos/web/ErrorCode.java
- [X] T006 GlobalExceptionHandler 扩展映射(课件草图,经用户确认):SessionNotFound/ResourceNotFound→404、IllegalStateException/ProviderUnavailable→503(既有 IllegalStateException→404 迁改)、AgentTimeout→504、兜底 500 统一话术不泄漏 e.getMessage;既有 IllegalArgumentException→400 保留并扩展 InvalidRequestException→400;404/503/504/400 的 message 取异常信息,500 用统一话术——oryxos-web/src/main/java/com/oryxos/web/GlobalExceptionHandler.java

**Checkpoint**: GlobalExceptionHandlerTest 全绿,错误出口定死。

---

## Phase 3: User Story 1 - 会话管理五端点(含共享引擎)(Priority: P1)🎯 MVP

**Goal**: 创建会话、发消息(与 CLI 同一引擎恰被调一次)、查历史(≤100 条)、归档(落库)、会话列表(第 11 个端点)

**Independent Test**: `mvn test -pl oryxos-web -Dtest=SessionApiControllerTest` 全绿;curl 创建→发消息→查历史→归档→列表链路走通

### Tests for User Story 1

- [X] T007 [P] [US1] 先写 SessionApiControllerTest(@WebMvcTest(SessionApiController),mock AgentService + SessionManager):超 32KB→400 且引擎不被调、空消息→400、会话不存在→404、正常请求 agentService.process 恰被调一次(课件关键回归)、历史只返回最近 100 条、归档置 archived、列表端点返回会话概要——oryxos-web/src/test/java/com/oryxos/web/controller/SessionApiControllerTest.java
- [X] T008 [P] [US1] 扩展 SessionManagerTest(既有类):listAll 按 last_active_at 倒序、空库返回空列表——oryxos-storage/src/test/java/com/oryxos/storage/SessionManagerTest.java

### Implementation for User Story 1

- [X] T009 [US1] SessionManager 接口增量新增 `List<Session> listAll()`(不改既有三方法契约)——oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java;JpaSessionManager 实现(finAll 按 lastActiveAt 倒序)——oryxos-storage/src/main/java/com/oryxos/storage/session/JpaSessionManager.java
- [X] T010 [P] [US1] MessageRequest(content)/MessageResponse(reply) 记录(课件命名)——oryxos-web/src/main/java/com/oryxos/web/dto/
- [X] T011 [US1] SessionApiController 改造:去掉内存 Map 与自拼 session_id,全走 SessionManager(getOrCreate/get/save/listAll);创建端点保留既有字段(profile_name 必填、user_id 默认 anonymous、channel 默认 web,缺失→400);发消息 32KB 校验→InvalidRequestException、会话不存在→SessionNotFoundException、引擎恰调一次;历史取末尾 100 条;归档置 archived 并 save 落库;新增 GET 列表端点——oryxos-web/src/main/java/com/oryxos/web/controller/SessionApiController.java

**Checkpoint**: US1 五端点全绿,会话层与 CLI/调度器共享同一持久化。

---

## Phase 4: User Story 2 - Agent 无状态调用(60s 超时)(Priority: P1)

**Goal**: invoke 走同一引擎、Agent 不存在 404、超 60 秒 504

**Independent Test**: curl invoke 返回真模型回复;不存在的 Agent 名拿 404;60s 超时与断 Provider 属人工故障注入项(课件明示切片测试模拟不了完整链路),504 映射由 GlobalExceptionHandlerTest 覆盖

### Implementation for User Story 2

- [X] T012 [US2] AgentApiController 改造:①调用前用 ProfileRegistry 校验 Agent 存在,不存在抛 ResourceNotFoundException→404;②注入 Spring TaskExecutor(applicationTaskExecutor,虚拟线程),`submit(process).get(60, SECONDS)`,超时 cancel(true) 并抛 AgentTimeoutException→504(不用 CompletableFuture/自建线程池,H4 ⑤);③会话身份走 sessionManager.getOrCreate("web", user_id 默认 anonymous, name),去掉自拼 id——oryxos-web/src/main/java/com/oryxos/web/controller/AgentApiController.java

**Checkpoint**: US1+US2 两个人推入口全部走同一引擎。

---

## Phase 5: User Story 3 - 查询三端点(记忆全文)(Priority: P2)

**Goal**: profiles/tools 列表(既有接线核实)、memory 返回完整数据(readAll 不截断)

**Independent Test**: `mvn test -pl oryxos-memory -Dtest='*MemoryStore*'` 全绿(三档后端契约);curl /memory 返回全文

### Tests for User Story 3

- [X] T013 [P] [US3] 扩展 MemoryStoreContractTest(三后端参数化契约)与各后端测试:readAll 返回完整数据、不截断(Markdown 归档区超 4000 字符仍全量返回)、与 buildContext 注入视图并存互不影响——oryxos-memory/src/test/java/com/oryxos/memory/

### Implementation for User Story 3

- [X] T014 [US3] LongTermMemoryStore 接口增量新增 `String readAll()`——oryxos-memory/src/main/java/com/oryxos/memory/LongTermMemoryStore.java;MarkdownMemoryStore 实现(文件原文,不走 extractSection/truncateIfNeeded)——oryxos-memory/src/main/java/com/oryxos/memory/MarkdownMemoryStore.java
- [X] T015 [P] [US3] SqliteMemoryStore.readAll()(全量 entries 拼装,无截断)——oryxos-memory/src/main/java/com/oryxos/memory/SqliteMemoryStore.java
- [X] T016 [P] [US3] Mem0MemoryStore.readAll()(远端全量读取路径,无本地截断)——oryxos-memory/src/main/java/com/oryxos/memory/Mem0MemoryStore.java
- [X] T017 [US3] MemoryService 接口增量新增 `String readAll()`(不改 buildContext/remember/recall 契约)——oryxos-core/src/main/java/com/oryxos/core/memory/MemoryService.java
- [X] T018 [US3] MemoryApiController 改用 memoryService.readAll() 返回完整数据——oryxos-web/src/main/java/com/oryxos/web/controller/MemoryApiController.java
- [X] T019 [P] [US3] 核实 ProfileApiController/ToolApiController 既有接线满足契约(已注入真实 ProfileRegistry/ToolRegistry,无需改动则记录核实结论)——oryxos-web/src/main/java/com/oryxos/web/controller/

**Checkpoint**: 三个查询端点全绿,管理台数据源就位。

---

## Phase 6: User Story 4 - 系统状态(Provider 连通探活)(Priority: P2)

**Goal**: health 返回 ok;info 带各 Provider 实时连通状态(真实探测、失败安全返回 DOWN)

**Independent Test**: `mvn test -pl oryxos-provider -Dtest=ProviderServiceTest` 全绿;curl /info 返回 providers 连通状态且端点恒 200

### Tests for User Story 4

- [X] T020 [P] [US4] 扩展 ProviderServiceTest:connectivity 对可探测地址返回 true、连接失败/超时返回 false、未配置 provider 不抛异常——oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java

### Implementation for User Story 4

- [X] T021 [US4] ProviderPort 接口增量新增 `Map<String, Boolean> connectivity()`(不改既有 chat 契约)——oryxos-core/src/main/java/com/oryxos/core/react/ProviderPort.java
- [X] T022 [US4] ProviderService.connectivity 实现:对每个 providerConfig 的 base-url 用短超时(connect/read 各 2s)RestClient 探测,任何 HTTP 响应(含 4xx)视为连通、连接失败/超时视为断开;顺序探测不并行(H4 ⑤)——oryxos-provider/src/main/java/com/oryxos/provider/ProviderService.java
- [X] T023 [US4] SystemApiController 的 /info 接入 providers 连通状态(探活失败不影响端点 200)——oryxos-web/src/main/java/com/oryxos/web/controller/SystemApiController.java

**Checkpoint**: 系统状态端点全绿,管理台"运行状态"页数据源就位。

---

## Phase 7: User Story 5 - 第一版只读管理平台(Priority: P3)

**Goal**: 五页只读管理台(会话列表/Profile/Tool/记忆/状态),托管 /admin,SPA 回落,风格 skill 沉淀

**Independent Test**: `npm run build` 产物落 static/admin;起服务浏览器访问 /admin 五页渲染真实数据、无写入口、三态占位、子路径刷新不 404

### Implementation for User Story 5

- [X] T024 [US5] 编写项目内 skill .claude/skills/oryxos-admin-ui/SKILL.md:设计 token 值取 website/.vitepress/theme/custom.css 实际值(主色 #FF6B2B、hover #FF8C42、强调 #E8450A、边框 #1a1a1a、灰阶 #eeeeee/#999/#555、字体 Space Grotesk/Inter + JetBrains Mono;课件提示词里的色值与该文件不符时以文件为准并记录)、工程约定(base '/admin'、产物 static/admin、SPA 回落、只调 /api/v1)、布局/三态/响应式规范、验收清单
- [X] T025 [US5] 按 T024 的 skill 生成 Vue 3 + Vite 管理台:工程骨架从 `origin/pr/contributing-md@f809d2d` 移植(package.json/package-lock.json/vite.config.js/tokens.css,对齐 #167);左侧导航五项,分别调 GET /api/v1/sessions、/profiles、/tools、/memory、/info 渲染;只读无写按钮;错误显示信封 message;空/加载/错误三态占位;vite.config base '/admin'、outDir → ../resources/static/admin(相对资源路径)——oryxos-web/src/main/frontend/
- [X] T026 [US5] WebMvcConfigurer:CORS 全开(allowedOriginPatterns "*" on /api/v1/**,TS §7.4)+ /admin/** 静态资源(PathResourceResolver 兜底:文件存在直出,否则回落 admin/index.html;GET /api/v1/** 不受影响)——oryxos-web/src/main/java/com/oryxos/web/config/WebConfig.java
- [X] T027 [US5] 移植并验证 frontend-maven-plugin 构建串联(对齐 #167,research R7):①从 `origin/pr/contributing-md@f809d2d` 原样移植插件段(1.15.1、generate-resources 三段 execution、`${frontend.skip}`)到 oryxos-web/pom.xml;②根 pom.xml properties 补全 #167 遗漏的 `frontend.node.version`/`frontend.skip`;③移植 .gitignore 三条目(`**/node_modules/`、oryxos-web/src/main/frontend/dist/、oryxos-web/src/main/resources/static/admin/——产物不入库,插件打包时重建);④`mvn clean package` 实测 npm install → vite build → 产物进 fat JAR 全链可用(含 fat JAR 内 /admin 资源存在);node 下载防火墙风险按 research R7 预案处理

**Checkpoint**: 管理平台五页只读可用,风格 skill 可供 30 节复用。

---

## Phase 8: Polish & 收尾

**Purpose**: 集成冒烟、全量门禁、文档同步、验收报告

- [X] T028 WebSmokeIT:@SpringBootTest(RANDOM_PORT)+ TestRestTemplate + @Tag("integration"),断言 /health、/info、/profiles、/tools 四端点 200 且信封字段完整(不断言 provider 连通值;真实触发 JPA repository 扫描);补强(analyze C1):不存在的 Agent 名 POST invoke → 404,不依赖模型——oryxos-boot/src/test/java/com/oryxos/boot/WebSmokeIT.java
- [X] T029 全量门禁:`mvn clean verify` 全绿(含 P3C/SpotBugs/FindSecBugs/PMD);手动 `mvn test -pl oryxos-boot -Dtest=WebSmokeIT` 全绿;前序节全部测试回归绿
- [X] T030 收尾文档同步(用户确认):①宪法、TechnicalSolution §7.2、CLAUDE.md 的"10 个端点"表述改为"核心阶段 10 个端点(会话列表为只读扩展)"类表述(宪法改动附 Amendment 变更说明:修改内容/理由/影响范围);②TS §7.2、CLAUDE.md 的"查长期记忆(MEMORY.md)"泛化为"长期记忆全文"
- [X] T031 节级验收报告:六项证据 DoD(mvn 全绿、harness 对号、交付物存在性核对、前序回归绿、H4 六条不变量自查、人工项清单)+ 变更总结(reviewer 导读)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖,立即开始
- **Foundational (Phase 2)**: 依赖 Setup,阻塞全部 user story
- **US1 (Phase 3)**: 依赖 Foundational;US2~US4 与 US1 无硬依赖(可并行);US5 数据依赖 US1/US3/US4 端点
- **Polish (Phase 8)**: 依赖全部 story(WebSmokeIT 需 US3/US4 端点就位)

### User Story Dependencies

- **US1 (P1)**: Foundational 后可开始——其余 story 不依赖
- **US2 (P1)**: Foundational 后可开始(SessionManager 为既有接口,不依赖 US1 的 listAll)
- **US3 (P2)**: Foundational 后可开始
- **US4 (P2)**: Foundational 后可开始
- **US5 (P3)**: 依赖 US1(会话列表端点)、US3(profiles/memory/tools)、US4(info)

### Within Each User Story

- 测试任务先于实现任务落地,先红后绿(T003/T007/T008/T013/T020)
- 实现与测试同一任务批次落地,红了当场修,不攒到最后

### Parallel Opportunities

- T001/T002 可并行;T003/T004 可并行(不同文件)
- US1~US4 在 Foundational 完成后可并行(单人开发按 P1→P2 顺序)
- T010/T015/T016/T019/T020 标记 [P],不同文件无依赖

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → Phase 2 Foundational → Phase 3 US1
2. **STOP and VALIDATE**: SessionApiControllerTest 全绿 + curl 五端点链路
3. 之后按 P1→P2→P3 顺序推进(US2→US3→US4→US5)

### Incremental Delivery

每完成一个 story 即跑该 story 的测试与 curl 验证;全部完成后 Phase 8 收尾。

---

## Notes

- [P] 任务 = 不同文件、无依赖
- 测试方法名必须英文(课件中文方法名翻译,原文进 @DisplayName)
- 写前核实第三方 API(mvn dependency:tree 已核实);已定字面量逐字保真
- 不自动 commit/push/package.sh,同步时机由用户决定
- 语法禁区:避开 P3C/ASM 解析不了的 Java 18+ 语法形态
