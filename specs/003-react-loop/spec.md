# 功能规格说明书：ReAct 循环引擎

**功能分支**: `017-lesson17-react`

**创建日期**: 2026-08-08

**状态**: 草案

**输入**: 用户描述："第17节需求：ReAct 循环——Agent 核心调度引擎。把'想一步、做一步、看结果'串成一个循环，模型想下一步该干嘛、调个工具去做、拿到结果看一眼，不够就再来一轮，够了就给最终答复。"

## 用户场景与测试 *(必填)*

### 用户场景 1 - 简单对话一轮收尾 (优先级: P1)

用户发一条不需要工具就能回答的消息（如"你好"），Agent 调一次大模型得到回复，无需执行任何工具，直接返回最终响应给用户。

**为什么是这个优先级**: 这是最基本的使用场景——大部分对话不需要工具调用。如果这条链路不通，整个 Agent 无法工作。

**独立测试方式**: Mock Provider 返回不带工具调用的响应，验证 ReActLoop 恰好调用一次 Provider 并返回正确文本。

**验收场景**:

1. **假设** Agent 已配置好 Profile 和 Provider，**当** 用户发送"你好"，模型返回不带工具调用的回复，**那么** 循环恰好执行 1 轮即返回模型回复文本。
2. **假设** 模型返回的回复无工具调用，**当** ReActLoop 收到该响应，**那么** 响应文本被追加到 Session 对话历史，循环正常终止。

---

### 用户场景 2 - 多轮工具调用完成任务 (优先级: P1)

用户提出需要外部信息的任务（如"查天气，帮我决定穿什么"），Agent 第一轮调工具获取数据，拿到结果后第二轮给出最终建议。

**为什么是这个优先级**: 这是 ReAct 区别于普通 chatbot 的核心价值——Agent 能自主调用工具完成复杂任务。

**独立测试方式**: Mock Provider 第一轮返回工具调用、第二轮返回最终文本，验证 ToolExecutor 被调用、工具结果回填 Session、第二轮 Prompt 包含上轮上下文。

**验收场景**:

1. **假设** Provider 第一轮返回工具调用请求，**当** ToolExecutor 执行工具返回结果，**那么** 工具结果被追加到 Session 对话历史，循环进入下一轮。
2. **假设** 第二轮 Provider 返回不带工具调用的最终回复，**当** ReActLoop 收到响应，**那么** 返回最终回复文本，总共恰好 2 轮。
3. **假设** 一次响应包含多个工具调用，**当** ToolExecutor 逐个执行，**那么** 每个工具结果都追加进 Session（顺序执行，不并行）。

---

### 用户场景 3 - 最大轮数强制终止 (优先级: P2)

模型因某种原因反复要求调工具、始终不给最终答复，循环在达到配置的最大轮数（默认 10）后强制终止，返回明确的终止消息，不会陷入死循环。

**为什么是这个优先级**: 这是生产环境的安全兜底——防止单次请求无限消耗资源。虽然不常触发，但缺失会导致系统可用性问题。

**独立测试方式**: Mock Provider 每次都返回工具调用（永不收敛），验证循环恰好在 maxIterations 次后停止，返回含"达到最大轮数"的消息。

**验收场景**:

1. **假设** maxIterations 设为 10，Provider 每轮都返回工具调用，**当** 循环运行，**那么** 恰好调用 10 次 Provider 后停止，返回含"达到最大轮数"的终止消息。
2. **假设** Profile 中 maxIterations 为自定义值（如 5），**当** 循环触发最大轮数终止，**那么** 以该自定义值而非默认值 10 终止。

---

### 用户场景 4 - 上下文管理与截断 (优先级: P2)

多轮对话中对话历史不断增长，系统按 maxHistoryTurns（默认 20）截断历史消息，确保不会因上下文过长导致 LLM 调用失败。

**为什么是这个优先级**: 上下文溢出会导致 LLM API 报错或成本失控，是 ReAct 循环能稳定运行的工程必要条件。

**独立测试方式**: 构造超过 maxHistoryTurns 的 Session 历史，验证 Prompt 中只包含最近 N 轮消息。

**验收场景**:

1. **假设** Session 有 30 轮历史消息，maxHistoryTurns 为 20，**当** PromptBuilder 组装 Prompt，**那么** 只包含最近 20 轮消息，前 10 轮被截断。
2. **假设** Session 历史消息少于 maxHistoryTurns，**当** PromptBuilder 组装 Prompt，**那么** 全部历史被保留。

---

### 用户场景 5 - 审计记录完整性 (优先级: P1)

每次工具执行的成败都落库审计（包括成功和失败两种情况），失败时记录原因，确保事后可追溯。

**为什么是这个优先级**: 可审计是 OryxOS 的差异化定位，审计数据地基 day one 就要立起来。如果审计不完整，"可审计"这个卖点就不成立。

**独立测试方式**: 分别模拟工具执行成功和失败场景，验证 tool_invocations 表中 success 字段正确、失败时 error_message 有内容。

**验收场景**:

1. **假设** 工具执行成功，**当** ToolExecutor 执行完毕，**那么** 审计记录 success=true，error_message 为空。
2. **假设** 工具执行抛出异常，**当** ToolExecutor 捕获异常，**那么** 审计记录 success=false，error_message 包含异常原因，异常继续上抛（不吞）。

---

### 用户场景 6 - AgentService 统一编排与 ProfileContext 安全 (优先级: P1)

三种触发源（CLI/Web/Scheduler）通过统一入口 AgentService.process() 发起处理，入口处设置当前 Profile 上下文（ThreadLocal），出口处（含异常路径）确保清除，防止线程复用时的上下文串号。

**为什么是这个优先级**: ThreadLocal 泄漏是最阴险的一类 bug——单请求测试永远不报错，只在并发复用时才暴露。必须有机制保证 finally 清除。

**独立测试方式**: 模拟 ReActLoop 抛异常，验证 AgentService 的 finally 块清掉了 ProfileContext。

**验收场景**:

1. **假设** 正常处理流程，**当** AgentService.process 执行期间，**那么** ProfileContext 可取到当前 Profile。
2. **假设** ReActLoop.run() 抛出异常，**当** AgentService.process 捕获并上抛，**那么** finally 块中 ProfileContext 被清除（get() 返回 null）。
3. **假设** 处理成功完成，**当** AgentService.process 返回，**那么** Session 被持久化。

---

### 边界情况

- 模型返回的工具名称在 ToolRegistry 中不存在时，ToolExecutor 应返回明确的"Tool not found"消息并记日志，不抛异常中断循环。
- ProviderService.chat() 调用失败（网络超时等），异常上抛，但审计记录先落 llm_calls（success=false）。
- Session 没有历史消息（首轮对话），PromptBuilder 正常组装仅含 system prompt 的 Prompt。
- maxIterations 和 maxHistoryTurns 取 Profile 配置值，未配置时分别回退到默认值 10 和 20。

## 功能需求 *(必填)*

### 功能需求

- **FR-001**: 系统必须提供 ReActLoop 循环引擎，接收 Session、用户消息、Profile，内部 for 循环最多 maxIterations 次（默认 10），每次迭代依次：组装 Prompt → 调 Provider → 判断是否有工具调用 → 无则返回最终文本 → 有则逐条执行工具 → 结果回填 Session → 继续下一轮。
- **FR-002**: 每一轮 LLM 响应和工具执行结果必须累积到 Session 对话历史，确保审计可追溯且下一轮能接入上轮上下文。
- **FR-003**: PromptBuilder 必须按固定顺序组装每轮 Prompt：① system prompt（含角色设定 + Bootstrap + AGENT.md 正文，末尾附当前日期时间）② 长期记忆注入 ③ 对话历史（按 maxHistoryTurns 截断，默认 20）④ 可用工具列表。
- **FR-004**: ToolExecutor 必须从 ToolRegistry 查找工具并执行，成功写 tool_invocations 审计记录（success=true），失败也写（success=false 带 error_message），工具执行异常不吞。
- **FR-005**: AgentService 作为统一编排入口，process() 方法必须：取 Profile → ProfileContext.set() → ReActLoop.run() → 持久化 Session → finally 中 ProfileContext.clear()。异常路径下 finally 也必须执行清除。
- **FR-006**: ContextLoader 必须按 Profile 的 bootstrap 字段读取配置文件拼 system prompt，每次 build 都重新读不缓存，Bootstrap 文件缺失至少 WARN 级别日志。
- **FR-007**: 系统禁止使用 Spring AI 的自动 tool 执行机制，Tool 调度和执行完全由 ReActLoop + ToolExecutor 控制。

### 关键实体

- **循环上下文（Loop Context）**: 一次 ReAct 执行的状态机——当前迭代次数、Session 引用、Profile 配置、是否已终止。
- **工具调用记录（Tool Invocation Record）**: 单次工具执行的审计记录——关联 Session、工具名称、输入参数、执行结果、成功/失败标志、失败原因、执行耗时、时间戳。
- **Prompt 组件（Prompt Components）**: 发给 LLM 的消息集合——system prompt 块、Memory 块、截断后的对话历史、工具列表块。

## 成功标准 *(必填)*

### 可衡量成果

- **SC-001**: 无需工具调用的简单对话在 1 轮内完成，Agent 返回正确响应文本。
- **SC-002**: 需要工具调用的多轮任务能正确执行工具并将结果回填，最终给出基于工具结果的回复。
- **SC-003**: 最大轮数兜底机制可靠——模型反复要求调工具时恰好在配置轮数后终止，绝不无限循环。
- **SC-004**: 审计记录完整率 100%——每次工具执行都有对应的 tool_invocations 记录，成功/失败均有区分。
- **SC-005**: ProfileContext 泄漏率为零——异常路径下 finally 清除可靠验证通过。
- **SC-006**: 所有自动化测试（`mvn test`）全绿，覆盖上述 6 个用户场景及边界条件。

## 假设

- 第 16 节 Provider 模块已交付：ProviderService、Profile、ProfileRegistry、LlmResponse、Prompt、LlmCallEntity 及审计表 llm_calls。
- Session 和 Message 类已定义，支持 append 操作和对话历史管理。
- ToolRegistry 已提供按名查找 Tool 的能力。
- MemoryService 接口已定义（Memory 模块后续实现），ReAct 阶段可通过接口调用但具体实现可能为 no-op。
- Sandbox 检查机制在第 24 节实现，本节 ToolExecutor 保留 Sandbox 调用位（留 TODO 标记）。
- 工具并行调用、Agent 间委托、流式输出、上下文压缩均不在核心阶段范围内。
- 数据库为 SQLite，手工建表脚本（schema.sql）维护 DDL。