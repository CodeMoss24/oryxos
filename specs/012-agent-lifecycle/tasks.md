---

description: "第30节任务清单:动态管理 Agent(一句话生成、上传即上线、免重启)"
---

# Tasks: 动态管理 Agent

**Input**: Design documents from `/specs/012-agent-lifecycle/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/rest-api.md、quickstart.md

**Tests**: 课件"验收 harness"明确要求测试先行(harness 先行),每 story 的测试任务先于实现任务落地。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1~US5(映射 spec.md 用户故事);US-FE = 前端(单文件 App.vue,独立可测);Setup/Foundational/Polish 无标签
- Include exact file paths in descriptions

## Path Conventions

- 多模块 Maven:oryxos-core / oryxos-memory / oryxos-web / oryxos-boot(9 模块既有,不新增模块)

---

## Phase 1: Setup

**Purpose**: 配置键与生成提示词就位

- [ ] T001 在 oryxos-boot/src/main/resources/application.yaml 新增 `oryxos.author.provider` / `oryxos.author.model` 配置键(provider 缺省逻辑在读取处实现:缺省取 `oryxos.providers` 第一个;model 无缺省)
- [ ] T002 新建 docs/prompt/prompt.md,定义 `AGENT_AUTHOR_PROMPT` 常量内容:OryxOS `AGENT.md` 格式说明(frontmatter 字段 name/description/provider/model/tools/identity/notify_channels/schedules + 正文要求,含示例),供生成端点复用(常量在 Java 代码里引用,文档为格式规范)

**Checkpoint**: 配置与提示词就位,后续 story 可并行开工

---

## Phase 2: Foundational

**Purpose**: 生命周期底座——`unregisterProfile` 是 delete/update 的硬前提,阻塞 US1 及后续所有 story

- [ ] T003 在 oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java 实现 `public void unregisterProfile(Profile profile)`:遍历 `profile.getSchedules()`,对每条从 `scheduledTasks` 取 `ScheduledFuture.cancel(false)`,并移除 `scheduledTasks`/`taskRefs`/`scheduledTaskIds` 三个索引(不动 `taskLocks`);任务未注册或 schedules 为空时静默跳过
- [ ] T004 在 oryxos-core/src/test/java/com/oryxos/core/scheduler/AgentSchedulerUnregisterTest.java 写单测:registerProfile 后 unregisterProfile → `scheduledFutureFor(id)` 为 null 且 `scheduledTaskIds` 已清(再 registerProfile 能重新调度);schedules 变更场景(先注销后注册,新旧 cron 不并存);delete 顺序回归由 US1 测试覆盖

**Checkpoint**: 注销能力就位——US1(delete/update)、US3(files 保存)、US4 全部依赖

---

## Phase 3: User Story 1 - 新建 Agent 即上线,免重启 (Priority: P1) 🎯 MVP

**Goal**: `POST /agents {name, description}` 脚手架完整 Agent 目录 + 派生注册;查改删;失败回滚不留半个 Agent

**Independent Test**: 创建 → 列表立刻可见;name 冲突 400 且零目录写入;注册失败回滚;delete 按"注销定时→移出索引→归档"顺序;update schedules 先注销后注册

### Tests for User Story 1 ⚠️(课件 harness 点名,先写先红)

- [ ] T005 [P] [US1] 建 oryxos-core/src/test/java/com/oryxos/core/agent/AgentLifecycleServiceTest.java:全 mock(InOrder 钉顺序),覆盖——create 按序执行;name 冲突第一步就拒、一个目录都不写(verify agentStore never write);**注册失败必须回滚已写目录**(doThrow on profileRegistry.register → assertThrows + verify agentStore.delete + verify agentScheduler never registerProfile + assertFalse registry.exists);create 与 watcher 走同一段 register(agentDir)(直接调 lifecycle.register(agentDir) 断言注册+定时);**删除必须先停定时再动索引和目录**(InOrder: unregisterProfile → remove → archive);update 改 schedules 先 unregister 后 register
- [ ] T006 [P] [US1] 建 oryxos-web/src/test/java/com/oryxos/web/controller/AgentApiControllerTest.java(standalone MockMvc,mock AgentLifecycleService/ProfileRegistry 等):create 冲突 → 400、查不存在 → 404、delete 不存在 → 404,响应体统一 ApiResponse 信封;端点薄转发(controller 只转调 lifecycle)

### Implementation for User Story 1

- [ ] T007 [P] [US1] 在 oryxos-core/src/main/java/com/oryxos/core/agent/AgentStore.java 实现:workspace 根解析(OryxOsRuntime)、`scaffold(name, description)` 生成四件套(AGENT.md 含 frontmatter:name/description/provider(name=mock 时 mock,否则 deepseek, model=deepseek-chat)/tools 基础集/identity;正文=name+description 任务指令;+ scripts/README.md + skills/README.md + REFERENCE.md)、`write(name, markdown)`、`writeAll(files)`、`delete(agentDir)`、`archive(name)`(移入 .oryxos/archive/,重名加时间戳后缀)、`resolveRelative(path)` 防目录穿越(normalize + startsWith)
- [ ] T008 [P] [US1] 在 oryxos-web/src/main/java/com/oryxos/web/dto/ 建 AgentView/CreateAgentRequest(name+description)/UpdateAgentRequest(description/provider/tools/schedules/notify_channels/body 任一覆写)DTO
- [ ] T009 [US1] 在 oryxos-core/src/main/java/com/oryxos/core/agent/AgentLifecycleService.java 实现:依赖 AgentStore/AgentLoader/ProfileRegistry/AgentScheduler/ProviderService;`create`(exists 拒 → scaffold → register(agentDir),catch 回滚 delete)、`register(Path)`(deriveProfile → registry.register → 有 schedules 则 scheduler.registerProfile)、`get`/`list`(Profile → AgentView 素材)、`update`(覆写 AGENT.md → deriveProfile 校验 → unregister 旧 + register 新)、`delete`(unregisterProfile → registry.remove → store.archive)
- [ ] T010 [US1] 扩展 oryxos-web/src/main/java/com/oryxos/web/controller/AgentApiController.java:加 create/get/update/delete 四端点(保留 invoke 不动);404 用既有 ResourceNotFoundException;400 用 InvalidRequestException
- [ ] T011 [US1] 跑通 oryxos-core 与 oryxos-web 测试模块:T005/T006 全绿

**Checkpoint**: 生命周期闭环(建/查/改/删),harness 判卷

---

## Phase 4: User Story 2 - 丢目录也即上线 (Priority: P1)

**Goal**: WorkspaceWatcher 实时监听 `.oryxos/agents/`,手工丢目录免重启即上线;坏目录不拖垮

**Independent Test**: 运行中拷入目录 → ProfileRegistry 出现;删目录 → 注销;坏目录跳过监听不断

### Tests for User Story 2 ⚠️

- [ ] T012 [P] [US2] 建 oryxos-core/src/test/java/com/oryxos/core/agent/WorkspaceWatcherTest.java(临时工作区 + 直接驱动事件处理方法,不依赖真实 WatchService 时序):往 agents/ 写 Agent 目录 → onAgentDirChanged(CREATE) 后 ProfileRegistry 出现;删目录 → 注销;写坏目录(AGENT.md 缺 provider)→ 抛错被 catch,其余 Agent 照常注册
- [ ] T013 [US2] 在 oryxos-core/src/main/java/com/oryxos/core/agent/WorkspaceWatcher.java 实现:`@Component`,启动守护线程(daemon,名 workspace-watcher)先全量扫 `.oryxos/agents/` 调 lifecycle.register(agentDir),再 `WatchService` 注册 agents 目录监听 ENTRY_CREATE/DELETE/MODIFY;事件循环:存在 → register、不存在 → unregisterByDir(注销);catch RuntimeException log.warn 单个坏目录不拖垮;短暂防抖(事件后 sleep)容忍多文件写入顺序
- [ ] T014 [US2] 跑通 T012;确认启动扫描与 29 节既有 scanAndRegister 不重复注册(registerProfile 幂等)

**Checkpoint**: 两条录入路径汇到同一段 register(agentDir)

---

## Phase 5: User Story 3 - 一句话生成并预览 (Priority: P1)

**Goal**: `generate-files` 大模型生成 AGENT.md 草稿(不落盘可预览)、`files` 保存即生效;非法产出 400、model 未配 503

**Independent Test**: 生成 → 可解析草稿、目录无新文件;保存 → 写入生效;非法 → 400

### Tests for User Story 3 ⚠️

- [ ] T015 [P] [US3] 建 GenerateTest(mock ProviderService/Lifecycle 依赖):generate-files 产出可被 AgentLoader.parseAgentMd 解析;只生成不落盘不注册(verify agentStore never);LLM 产出非法(缺 name/provider)→ 400 可读原因;model 未配 → 503;files 保存:AGENT.md 非法 → 400 不写坏目录、合法 → writeAll + 重注册

### Implementation for User Story 3

- [ ] T016 [US3] 在 AgentLifecycleService.java 实现 `generateFiles(name, description)` 与 `saveFiles(name, files)`:generateFiles——Agent 存在校验(404)→ 构造生成 Profile(name=`__author__`,provider=author 配置;Agent 已存在则沿用其 provider;model 空 → ProviderUnavailableException 503)→ `providerService.chat("author-generator", genProfile, new Prompt(...))` → 剥 ``` 围栏 → AgentLoader.parseAgentMd 校验(非法 → InvalidRequestException 400)→ 返回 Map<路径,内容>;saveFiles——AGENT.md 校验可解析(400)→ agentStore.writeAll → unregister 旧 + register 新
- [ ] T017 [US3] 扩展 AgentApiController.java:加 generate-files / files 两端点;author 配置读取(@Value oryxos.author.provider/model,provider 缺省取 oryxos.providers 第一个)
- [ ] T018 [US3] 跑通 T015

**Checkpoint**: 一句话生成闭环(生成→预览→保存),llm_calls 审计落库

---

## Phase 6: User Story 4 - 详情可查可改可删 + 专属记忆 + 固定会话 (Priority: P2)

**Goal**: per-agent 记忆(`agents/<name>/MEMORY.md`)、固定会话(channel=admin/user=console)累积上下文;移除全局 memory 端点

**Independent Test**: save_memory 落对 Agent 文件;固定会话幂等累积;GET memory/session 返回正确内容

### Tests for User Story 4 ⚠️

- [ ] T019 [P] [US4] 在 oryxos-memory/src/test 建 per-agent 记忆回归(MarkdownMemoryStore 直测):置入 agentName → append/load 落在 `agents/<name>/MEMORY.md`;无 agentName → 回退全局路径;两 Agent 记忆互不可见;LongTermMemoryStore 既有契约测试不改仍绿
- [ ] T020 [P] [US4] 固定会话回归(web 或 core):getOrCreate("admin","console",name) 幂等——两次调用同一 session;session/messages 发消息后 GET session 可见累积

### Implementation for User Story 4

- [ ] T021 [US4] 在 oryxos-core/src/main/java/com/oryxos/core/runtime/ToolExecutionContext.java 建 ThreadLocal<String> agentName(set/get/clear)
- [ ] T022 [US4] 修改 oryxos-core/src/main/java/com/oryxos/core/react/ToolExecutor.java:`execute` 入口 `ToolExecutionContext.set(profile.name())`、`finally` 中 clear(同步阻塞模型,一次 ReAct 一条虚拟线程,ThreadLocal 可靠)
- [ ] T023 [US4] 修改 oryxos-memory/src/main/java/com/oryxos/memory/MemoryServiceImpl.java + MarkdownMemoryStore.java:buildContext(取 session.profileName())与 readAll(取入参 Agent 名)在委托 store 前后临时置入/复原 agentName;MarkdownMemoryStore 打开文件时优先 `agents/<name>/MEMORY.md`,无 agentName 回退全局(SPI 契约与三档后端不动)
- [ ] T024 [US4] 扩展 AgentApiController.java:加 GET /agents/{name}/memory(→ 200 memory 全文);**删除** oryxos-web/src/main/java/com/oryxos/web/controller/MemoryApiController.java(全局 /api/v1/memory 移除,课件 5.2.1)
- [ ] T025 [US4] 扩展 AgentApiController.java:加 GET /agents/{name}/session(SessionView)与 POST /agents/{name}/session/messages(固定会话触发 ReAct,60s 超时复用 invoke 逻辑)
- [ ] T026 [US4] 跑通 T019/T020;确认移除全局 memory 后无遗留引用(web 测试/前端除外,前端在 US-FE 处理)

**Checkpoint**: 每个 Agent 有自己的记忆与会话,全局 memory 端点下线

---

## Phase 7: User Story 5 - 工作区文件浏览与编辑 (Priority: P2)

**Goal**: tree 目录树 + file 读写;防目录穿越是唯一安全要点

**Independent Test**: tree 结构;`../../etc/passwd` → 400;正常读写生效

### Tests for User Story 5 ⚠️

- [ ] T027 [P] [US5] 建 oryxos-web/src/test/java/com/oryxos/web/controller/WorkspaceApiControllerTest.java:tree 返回 agents/archive 结构、可钻进 Agent 目录列文件;**file?path=../../etc/passwd 目录穿越 → 400**(关键回归);正常文件返回内容;POST file 写 AGENT.md 走 update、越界 400

### Implementation for User Story 5

- [ ] T028 [US5] 建 oryxos-web/src/main/java/com/oryxos/web/controller/WorkspaceApiController.java:GET tree(agents/ + archive/ 递归 FileNode)、GET file?path=(防穿越 → 400)、POST file{path, content}(防穿越;目标为 agents/<name>/AGENT.md → 走 AgentLifecycleService.update;其余直接写盘,父目录不存在则创建);FileNode/FileContentRequest DTO 落 oryxos-web/src/main/java/com/oryxos/web/dto/
- [ ] T029 [US5] 跑通 T027

**Checkpoint**: 文件浏览器读写闭环,越界路径 100% 拦截

---

## Phase 8: User Story - 管理台前端 (US-FE,单文件 SPA 独立交付)

**Goal**: App.vue 升级为"Agent 列表 + 新建 + 详情 5 tab";移除全局长期记忆页

**Independent Test**: `npm run build` 无报错;页面走通"新建 → 预览 → 保存 → 会话 → 记忆 → 删除"

- [ ] T030 [P] [US-FE] 修改 oryxos-web/src/main/frontend/src/App.vue:Agent 列表页升级(表格 查看/编辑/删除+删前二次确认 + "新建 Agent"表单 name+description → POST /agents;出错显示 ApiResponse.message);移除"长期记忆"导航项与全局 memory 视图
- [ ] T031 [US-FE] App.vue 详情页 5 tab:基本信息(AgentView 渲染)/生成(描述 → POST generate-files → 可编辑预览 → POST files 保存并生效)/文件(tree + file 读写)/会话(固定会话气泡 + 输入框发消息)/记忆(只读 MEMORY.md);样式沿用 oryxos-admin-ui skill token(深色+橙,custom.css 为准),三态占位必做
- [ ] T032 [US-FE] 构建验证:`cd oryxos-web/src/main/frontend && npm run build` 无报错,产物落 static/admin/

**Checkpoint**: 管理台"真能管",全流程可走通

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 收尾、回归、H4 自查

- [x] T033 [P] 清理:grep 全局 /api/v1/memory 遗留引用(前端/测试/docs)全部更新;确认 MemoryApiController 删除后 web 模块无编译残留
- [x] T034 [P] 在 oryxos-core/oryxos-memory/oryxos-web 跑单模块测试,红了当场修
- [x] T035 全量门禁:`mvn clean verify` 全绿(含 P3C/SpotBugs/FindSecBugs/PMD),贴关键输出
- [x] T036 H4 六条不变量自查:①涉外 IO 首行过 Sandbox(生成链路走 ProviderService 既有路径,无新涉外 IO;watcher 纯本地)②LLM 调用成败落 llm_calls、工具成败落 tool_invocations(generate 链路走 ProviderService.chat 内建审计)③grep 无明文 key ④session_id 只在 SessionManager 内拼(固定会话只传三元组)⑤无 Reactor/CompletableFuture/自建线程池(watcher 守护线程 = 基础设施线程)⑥无 Spring AI 自动 tool 执行
- [x] T037 交付物存在性核对 + quickstart.md 人工验收清单整理,输出节级验收报告

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 Setup;阻塞所有 story(T003 的 unregisterProfile 是 US1 delete/update 测试的硬前提)
- **User Stories (Phase 3~7)**: 依赖 Foundational;US3 依赖 US1 的 AgentLifecycleService 骨架;US4 依赖 US1(端点)+ Foundational;US5 依赖 US1(update 链路);US-FE 依赖 US1~US4 端点齐(可并行开发但联调在后)
- **Polish**: 依赖全部 story

### User Story Dependencies

- **US1 (P1)**: Foundational 完成后即可,无其它 story 依赖 — 先行
- **US2 (P1)**: 依赖 US1(register(agentDir) 在 LifecycleService 上)
- **US3 (P1)**: 依赖 US1(AgentLifecycleService/AgentStore 骨架 + AgentApiController 扩展点)
- **US4 (P2)**: 依赖 US1(端点骨架);per-agent 记忆改造自身独立
- **US5 (P2)**: 依赖 US1(update 链路)+ AgentStore.resolveRelative(防穿越,可独立)
- **US-FE**: 依赖 US1~US4 端点可用

### Parallel Opportunities

- T005/T006(US1 测试)、T007/T008(US1 模型/DTO)可并行
- US2 与 US3 实现互不冲突(T012~T014 与 T015~T018 文件不同)
- US4 的 memory 改造(T021~T023)与 US5(T027~T028)可并行
- 前端 T030/T031 可在后端端点定型后并行开发

---

## Implementation Strategy

### MVP First (US1 + Foundational)

1. Phase 1: Setup(配置键 + prompt.md)
2. Phase 2: Foundational(unregisterProfile + 测试)
3. Phase 3: US1(建/查/改/删闭环)——MVP!
4. STOP 验证:AgentLifecycleServiceTest + AgentApiControllerTest 全绿

### Incremental Delivery

1. US1 完成 → 生命周期闭环可演示
2. US2 → 丢目录即上线可演示
3. US3 → 一句话生成可演示
4. US4 → 专属记忆 + 固定会话可演示
5. US5 → 文件浏览器可演示
6. US-FE → 管理台全流程
7. Polish → mvn clean verify 全绿收尾

---

## Notes

- 语法禁区:P3C/ASM 解析不了 Java 18+ 增强 switch(`default ->` 写法)一律避开
- 测试方法名英文(课件中文方法名语义等价翻译 + @DisplayName 保留原文)
- 每个 story 完成跑该模块测试,红了当场修;不攒到最后
- 不自动 commit/push/package.sh,同步时机由用户决定
- 生成端点不落盘不注册;create 失败回滚;update/文件保存校验失败不写坏目录(不自动回滚用户文件)
