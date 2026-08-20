# Research: 动态管理 Agent 关键决策

> Phase 0 输出。全部 NEEDS CLARIFICATION 已通过代码库调研(第 30 节 H0)解决;无新第三方依赖需要核实。

## R1. per-agent 记忆的实现路径(5.2.1)

- **Decision**: 新增 `ToolExecutionContext`(core,`ThreadLocal<String> agentName`)。`ToolExecutor.execute` 在执行工具前 `set(profile.name())`、`finally` 中 `remove()`(同步阻塞模型下一次 ReAct 循环跑在一条虚拟线程上,ThreadLocal 可靠);`MarkdownMemoryStore` 打开文件时优先 `agents/<name>/MEMORY.md`,无 agentName 回退全局 `.oryxos/memory/MEMORY.md`;`MemoryServiceImpl.buildContext`(取 `session.profileName()`)与 `readAll`(取入参 Agent 名)在委托 `store.load`/`store.readAll` 前后临时置入再复原。
- **Rationale**: 课件明确"工具不知道自己在替哪个 Agent 跑"是关键障碍,ThreadLocal 是同步阻塞模型下的最小侵入解法;`LongTermMemoryStore` SPI 与三档后端契约测试一行不改(向后兼容)。
- **Alternatives considered**: 给 `OryxTool.execute` 加执行上下文参数——接口签名全链路改动,三档后端契约测试全动,否决;Session 参数穿透 MemoryService——sessionId 不带 profile 语义,且 readAll 无 session,否决。

## R2. WorkspaceWatcher 监听架构(FR-013)

- **Decision**: 装配层 `@Component` 启动时 `@PostConstruct` 起守护线程(`setDaemon(true)`,线程名 `workspace-watcher`),先全量扫 `.oryxos/agents/` 调 `lifecycle.register(agentDir)` 注册存量,再用 JDK `WatchService` 注册 `agents` 目录(`ENTRY_CREATE`/`ENTRY_DELETE`/`ENTRY_MODIFY`),事件循环逐事件处理:目录级事件 → 存在性判断(存在=新增/修改 → `register`,不存在=删除 → 注销)。单个目录注册失败 catch 记 `log.warn` 不中断循环。`scheduleWithFixedDelay` 或事件后 `Thread.sleep(200ms)` 防抖:写目录是多文件操作,AGENT.md 可能后到,首次失败靠下一次 MODIFY 事件重试。
- **Rationale**: 课件要求"JDK WatchService + 后台守护线程(跟 AgentScheduler 同类)"。macOS WatchService 不监听子目录内文件改动(课件 5.2.3 已注明),故只需监听 agents/ 一层、不递归——这也是为什么文件编辑必须显式重注册。
- **Alternatives considered**: Spring `@Scheduled` 轮询目录——不是实时监听,课件点名 WatchService;递归监听 skills/scripts——macOS 不触发、徒增复杂度,否决。

## R3. AgentScheduler.unregisterProfile 语义(FR-003/004)

- **Decision**: 遍历 `profile.getSchedules()`:① `scheduledTasks` 取 `ScheduledFuture` 调 `cancel(false)`(课件口径,不中断正在跑的那次)② 从 `scheduledTasks`/`taskRefs`/`scheduledTaskIds` 三个索引移除(否则重注册时幂等检查 `scheduledTaskIds.add(id)` 为 false 会跳过,update 后新 cron 不生效——课件只提句柄表,但索引一致性是注册可重入的硬前提)③ 不动 `taskLocks`(课件明示;锁条目泄漏可接受,重复注册同一 id 复用同一把锁)。schedules 为空或任务未注册时静默跳过。
- **Rationale**: `cancel(false)` 不中断执行中任务;清三个索引保证 registerProfile 幂等可重入。
- **Alternatives considered**: 只清 scheduledTasks——update 后再注册被 scheduledTaskIds 挡死,否决。

## R4. 生成链路 provider/model 与 503(FR-014)

- **Decision**: 新增配置键 `oryxos.author.provider`(缺省取 `oryxos.providers` 列表第一个的 name)与 `oryxos.author.model`(无缺省)。`generate-files` 时:Agent 已存在 → provider 沿用该 Agent 的 `AGENT.md` frontmatter(课件 5.2.4);model 取生成 Profile 的 `provider.model`,为空 → 抛 `ProviderUnavailableException`(→503,消息"author model 未配置")。构造生成用 `Profile`(name=`__generator__` 之类内部名,provider=author 配置),`ProviderService.chat(genSessionId, genProfile, Prompt)` 落 `llm_calls`(sessionId 用固定值 `author-generator`)。
- **Rationale**: 系统没有"默认 model"(model 只在各 AGENT.md),课件 5.1 明示这是"唯一的实现层缺口",补配置键是唯一解法;model=null 不能发(OpenAI 兼容端点拒收)。
- **Alternatives considered**: 从任一已存在 Agent 借 model——不可预测,否决。

## R5. 脚手架模板内容(FR-001)

- **Decision**: `AgentStore.scaffold(name, description)` 生成四件套:① `AGENT.md`(frontmatter:`name`/`description`/`provider`(name=deepseek,model=deepseek-chat)/`tools`(内置读文件+http+memory 基础集)/`identity`(agent_name=name, prompt=description 衍生任务指令);正文按 description 生成一段"你是 <name>,负责 <description>"的任务指令)② `scripts/README.md` 占位 ③ `skills/README.md` 占位 ④ `REFERENCE.md`(说明渐进式披露用法)。provider 选择策略:名称为 `mock` 时用 mock(整机测试),否则 deepseek。
- **Rationale**: 课件说"模板内容"未展开细节;四件套与 29 节 `daily-reconcile/` 参照物同构,保证派生可解析(`deriveProfile` 必填 name/provider.name)。
- **Alternatives considered**: 光杆 AGENT.md——用户故事 1 要求"完整目录(四件套)",否决。

## R6. 前端结构(FR-016)

- **Decision**: 单文件 `App.vue` 内扩展(既有 SPA 无路由库):导航"Agent"项升级为 Agent 列表页(新建表单 + 表格:查看/编辑/删除),点详情进入 5 tab(基本信息/生成/文件/会话/记忆),返回列表按钮;移除"长期记忆"全局页。样式沿用 `oryxos-admin-ui` skill 的 token(深色+橙,token 表以 custom.css 为准)。
- **Rationale**: 26/28 节都是单文件结构,无路由依赖,加内部视图切换状态即可;skill 明示 30 节加 Agent 管理页复用同一套。
- **Alternatives considered**: 引入 vue-router——加依赖,现有模式不需要,否决。

## R7. 错误码映射(FR-015)

- **Decision**: 复用 `GlobalExceptionHandler` 既有映射:`InvalidRequestException`/`IllegalArgumentException`→400、`ResourceNotFoundException`→404、`ProviderUnavailableException`→503、`AgentTimeoutException`→504、兜底 500。generate-files 校验非法 → `InvalidRequestException`;防穿越越界 → `InvalidRequestException`;model 未配 → `ProviderUnavailableException`。统一 `ApiResponse` 信封,code 各自区分。
- **Rationale**: 26 节口径逐字沿用,不发明新状态码(课件 §2.5)。
