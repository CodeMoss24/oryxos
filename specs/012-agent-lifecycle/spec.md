# Feature Specification: 动态管理 Agent(一句话生成、上传即上线、免重启)

**Feature Branch**: `030-lesson30-agent-lifecycle`

**Created**: 2026-08-20

**Status**: Draft

**Input**: User description: "第30节需求:动态管理 Agent——一句话生成、上传即上线、免重启"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 新建 Agent 即上线,免重启 (Priority: P1)

运营在管理台填 `name` + `description` 点"新建",系统脚手架出一个完整 Agent 目录(`AGENT.md` + `scripts/` + `skills/` + `REFERENCE.md`,模板内容),完成派生与注册。**不重启进程**,这个 Agent 立刻出现在列表里,到 cron 点自己跑。name 冲突或校验失败时不留半个 Agent(已写的目录回滚删除)。

**Why this priority**: 这是"动态管理"的骨架——没有它,后续生成/编辑/删除都没有承载。26 节管理台"只读"的限制由此解除。

**Independent Test**: 调创建接口(或管理台表单)→ 列表立刻可见;cron 到点真实触发;创建失败时工作区无残留目录。

**Acceptance Scenarios**:

1. **Given** 系统运行中,Agent 名不与任何已注册 Agent 冲突,**When** 提交 `{name, description}` 创建,**Then** 返回 200,`AGENT.md` 落在 `.oryxos/agents/<name>/`,列表接口立刻可见,且 `scripts/`/`skills/`/`REFERENCE.md` 骨架文件一并生成
2. **Given** 提交的 name 与已存在 Agent 冲突,**When** 创建,**Then** 400 且 `.oryxos/agents/` 下不出现任何新目录
3. **Given** 注册校验失败(如引用了不存在的 provider),**When** 创建,**Then** 报错且已写的 Agent 目录被回滚,系统里没有半个 Agent

### User Story 2 - 丢目录也即上线 (Priority: P1)

运维不走 API,直接往 `.oryxos/agents/` 拷一个 Agent 目录(scp / git / 编辑器)。系统实时监听该目录:新增/修改 → 自动登记可用;删除/移走 → 自动注销。**与 API 上传殊途同归**——行为完全一致、全程免重启;单个坏目录不拖垮监听。

**Why this priority**: "实时监听"是"上传即上线"名符其实的技术保证;手工路径与 API 路径走同一段注册代码是本节的第一契约。

**Independent Test**: 运行中拷入目录 → 几秒内列表出现;删掉目录 → 列表消失;拷入坏目录 → 其余 Agent 不受影响。

**Acceptance Scenarios**:

1. **Given** 系统运行中,**When** 往 `.oryxos/agents/` 拷入一个合法 Agent 目录,**Then** 几秒内 `GET /api/v1/agents` 出现它,无需重启
2. **Given** 系统运行中,**When** 从 `.oryxos/agents/` 删除该目录,**Then** 从列表注销,定时句柄一并取消
3. **Given** 拷入一个 `AGENT.md` 非法的坏目录,**When** 监听器处理,**Then** 只记日志跳过,其余 Agent 照常注册、监听不中断

### User Story 3 - 一句话生成并预览 (Priority: P1)

运营对**已存在的 Agent** 说一句描述(如"每天早上九点查北京天气,把穿搭建议发到团队群"),系统用大模型生成一份 `AGENT.md` 草稿,**原样返回、不落盘、不注册**,页面可改(尤其 cron / tools 敏感项)后再"保存并生效"。生成本身是一次 LLM 调用,落审计。LLM 产出非法定义 → 400 带可读原因;生成 provider 未配 model → 503 可读错误。

**Why this priority**: "一句话生成"是本节唯一新面孔,两步人在环里(生成→预览确认)防 LLM 把 cron 理解错、tools 给多权限。

**Independent Test**: 调生成接口 → 拿到可解析的 `AGENT.md` 草稿文本;列表不受影响(没注册);保存接口把(可能改过的)文件写入并重注册。

**Acceptance Scenarios**:

1. **Given** 系统配好生成 provider/model,**When** 对某 Agent 提交一句描述,**Then** 返回 `{相对路径 → 内容}` 草稿,能被解析成合法 Agent 定义,且未落盘、未注册
2. **Given** LLM 产出非法定义,**When** 生成,**Then** 400 带可读原因
3. **Given** 生成 provider 已配、model 留空,**When** 生成,**Then** 503 可读错误,不向 LLM 端点发空 model
4. **Given** 用户改过草稿后提交保存,**When** `POST .../files` 且 `AGENT.md` 可解析,**Then** 文件写入目录、Agent 重注册,写入即生效;`AGENT.md` 不可解析 → 400 且目录不被写坏

### User Story 4 - 详情可查、可改、可删 (Priority: P2)

管理台点开一个 Agent:看基本信息(provider/model/tools/定时)、看它的文件并可编辑保存、跟它的**固定会话**对话(上下文可累积)、看它自己的 `MEMORY.md`(专属记忆,`save_memory` 由 Agent 自己写入)。编辑 `AGENT.md` 走"写 + 校验 + 重注册",`schedules` 变更先注销旧句柄再注册新的。删除时先停定时 → 移出索引 → 整个目录归档到 `.oryxos/archive/`(不物理删,历史审计仍可追溯)。

**Why this priority**: 管理台从"能看"升级成"真能管",per-agent 记忆与固定会话让"这个 Agent"的概念完整。

**Independent Test**: 详情接口返回 AgentView;改文件后行为即时变化;删 Agent 后列表消失、目录进 archive/。

**Acceptance Scenarios**:

1. **Given** 一个已注册 Agent,**When** `GET /agents/{name}`,**Then** 200 返回名称/描述/provider/model/tools/定时等详情;不存在的 name → 404
2. **Given** 更新正文/配置,**When** `PUT /agents/{name}`,**Then** `AGENT.md` 被覆写即时生效;`schedules` 变化时旧句柄先注销、新句柄再注册,新旧 cron 不同时跑
3. **Given** 删除 Agent,**When** `DELETE /agents/{name}`,**Then** 定时先注销 → 移出索引 → 整个目录移入 `.oryxos/archive/`,不物理删
4. **Given** Agent 的固定会话,**When** 发消息,**Then** 恒落同一条会话(channel=admin, user=console),上下文跨消息累积,`GET .../session` 返回该会话最近 ≤100 条消息
5. **Given** 管理台文件页,**When** 查看/编辑某文件,**Then** 目录树列出 agents/ 与 archive/ 下文件,点开只读展示或编辑保存;编辑到 `agents/<name>/AGENT.md` 走更新注册链路
6. **Given** `save_memory` 被该 Agent 调用,**When** 查 `GET /agents/{name}/memory`,**Then** 返回该 Agent 自己的 `MEMORY.md`;别的 Agent 的记忆互不可见

### User Story 5 - 工作区文件浏览与编辑 (Priority: P2)

运营通过"工作区"视角钻进任意 Agent 目录看它的 `AGENT.md`/`scripts/`/`skills/`/`REFERENCE.md`,也可直接编辑保存。**防目录穿越是唯一安全要点**:任何 `path` 参数解析后必须落在 `.oryxos/` 内,越界 → 400。

**Why this priority**: 管理台要"看得到文件、改得了文件",渐进式披露目录的管理价值才完整。

**Independent Test**: tree 接口返回结构;正常文件可读可写;越界路径一律 400。

**Acceptance Scenarios**:

1. **Given** 工作区有 agents/ 与 archive/,**When** `GET /workspace/tree`,**Then** 返回两棵目录树,Agent 目录可展开到文件
2. **Given** `path` 参数 `../../etc/passwd` 之类越界路径,**When** 读或写,**Then** 400 拒绝(关键回归)
3. **Given** 合法路径,**When** 读,**Then** 返回文件文本内容;**When** 写,**Then** 落盘生效

### Edge Cases

- 手工丢目录 + API 创建**同一时刻**操作同一 Agent:两条路径都汇到同一段注册代码,后到者覆写注册,不产生两份。
- 目录在 `AGENT.md` 写了一半时被监听拾取:校验失败 → 记日志跳过,监听不中断;下次变更事件再试。
- `schedules` 从有到无(或 cron 变化)后更新:`schedules` 变了必须先注销旧句柄再注册新的,否则旧 cron 跟新 cron 一起跑。
- 删除与定时触发竞态:先停定时再动索引,避免"索引没了定时还在跑"的窗口期空指针。
- 归档目录重名:同一 Agent 重复删除(或删了再建再删)→ 归档目标已存在时按时间戳加后缀,不覆盖旧归档。
- 生成接口并发调用、或生成时 Agent 不存在:对不存在 Agent 生成 → 404;并发保存同组文件 → 最后一次写入生效(单实例内写文件原子性靠顺序写,不做锁)。
- 监听目录本身被删除(`.oryxos/agents/` 整个没了):监听器捕获异常记日志,等待目录重建后恢复(或按装配策略,重启恢复)。
- 编辑 `AGENT.md` 后派生校验失败:回滚?不——写盘动作已完成,返回 400 并提示;保留用户刚写的内容(人可再改),**不自动回滚用户文件**。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 支持通过 `POST /agents` 用 `{name, description}` 脚手架出完整 Agent 目录(`AGENT.md` + `scripts/` + `skills/` + `REFERENCE.md`,模板内容),随后派生 Profile 并注册;`schedules` 非空则注册定时。name 冲突 → 400 且一个目录都不写;注册失败 → 回滚已写目录,不留半个 Agent。
- **FR-002**: 系统 MUST 提供 `GET /agents`(列表)与 `GET /agents/{name}`(详情);不存在的 name → 404。
- **FR-003**: 系统 MUST 支持 `PUT /agents/{name}` 覆写 `AGENT.md` 并重注册;`schedules` 变化时先注销旧句柄、再注册新的,新旧 cron 不同时跑。
- **FR-004**: 系统 MUST 支持 `DELETE /agents/{name}`,顺序为:注销定时 → 移出索引 → 整个目录移入 `.oryxos/archive/`(不物理删)。
- **FR-005**: `POST /agents/{name}/invoke` 保持 26 节无状态调用语义不变。
- **FR-006**: 系统 MUST 支持 `POST /agents/{name}/generate-files {description}`:一次 LLM 调用(落 `llm_calls` 审计)产出 `AGENT.md` 草稿 → 校验可解析(非法 → 400 可读原因)→ 返回 `{相对路径 → 内容}` 给前端预览可改,**不落盘、不注册**;输出 provider 沿用该 Agent 既有 provider;多余代码围栏须剥掉。
- **FR-007**: 系统 MUST 支持 `POST /agents/{name}/files {files}` 保存一组文件:先校验 `AGENT.md` 可解析(非法 → 400,不写坏目录)→ 写入 → 覆写后重注册(`schedules` 变更先注销旧句柄),写入即生效。
- **FR-008**: 系统 MUST 让每个 Agent 拥有专属记忆(`agents/<name>/MEMORY.md`):`save_memory`/`recall_memory` 在 ReAct 执行期间按当前 Agent 落到对应文件;`GET /agents/{name}/memory` 返回该 Agent 记忆;**移除**全局 `GET /api/v1/memory` 端点。
- **FR-009**: 系统 MUST 为每个 Agent 提供固定会话(`channel=admin`、`user=console`、profile=Agent 名,`getOrCreate` 幂等):`GET /agents/{name}/session` 返回该会话最近 ≤100 条消息;`POST /agents/{name}/session/messages {content}` 往固定会话发消息触发 ReAct,上下文跨消息累积。
- **FR-010**: 系统 MUST 提供 `GET /workspace/tree`:返回 `.oryxos/agents/`(每 Agent 一目录)与 `.oryxos/archive/` 的目录树。
- **FR-011**: 系统 MUST 提供 `GET /workspace/file?path=`:读文件文本;**必做防目录穿越**——path 解析为绝对路径后必须落在 `.oryxos/` 内(`normalize()` 后 `startsWith(root)`),越界 → 400。
- **FR-012**: 系统 MUST 提供 `POST /workspace/file {path, content}`:写文件,同一套防穿越;目标为 `agents/<name>/AGENT.md` 时走"写 + 校验 + 重注册"链路;其余文件直接写盘。
- **FR-013**: 系统 MUST 实时监听 `.oryxos/agents/`:启动全量扫一遍,之后 JDK WatchService 监听;目录新增/修改 → 调与 API 创建**同一段**注册代码,删除/移走 → 注销;单个坏目录不拖垮监听(记日志跳过)。
- **FR-014**: 系统 MUST 提供生成端点配置:`oryxos.author.provider`(缺省取 `oryxos.providers` 第一个)与 `oryxos.author.model`;model 留空 → 生成端点返回 503 可读错误,不得向 LLM 端点发空 model。
- **FR-015**: 错误码沿用 26 节口径:400(name 已存在 / 字段非法 / 引用不存在的 provider / LLM 产出非法)、404(查改删不存在)、503(model 未配)、504(调用超时),统一 `ApiResponse` 信封,错误各自 `code` 区分。
- **FR-016**: 管理台 MUST 提供 Agent 列表页(含"新建 Agent"= 填 name + description)与详情页 5 个 tab(基本信息 / 生成 / 文件 / 会话 / 记忆);删除前二次确认;出错显示信封 message;只调既有与本节新增端点。

### Key Entities *(include if feature involves data)*

- **Agent**: `.oryxos/agents/<name>/` 目录,`AGENT.md`(frontmatter = profile,正文 = 指令)+ 可选 `scripts/`/`skills/`/`REFERENCE.md`/`MEMORY.md`;真相源只有文件系统。
- **AgentFile(生成/保存草稿)**: 相对路径 → 文本内容 的映射,生成端点产出的预览载体,落盘前可人改。
- **WorkspaceNode(tree)**: agents/ 与 archive/ 下的目录树节点(目录可展开到文件),供文件浏览器渲染。
- **AgentView(详情)**: Agent 的可读视图:name/description/provider/model/tools/schedules 等。
- **固定会话**: 每 Agent 一条,channel=admin + user=console + profile=Agent 名,幂等 getOrCreate。
- **专属记忆**: `agents/<name>/MEMORY.md`,与 AGENT.md 同目录,Agent 自己经 `save_memory` 写入。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 创建 Agent 后 **无需重启进程**,秒级出现在 `GET /agents` 列表,且到 cron 点自动运行(harness 用 mock 验证注册链路,真链路人工验收)。
- **SC-002**: 三种录入路径(API 创建 / 手工丢目录 / 启动扫描)对同一 Agent 目录产生**行为完全一致**的注册结果——harness 断言"同一段 register(agentDir)"。
- **SC-003**: 目录穿越攻击 100% 被拦:任何越界 path 均 400,harness 有 `../../etc/passwd` 回归。
- **SC-004**: 删除可追溯:DELETE 后目录在 `.oryxos/archive/`,历史 `llm_calls`/`tool_invocations` 仍可查(不物理删)。
- **SC-005**: `mvn clean verify` 全绿(含 P3C/SpotBugs/FindSecBugs/PMD);课件 harness 五个测试类全部存在且关键回归逐个对号。
- **SC-006**: 管理台可完成全流程:一句话生成 → 预览 → 保存并生效 → 看它自己跑 → 文件浏览 → 会话对话 → 记忆查看 → 编辑 → 删除(人工验收清单)。

## Assumptions

- 内网假设:本组端点不做认证鉴权,扩展阶段随 API Key/RBAC 补(课件"先别做"逐项照搬)。
- 创建走 JSON 脚手架(模板内容),**不做** multipart/zip 上传;带脚本的复杂 Agent 走手工丢目录路径。
- 不做 Agent 启用/停用状态位、dry-run 试跑、版本历史、多轮追问式细化、工作区上传入口(课件"先别做"逐项照搬)。
- 文件浏览器支持编辑保存(5.2.3 增补,原设计只读已废弃,以实现为准)。
- "一句话生成整个新 Agent"端点已废弃(5.1):生成下沉为"对已存在 Agent 的按需重生成",创建 = 模板脚手架(以实现为准)。
- 依赖前序交付:29 节 `AgentLoader`/`ProfileRegistry`/`AgentScheduler.registerProfile` + `scheduledTasks` 句柄表(本节补 `unregisterProfile`);27 节 `ProviderService.chat`;26 节 `AgentApiController.invoke`/`ApiResponse`/`GlobalExceptionHandler`;22 节 Memory 三档后端;27 节 `OryxOsRuntime`。
- 外部依赖:JDK 内置 `java.nio.file.WatchService` 做目录监听,无新增第三方依赖(软门禁 6 不受触发)。
- 生成/编辑 `AGENT.md` 后派生校验失败:不自动回滚用户文件,返回 400 保留内容供人再改(区别于 create 的失败回滚)。
