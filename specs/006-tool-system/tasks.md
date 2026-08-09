---
description: "Task list for Tool 体系 (Lesson 20)"
---

# Tasks: Tool 体系

**Input**: Design documents from `/specs/006-tool-system/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/tools.md, quickstart.md

**Tests**: 本 feature 测试为强制的——课件"验收 harness"是验收标准本身,每个 story 的测试任务排在实现任务之前或并行(harness 先行)。测试方法名英文(驼峰),课件中文方法名翻译后进 `@DisplayName`。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## 关键背景(实现前必读)

- Spring AI 锁定 **1.0.0-M4**:无 `@Tool` 注解,内置工具与方式三统一走 `FunctionCallback.builder().method(name, paramTypes...).targetObject(bean).build()` 管道,schema 从方法签名自动生成(`-parameters` 已开,参数名保真)。见 research.md 决策 1。
- MCP SDK **0.7.0**(io.modelcontextprotocol.sdk:mcp):`McpClient.sync(transport)` → `SyncSpec.build()` → `McpSyncClient`(类,listTools/callTool 非 final 可 mock);构造后**必须先 `initialize()`** 再 `listTools()`。见 research.md 决策 2。
- 存量工具(FileTools/ShellTools/HttpTools)行为红线:工具名/schema/执行行为逐字保真;越界语义 = 返回 `ToolResult.failure(msg, false)`(不抛异常,与存量一致);schemas 由手写字符串变为方法签名自动生成(语义等价)。
- NotifyTools(19 节)保持 OryxTool Bean 形态不动;ToolAutoRegistrar 同时保留 OryxTool Bean 扫描。
- 越界测试用**真实 WhitelistSandbox + 受控配置**(构造时显式传白名单,不 mock 掉安全校验)。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与配置骨架

- [ ] T001 Add `io.modelcontextprotocol.sdk:mcp:0.7.0` dependency to `oryxos-tool/pom.xml`(直接声明版本,不进 BOM;然后 `mvn -pl oryxos-tool dependency:tree` 确认解析且与 spring-ai-core M4 无冲突)
- [ ] T002 [P] Fill `.oryxos/mcp_servers.yaml` with commented example skeleton (stdio + sse 两例,字段 name/transport/command/args/env/url,见 data-model.md §3;缺配置时 McpClientService 必须安全跳过)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 统一管道与测试基座——所有 user story 的前置

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Create `AnnotatedToolAdapter` in `oryxos-tool/src/main/java/com/oryxos/tool/adapter/AnnotatedToolAdapter.java`: wraps `org.springframework.ai.model.function.FunctionCallback` into `OryxTool` — getName→fc.getName(), getDescription→fc.getDescription(), getInputSchema→fc.getInputTypeSchema(), execute(inputJson)→`ToolResult.success(fc.call(inputJson))`, 异常 catch → `ToolResult.failure(e.getMessage(), true)`(可重试,对齐 HttpTools 既有语义);实现类不 new 任何东西,全部委托
- [ ] T004 Modify `ToolAutoRegistrar` in `oryxos-tool/src/main/java/com/oryxos/tool/ToolAutoRegistrar.java`: 除既有 `getBeansOfType(OryxTool.class)` 注册外,新增 `getBeansOfType(FunctionCallback.class)` 扫描,每个用 T003 的适配器包装注册;注册顺序:先 OryxTool beans 再 FunctionCallback 包装(同名覆盖时后者生效,NotifyTools 与内置工具无重名,理论无冲突)
- [ ] T005 [P] Create test fixture `ToolTestFixture` in `oryxos-tool/src/test/java/com/oryxos/tool/`: 用 `AnnotationConfigApplicationContext` 装配——真实 `WhitelistSandbox`(构造传参:allowedPaths=JUnit @TempDir, allowedCommands=[echo,ls], allowedDomains=[api.weather.com, api.duckduckgo.com])+ 内置工具类 + ToolConfiguration + ToolAutoRegistrar + ToolRegistry;暴露 `registry()`/`sandbox()`/`tempDir()` 静态访问,供全部 harness 测试复用

**Checkpoint**: 统一管道可装配——内置工具能经 FunctionCallback 管道注册为 OryxTool

---

## Phase 3: User Story 1 - 内置工具重构 (Priority: P1) 🎯 MVP

**Goal**: 6 个存量工具(文件 3 + shell + http 2)重构为方法 + 装配管道,行为保真;契约三件套与注册过滤验收落地

**Independent Test**: 对每个工具分别执行合法输入与越界输入——合法返回真实结果、越界返回 failure 且无真实 IO

### 测试先行(harness,课件正文两个用例为模板)

- [ ] T006 [US1] Create `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest` in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/` (each: ①正常能跑通——经 ToolTestFixture 拿 registry.find(name) 执行合法输入断言 success;②越界会被拦——白名单外路径/命令/域名执行断言 failure 且 errorMessage 含拦截信息;用 `@DisplayName` 保留课件中文用例名,方法名英文;FileTools 覆盖 read_file/write_file/list_dir 三条)
- [ ] T007 [US1] Create `OryxToolContractTest` in `oryxos-tool/src/test/java/com/oryxos/tool/`: `@ParameterizedTest @MethodSource("allRegisteredTools")` 遍历 ToolTestFixture 真实 ToolRegistry——name/description/inputSchema 三件套非空(`assertNotNull`),新工具自动纳入
- [ ] T008 [US1] Create `ToolRegistryTest` in `oryxos-tool/src/test/java/com/oryxos/tool/`: 三类来源(内置/FunctionCallback 包装/MCP 适配器)都注册为 OryxTool;`subset(List.of("a","b"))` 结果**恰好等于**声明列表——断言大小相等且每个都在内(多过滤/少过滤都红)

### 实现

- [ ] T009 [P] [US1] Refactor `FileTools` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`: 删除 inner OryxTool 类,改为普通 public 方法 `readFile(String path)` / `writeFile(String path, String content)` / `listDir(String path)`,方法体第一行 `sandbox.enforce(FILE_READ/FILE_WRITE, path)`,异常语义与结果格式与存量逐字一致;删除不再需要的 `extractField`(检查 NotifyTools 是否仍引用,若引用保留为静态工具方法)
- [ ] T010 [P] [US1] Refactor `ShellTools` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`: 普通方法 `shell(String command)`,首行 `sandbox.enforce(SHELL_COMMAND, command)`,30s 超时与 `destroyForcibly` 逻辑保真
- [ ] T011 [P] [US1] Refactor `HttpTools` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpTools.java`: 普通方法 `httpGet(String url)` / `httpPost(String url, String body)`,首行 `sandbox.enforce(HTTP_REQUEST, url)`,30s 超时保真
- [ ] T012 [US1] Create `ToolConfiguration` in `oryxos-tool/src/main/java/com/oryxos/tool/config/ToolConfiguration.java`: 注册 6 个 `FunctionCallback` @Bean(方法引用经 `builder().description(...).method("read_file", String.class).targetObject(fileTools).build()`,description 从存量 getDescription 逐字搬移;装配一处可见全部工具,对齐课件"在装配处注册")

**Checkpoint**: 6 个工具经统一管道注册;T006/T007/T008 全绿;`tool list` 可看(人工)

---

## Phase 4: User Story 2 - MCP 方式二接线 (Priority: P1)

**Goal**: mcp_servers.yaml 声明即接入;失联只 WARN 不拖垮启动;调用原样转发

**Independent Test**: 正常 server 工具全部注册可用;坏 server 启动不炸、其余照常

### 测试先行

- [ ] T013 [US2] Create `McpToolAdapterTest` in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/`: mock `McpSyncClient`——①execute 转发:verify callTool 收到 `CallToolRequest(name, 原样 argsMap)`,返回 TextContent 拼成 content;②isError=true → failure(retryable=true);③callTool 抛异常 → failure("MCP 调用失败", true);④getInputSchema 返回 tools/list 的 schema 序列化(非空)
- [ ] T014 [US2] Create `McpClientServiceTest` in `oryxos-tool/src/test/java/com/oryxos/tool/mcp/`: 继承 McpClientService 的测试子类 override `connect(cfg)`——①正常:mock 的 listTools 返回 2 个工具 → registry 注册 2 个;②失联:`connect` 抛 `ConnectException` → `connectAll()` 不抛、registry 不含坏 server 工具、含好 server 工具(课件关键回归:外部依赖可用性≠自身可用性)

### 实现

- [ ] T015 [P] [US2] Create `McpServerConfig` in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/`: record(name, transport, command, args, env, url);提供 `parse(List<Map>)` 容错解析(SnakeYAML 读 `.oryxos/mcp_servers.yaml`,顶层兼容 `servers:` 列表与直接列表;缺 name/transport 非法/stdio 缺 command/sse 缺 url → WARN 跳过该项)
- [ ] T016 [US2] Rewrite `McpClientService` in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClientService.java`: `init()` → 读配置 → 逐 server `connect(cfg)`(protected 测试缝:stdio → `StdioClientTransport(ServerParameters.builder(command).args(args).env(env))`;sse → `HttpClientSseClientTransport(url)`)→ `McpClient.sync(transport).build()` → `initialize()` → `listTools()` → 每个 `McpSchema.Tool` 包成 `McpToolAdapter` 注册;任何异常 catch 记 `log.warn` 跳过该 server 继续;删除 listMcpTools 占位
- [ ] T017 [US2] Rewrite `McpToolAdapter` in `oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpToolAdapter.java`: 构造收 `(McpSyncClient client, McpSchema.Tool tool)`;getName/getDescription 从 tool 取;getInputSchema: `var js = tool.inputSchema()` 手动提取 type/properties/required/additionalProperties 组 LinkedHashMap 再 Jackson 序列化(JsonSchema 包私有,不能直接引用类型名);execute: `callTool(new CallToolRequest(name, objectMapper.readValue(inputJson, Map.class)))` → TextContent 块拼接 content;isError → failure(content, true);异常 → failure("MCP 调用失败", true)

**Checkpoint**: T013/T014 全绿;失联隔离行为真实(可人工用坏配置冒烟)

---

## Phase 5: User Story 3 - 方式三注册 (Priority: P2)

**Goal**: 业务方 FunctionCallback Bean 放进工程即注册( M4 等价形态,管道在 Foundational 已就绪)

**Independent Test**: 测试里声明自定义 FunctionCallback @Bean → registry 可见且可调

- [ ] T018 [US3] Add test to `oryxos-tool/src/test/java/com/oryxos/tool/`(新建 `AnnotatedToolAdapterRegistrationTest`): 测试上下文里声明一个自定义 FunctionCallback @Bean(如 `my_custom_tool`,description/inputTypeSchema 自定义)→ 断言 ToolTestFixture 的 registry 含该工具、三件套非空、`execute` 返回预期结果——端到端验证"自动扫描→包装→注册→可调"

**Checkpoint**: T018 绿——方式三管道闭环(人工项:真实 @Tool 形态待 M6 升级或 FunctionCallback 示例进工程)

---

## Phase 6: User Story 4 - 业界级扩展工具 (Priority: P2)

**Goal**: edit_file/grep/glob 扩进 FileTools;ask_user/web_search 接口先行

**Independent Test**: 每个扩展工具合法输入与越界输入两路径

### 测试先行

- [ ] T019 [US4] Extend `FileToolsTest` in `oryxos-tool/src/test/java/com/oryxos/tool/builtin/FileToolsTest.java`: ①edit_file——唯一匹配替换成功且文件其余内容不动;找不到 oldText → failure 且文件一字不动;出现多次 → failure 且文件一字不动;②grep——`文件:行号:内容` 格式、命中正确、超 200 条截断注明、二进制/非 UTF-8 文件跳过不中断;③glob——通配命中正确、超 200 条截断
- [ ] T020 [US4] Create `InteractionToolsTest` in `oryxos-tool/src/test/java/com/oryxos/tool/interaction/`: mock UserInteraction 返回回答 → ask_user 成功带回;UserInteraction 抛异常 → failure 不静默;UnsupportedUserInteraction.ask → 抛"不支持交互"异常
- [ ] T021 [US4] Create `DuckDuckGoSearchProviderTest` in `oryxos-tool/src/test/java/com/oryxos/tool/search/`: MockWebServer 假端点——①正常:RelatedTopics Text/FirstURL 与 AbstractText 解析成 SearchResult 列表;②空结果:返回空列表不炸;③HTTP 错误:抛异常;④请求发出前已过 sandbox.enforce(用受限 WhitelistSandbox 断言 URL 域名在白名单内才放行)

### 实现

- [ ] T022 [P] [US4] Extend `FileTools` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/FileTools.java`: 新增 `editFile(String path, String oldText, String newText)`(先读全文计数,唯一匹配才写回,其余情况 failure 且不落盘)/ `grep(String path, String pattern)`(Files.walk 递归,严格 UTF-8 解码失败跳过,上限 200 条截断注明,`文件:行号:内容`)/ `glob(String pattern)`(PathMatcher 通配,上限 200 条截断);全部首行 `sandbox.enforce`
- [ ] T023 [P] [US4] Create `UserInteraction` interface + `ConsoleUserInteraction`(BufferedReader 读一行,IOException 抛异常)+ `UnsupportedUserInteraction`(抛异常带"不支持交互") in `oryxos-tool/src/main/java/com/oryxos/tool/interaction/`
- [ ] T024 [P] [US4] Create `InteractionTools` in `oryxos-tool/src/main/java/com/oryxos/tool/interaction/`: `askUser(String question)` → `userInteraction.ask(question)` 结果回传;异常 → failure
- [ ] T025 [P] [US4] Create `SearchProvider` interface + `SearchResult` record in `oryxos-tool/src/main/java/com/oryxos/tool/search/`;Create `DuckDuckGoSearchProvider`(base URL 与 HttpClient 构造可注入,`search(query)` 内部第一件事 `sandbox.enforce(HTTP_REQUEST, url)` 再发请求;解析 RelatedTopics 含嵌套 Topics 展平,AbstractText/AbstractURL 兜底)
- [ ] T026 [US4] Create `WebSearchTools` in `oryxos-tool/src/main/java/com/oryxos/tool/builtin/`: `webSearch(String query)` → provider.search → 渲染文本(标题/URL/摘要)
- [ ] T027 [US4] Extend `ToolConfiguration` in `oryxos-tool/src/main/java/com/oryxos/tool/config/ToolConfiguration.java`: 注册 5 个新工具 FunctionCallback @Bean(edit_file/grep/glob/ask_user/web_search);`UserInteraction` 按 `@ConditionalOnProperty(name="oryxos.cli.interactive", havingValue="true")` 装配 ConsoleUserInteraction,否则 UnsupportedUserInteraction(两实现不加 @Component)

**Checkpoint**: T019/T020/T021 全绿;14 工具全部注册(6 存量 + 5 扩展 + notify + 2 memory 待 22 节)

---

## Phase 7: Polish & Cross-Cutting

**Purpose**: 全量门禁与契约核对

- [ ] T028 Run `mvn clean verify` — 全量门禁(P3C/SpotBugs/FindSecBugs/PMD)全绿;有红先修实现,不删断言不 @Disabled
- [ ] T029 [P] Cross-check `contracts/tools.md` 14 工具注册清单 vs 真实 registry(写一个临时断言或 `oryxos tool list` 冒烟);确认前序节测试(oryxos-core/provider/storage 全量)回归绿
- [ ] T030 [P] Output 剩余人工项清单到验收报告(方式一/方式三/真网络/ask_user 交互/审计目检/MCP 失联冒烟,见 quickstart.md)

---

## Dependencies (story completion order)

```text
Phase 1 (Setup) → Phase 2 (Foundational: AnnotatedToolAdapter + ToolAutoRegistrar + Fixture)
    ├──→ Phase 3 (US1 内置工具重构, MVP)  ──┐
    │                                       ├──→ Phase 7 (Polish)
    ├──→ Phase 4 (US2 MCP 接线)  ───────────┘
    ├──→ Phase 5 (US3 方式三)  (依赖 Phase 2,可与 US1/US2/US4 并行)
    └──→ Phase 6 (US4 扩展工具) (依赖 Phase 2,可与 US2 并行)
```

- Phase 3/4/5/6 相互独立(仅依赖 Phase 2),可并行实现;T009/T010/T011 相互独立可并行,T022-T026 相互独立可并行。
- 测试任务(T006-T008、T013/T014、T019-T021)均在对应实现任务之前或并行——harness 先行。

## Implementation Strategy (MVP first)

1. **MVP = US1(Phase 3)**:先跑通"内置工具经管道注册 + 契约 + 过滤"——这是课件核心(统一抽象屏蔽来源)。
2. 再补 US2(MCP,硬依赖 31 节 Demo)、US4(扩展工具)、US3(方式三闭环)。
3. 全程遵守:每任务完成即跑该模块测试,红了当场修,不攒到最后;测试方法名英文;不动前序节公共接口;越界语义与存量一致(failure 而非抛异常)。
