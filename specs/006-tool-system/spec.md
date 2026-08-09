# Feature Specification: Tool 体系

**Feature Branch**: `020-lesson20-tool`

**Created**: 2026-08-09

**Status**: Draft

**Input**: User description: "第20节需求：Tool 体系——让 Agent 真正能动手干事的那双手。Provider 让 Agent 会调模型，ReAct 让它会思考，但到现在它还只会'想'和'说'。Tool 是 Agent 的手——LLM 负责决定'调哪个工具、传什么参数'，OryxOS 负责把工具真正执行掉、再把结果递回给 LLM。"

## Clarifications

### Session 2026-08-09

- Q: 内置工具(含新加的 5 个扩展工具)按哪种风格实现?存量已交付的是手写统一抽象实现,课件六.5 写的是走 `@Tool` 注解管道 → A: Option A——重构存量内置工具为 `@Tool` 注解风格,新增工具也走 `@Tool` 管道(课件字面方案)。存量 FileTools/ShellTools/HttpTools 重构为 `@Tool` 注解方法,新增 5 个扩展工具同样走 `@Tool` 管道;统一注解包装适配器是内置工具与方式三 Plugin Tool 共用的桥梁;重构必须保持工具名、参数说明、执行行为(含首步安全校验)逐字保真。
- Q: 项目锁定的 Spring AI 1.0.0-M4 本地核实**无 `@Tool` 注解**(该注解 M5+ 才引入),课件字面方案无法执行(软门禁 #5);升级 M6 会 API 断裂波及 16/17 节 → A: M4 原生 FunctionCallback 管道——内置工具写成普通方法,装配处用 `FunctionCallback.builder().method(...)` 包装,schema 从方法签名自动生成(不再手写);`AnnotatedToolAdapter` 把 FunctionCallback 包装成统一抽象注册。本质不变(零手写 schema、启动自动扫描、统一适配器包装),仅"注解字面"换成"builder 装配"。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent 通过内置工具真正干活，越界被拦 (Priority: P1)

运维用户让 Agent "查一下服务器上的配置文件"或"发个请求问天气"，Agent 决定调用内置工具读文件、跑命令、发 HTTP 请求，真正拿到外部世界的结果回给用户；当目标不在白名单内（文件路径、命令、域名越界）时，请求在真正发出 IO 前被拦下，Agent 得到明确的失败信息而不是一次危险操作。

**Why this priority**: 这是 Tool 体系存在的根本原因——没有内置工具，Agent 只是会说话的模型；没有白名单拦截，工具就是事故入口。P1 覆盖"正常能跑通 + 越界会被拦"这条最短验收链路。

**Independent Test**: 对每个内置工具分别执行合法输入与越界输入，验证合法输入返回真实结果、越界输入在 IO 发生前被拒绝。

**Acceptance Scenarios**:

1. **Given** 一个合法文件路径，**When** Agent 调用读文件工具，**Then** 返回文件内容。
2. **Given** 一个白名单之外的路径（如上级目录穿越），**When** Agent 调用读/写文件工具，**Then** 调用被拦截并返回明确错误，文件系统未被触碰。
3. **Given** 一个白名单之外的命令/域名，**When** Agent 调用 shell 工具或 HTTP 工具，**Then** 调用被拦截并返回明确错误，命令未执行、请求未发出。

---

### User Story 2 - 声明即接入：外部 MCP server 暴露的能力自动可用 (Priority: P1)

企业运维负责人在 `.oryxos/mcp_servers.yaml` 里声明一个内部 MCP server（进程名、启动参数、环境变量），OryxOS 启动时自动连接、拉取工具清单、全部注册为可用工具；其中某个 server 失联时，OryxOS 只记录告警并继续启动，其余 server 的工具照常注册——外部依赖的可用性不拖垮自身的可用性。

**Why this priority**: 这是 Plugin Tool 方式二的核心价值，也是三个验收 Demo（科技日报、GitHub 日报）硬依赖的接入通道——"轻代码接入企业自有系统"是核心阶段业务能力的最大来源。

**Independent Test**: 配置一个正常 server 和一个失联 server，启动后验证正常 server 的工具全部可用、失联 server 不导致启动失败。

**Acceptance Scenarios**:

1. **Given** mcp_servers.yaml 声明了一个可达的 MCP server，**When** OryxOS 启动，**Then** 该 server 暴露的每个工具都注册为统一形态的可用工具，Agent 可调用。
2. **Given** mcp_servers.yaml 声明了两个 server 其中一个失联，**When** OryxOS 启动，**Then** 失联 server 只记录告警（不抛异常、不中断启动），可达 server 的工具照常全部注册。
3. **Given** Agent 调用某个 MCP 工具，**When** 执行，**Then** 调用参数原样转发给对应 server，结果包成统一结果格式返回，失败时标记为可重试。

---

### User Story 3 - Java @Tool 工具放进工程即注册 (Priority: P2)

业务方开发了一个带 @Tool 注解的 Java 方法（如"查库存"），作为 Spring Bean 放进工程。OryxOS 启动时自动扫描注册，Agent 在对话中直接调用它——进程内直调，性能最好，适合深度集成。

**Why this priority**: 这是方式三"重代码深度集成"的唯一接入路径。优先级 P2 因它要求写 Java 代码、门槛最高，主推仍是方式一/二，但方式三是存量 Java 团队最自然的扩展方式。

**Independent Test**: 写一个带 @Tool 注解的示例方法 Bean，启动后工具列表可见、Agent 调用返回预期结果。

**Acceptance Scenarios**:

1. **Given** 工程里有一个带 @Tool 注解的 Spring Bean 方法，**When** OryxOS 启动，**Then** 该方法被包装成统一形态的工具注册，契约三件套（名称/描述/参数说明）完整。
2. **Given** 上述工具已注册，**When** Agent 在对话中调用它，**Then** 参数透传、返回结果、审计落库。

---

### User Story 4 - 业界级扩展工具补齐日常高频动作 (Priority: P2)

Agent 需要改文件里一段唯一出现的文本（而不是整文件重写）、按正则搜内容、按通配找文件、中途向用户提问请求确认、上网搜资料。五个扩展工具补齐这些高频动作，与既有工具共用同一套注册、安全校验与审计链路。

**Why this priority**: 缺这五个工具 Agent 在日常任务里明显笨手笨脚——改一行配置要重写整个文件、找文件只能层层列目录硬翻、危险操作没人拍板、日报类 Agent 没有上网入口。P2 因为它们都是"基础工具"而非业务能力，是主流 Agent 产品的公约数。

**Independent Test**: 对每个扩展工具执行合法输入与越界输入，验证正常路径与拦截路径。

**Acceptance Scenarios**:

1. **Given** 文件里某段文本唯一出现，**When** Agent 调用编辑工具把它替换为新文本，**Then** 替换成功且文件其余内容一字不动；**Given** 该文本缺失或出现多次，**When** 调用，**Then** 报错且文件未被修改。
2. **Given** 一个目录，**When** Agent 按正则搜索内容，**Then** 返回 `文件:行号:内容` 格式结果，超过条数上限截断并注明，二进制/非 UTF-8 文件跳过不中断。
3. **Given** Agent 需要向用户提问，**When** 当前渠道支持人机交互（如 CLI），**Then** 用户输入被带回给 Agent；**Given** 渠道无人值守（定时任务/无状态调用），**When** 提问，**Then** 返回"不支持交互"的明确错误，绝不静默卡住。
4. **Given** Agent 调用搜索工具，**When** 执行，**Then** 第一件事同样过域名白名单校验（不因名字叫"搜索"就绕过治理），结果渲染成模型可读文本。
5. **Given** Agent 调用任何内置/扩展工具，**When** 成功或失败，**Then** 均写入既有审计记录。

---

### Edge Cases

- 工具的输入参数说明（schema）缺失时，Provider 翻译 Function Calling 会卡死——如何保证注册的每个工具三件套（名称/描述/参数说明）都不缺？
- 按 Profile 的 tools 字段过滤时，多过滤出一个（没过滤干净）和少过滤一个（过滤过头）都算错——如何保证子集精确？
- MCP server 连接超时/进程不存在/协议握手失败时，是否只告警不炸启动？
- MCP 工具执行失败（server 报错、超时）时，错误如何标记（是否可重试）？
- 编辑工具要求"旧文本唯一匹配"——找不到、出现多次都报错且不改文件，如何保证？
- 超大目录 grep/glob 一次吐满上下文怎么办？grep 遇到二进制文件怎么办？
- ask_user 在无人值守渠道调用时，如何避免静默卡住？
- web_search 的搜索目标域名不在白名单时，是否与 http_get 一样被拦？
- 工具调用超时（如 shell 30 秒）如何处理？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供统一的工具抽象（OryxTool），约定四个方法：getName / getDescription / getInputSchema（参数 JSON Schema，缺了它 Provider 翻译 Function Calling 直接卡死）/ execute（接收 JSON 输入，返回统一结果）。
- **FR-002**: 工具执行结果必须携带四样信息：成功标识、结果内容、错误信息、是否可重试——ReAct 循环拿到失败结果时能判断这错值不值得再调一次。
- **FR-003**: 系统必须提供 ToolRegistry 统一管理所有工具：内置工具、@Tool 注解的 Java 工具、MCP 工具都以统一抽象形态注册进来；ReAct 循环只跟统一抽象打交道，完全不感知来源。
- **FR-004**: 系统必须支持按 Profile 的 tools 字段从 Registry 过滤出该 Profile 可用的工具子集；子集必须精确匹配声明列表——多一个（没过滤干净）和少一个（过滤过头）都是错。
- **FR-005**: 系统必须提供九个内置工具覆盖最短链路：文件（读/写/列目录）、shell 命令（带超时）、HTTP GET/POST、记忆存取、通知推送；每个内置工具的 execute 第一步必须先过安全校验（文件读/文件写/shell 命令/HTTP 请求四类动作），校验不过直接拦下、不发出任何真实 IO。
- **FR-006**: 系统必须支持方式二 MCP 接入：`.oryxos/mcp_servers.yaml` 声明 server 的 name/transport/command/env；启动时连接所有声明的 server、拉取工具清单、每个工具包装成统一抽象注册。
- **FR-007**: MCP server 连接失败（失联/超时/进程异常）时，系统只记录告警并继续启动，其余 server 的工具照常注册——外部依赖的可用性不是自己的可用性。
- **FR-008**: MCP 工具执行时，调用参数必须原样转发给对应 server，结果包装成统一结果格式返回；执行失败时结果必须标记为可重试。
- **FR-009**: 系统必须支持方式三 @Tool 接入：带 @Tool 注解的 Spring Bean 方法启动时自动扫描、自动生成参数说明、包装成统一抽象注册，Agent 可调用。
- **FR-010**: 系统必须提供 edit_file 工具：把文件里一段唯一出现的旧文本替换为新文本；旧文本找不到或出现多次都报错且文件一字不动。
- **FR-011**: 系统必须提供 grep 工具：在路径下按正则搜内容，返回 `文件:行号:内容` 格式；结果有条数上限（200 条），超出截断并注明；二进制/非 UTF-8 文件跳过不中断整次搜索。
- **FR-012**: 系统必须提供 glob 工具：按通配符（如 `**/*.yaml`）找文件路径，结果同样有条数上限。
- **FR-013**: 系统必须提供 ask_user 工具，通过用户交互抽象向当前渠道的用户提问并带回回答；抽象拿不到回答时必须抛异常（明确报错），绝不静默卡住；无人值守渠道挂"不支持交互"的实现，CLI 等可交互渠道挂读终端实现。
- **FR-014**: 系统必须提供 web_search 工具：调用搜索引擎检索并返回结果；执行第一步必须先过 HTTP 请求白名单校验（与 http_get 同一套治理，不因名字叫"搜索"就绕过）；具体搜索引擎抽成可替换的提供方接口。
- **FR-015**: 所有工具调用的成功/失败都必须走既有审计路径落库（工具执行审计 day one 已建立，本节新增工具自动复用，不新增审计逻辑）。
- **FR-016**: 注册进 Registry 的每个工具，名称/描述/参数说明三件套都必须非空——用遍历 Registry 的契约测试自动核查，新增工具自动纳入检查范围。

### Key Entities *(include if feature involves data)*

- **Tool（能力单元）**: Agent 可调用的外部能力；核心属性：名称（LLM 点名用）、描述（给 LLM 看）、输入参数说明（JSON Schema）、执行入口；来源：内置 / @Tool Java 注解 / MCP 外部 server 三种。
- **ToolRegistry（工具清单）**: 统一持有所有已注册工具；按 Profile 声明的工具名过滤出子集。
- **MCP server 配置**: 声明在 `.oryxos/mcp_servers.yaml`；每项含 name、transport（连接方式）、command（启动命令）、env（环境变量）。
- **Profile.tools（工具白名单）**: Profile 字段声明该 Agent 可用工具名列表，运行时从 Registry 过滤；核心阶段唯一的工具治理雏形。
- **用户交互通道**: ask_user 的承载抽象——能拿到回答的渠道（如终端）与拿不到的渠道（无人值守）行为不同。
- **搜索提供方**: web_search 的引擎抽象——核心阶段挂免 key 实现，换引擎不改工具本身。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 所有自动化测试通过 `mvn test` 以零失败运行，包括：OryxToolContractTest（参数化遍历每个已注册工具，名称/描述/参数说明三件套非空——任何工具漏掉 getInputSchema 立刻红）、ToolRegistryTest（三种来源的工具都注册为统一抽象；按 Profile 过滤后子集与声明列表精确相等）、FileToolsTest / ShellToolsTest / HttpToolsTest（各自覆盖"正常能跑通 + 越界会被拦"两条）、McpToolAdapterTest / McpClientServiceTest（mock 连接：工具被包装注册、参数原样转发、结果包装正确、连接失败只告警且其余工具照常注册、启动不炸）。
- **SC-002**: 九个内置工具 + 五个扩展工具全部注册进 Registry，且每个工具的 execute 首步都过安全校验。
- **SC-003**: 声明一个失联的 MCP server 启动不失败、其余 server 工具可用——"外部依赖的可用性不是自己的可用性"。
- **SC-004**: 编辑工具"唯一匹配才改"语义可验证：唯一匹配改成功且文件其余内容不动；缺失/多次匹配报错且文件一字不动。
- **SC-005**: ask_user 在无人值守渠道返回明确"不支持交互"错误，不卡死、不静默。
- **SC-006**: 前序节全部测试（Provider / ReAct / CLI / Notify）回归绿，跨节契约无破坏。

## Assumptions

- 前序节已交付统一工具抽象（含 getInputSchema）、统一结果类型、ToolRegistry、执行/审计链路（ToolExecutor）、Profile.tools 字段与过滤接线、通知工具与出站适配器、Sandbox 前向接口（含应用层白名单实现并已装配）——本节在其上补齐注册/过滤/扩展/MCP 接线，不重造已存在的概念。
- MCP 方式二依赖外部 SDK（io.modelcontextprotocol.sdk，本地仓库 0.7.0 已核实存在，含 stdio/SSE 传输、工具清单、工具调用 API），作为本节新增依赖。
- web_search 核心阶段挂免 key 的 DuckDuckGo Instant Answer API，其域名需列入 HTTP 域名白名单；换 Google/Bing/Tavily 只新增提供方实现。
- 工具执行"越界会被拦"的测试用真实白名单实现 + 受控配置（测试内构造，不依赖全局配置），不 mock 掉安全校验本身。
- MCP 测试全部 mock 连接层，不碰真实网络、不启动真实子进程。
- 内置工具按 M4 原生 FunctionCallback 管道统一重构（用户已确认）：存量 FileTools/ShellTools/HttpTools 重构为普通方法 + 装配处 `FunctionCallback.builder().method(...)` 包装（schema 从方法签名自动生成，不再手写 schema 字符串），新增 5 个扩展工具同样走此管道；`AnnotatedToolAdapter` 把 FunctionCallback 包装成统一抽象，是内置工具与方式三 Plugin Tool 共用的桥梁；重构保持工具名、参数说明、执行行为（含首步安全校验）逐字保真，行为回归由既有契约测试与功能测试兜底。NotifyTools 保持 19 节形态（本节只完成注册，不重构）。Spring AI 保持 1.0.0-M4 锁定版本不升级。
- 工具名称、配置键等已定字面量（read_file / write_file / list_dir / shell / http_get / http_post / notify / save_memory / recall_memory、mcp_servers.yaml 字段名、Sandbox 动作四值）逐字保真，不做任何改名。
