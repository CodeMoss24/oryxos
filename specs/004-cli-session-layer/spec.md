# 功能规格说明书：CLI 命令行入口与 Session 会话层

**功能分支**: `018-lesson18-cli`

**创建日期**: 2026-08-09

**状态**: 草案

**输入**: 用户描述："第18节需求：CLI 命令行入口 + Session 会话层——OryxOS 的第一个'人推'入口，与所有入口共用的会话持久化地基。CLI 是消息进出的门，不是干活的人：读输入、交给引擎、打印结果三步，自己不想、不调模型、不执行工具。会话层（SessionManager + sessions 表）是所有入口（CLI/Web/定时）共用的对话持久化地基，出口径问题最难查，要在这节钉死。"

## 用户场景与测试 *(必填)*

### 用户场景 1 - 终端交互式对话（chat）(优先级: P1)

开发者在终端运行 `oryxos chat`，进入交互式对话：程序打印提示符，开发者输入一句话，Agent 处理后把回复打印出来，如此一问一答多轮；输入 `/quit` 退出。会话按 channel+user+profile 三元组自动获取或创建，多轮对话累积在同一会话里。

**为什么是这个优先级**: chat 是 CLI 最核心的入口，让用户第一次能"亲手"跟自己的 Agent 说话，是 Demo 一完整体验的支柱。会话持久化（SessionManager + sessions 表）是它的地基，也是后面所有入口共用的，必须先立起来。

**独立测试方式**: 会话层由 `SessionManagerTest`/`SessionRepositoryTest` 单测承载（同一三元组幂等、不同三元组隔离、模拟重启历史还在）。chat 的交互循环本身是进程级行为，留人工清单。

**验收场景**:

1. **假设** 用户已初始化 OryxOS 工程，**当** 运行 `oryxos chat` 并输入一条消息，**那么** Agent 处理该消息并打印回复。
2. **假设** 用户在 chat 中进行了多轮对话，**当** 输入 `/quit`，**那么** 循环正常退出、命令结束。
3. **假设** 同一用户同一 Agent 对话中断后重启进程，**当** 再次运行 `oryxos chat`，**那么** 进入的是同一个会话，历史还在、能接着聊（同一三元组历次 getOrCreate 返回同一个 Session）。

### 用户场景 2 - 轻命令秒回（init / profile list）(优先级: P2)

开发者在终端运行 `oryxos init` 或 `oryxos profile list` 这类只读文件/写文件的轻命令，结果瞬间返回，不等待 Spring 启动。

**为什么是这个优先级**: 12 个命令里"看一眼就退"的轻命令占多数，等 4 秒才出结果体验差。轻重分流是启动速度的架构决策，但要等 chat（P1）跑通后才显价值。

**独立测试方式**: 进程级行为（是否启动 Spring），留人工清单——肉眼可见轻命令秒回、重命令有 Spring 启动日志。

**验收场景**:

1. **假设** 工程已初始化，**当** 运行 `oryxos profile list`，**那么** 立即列出所有 Agent/Profile，无 Spring 启动延迟。
2. **假设** 用户运行 `oryxos status`、`oryxos tool list`、`oryxos session list` 等查询命令，**那么** 各自输出对应信息且命令正常退出。

### 用户场景 3 - 指定 Agent 对话与服务启动 (优先级: P3)

开发者运行 `oryxos chat --profile <name>` 与指定 Agent 对话；运行 `oryxos serve` 启动 HTTP 服务；运行 `oryxos gateway` 启动守护进程。三种运行模式共享同一份 Profile 配置和 Session 存储。

**为什么是这个优先级**: serve/gateway 的本体分别由 26 节（Web Service）与扩展阶段交付，本节只保证命令存在、能起 Spring、共享存储这一契约先立住。

**独立测试方式**: 人工跑命令验证能启动、日志无 JPA 报错。

**验收场景**:

1. **假设** 用户运行 `oryxos chat --profile weather`，**那么** 与 weather Agent 对话，Session 落在同一份存储里。
2. **假设** 用户运行 `oryxos serve`，**那么** Spring 启动日志中 JPA repository 扫描数量 N > 0（审计与会话写库不报 "Found 0 JPA repository interfaces"）。

### 边界情况

- 用户在 chat 中输入空白行：不交给引擎，继续循环等待下一行。
- 用户输入 `/quit` 带前后空格：trim 后判断，仍能退出。
- 同一进程内重复进入同一会话：getOrCreate 幂等，不产生第二个会话。
- 会话保存后进程崩溃：已落库的会话在下次启动后仍可读取（模拟重启测试覆盖）。

## 需求 *(必填)*

### 功能需求

- **FR-001**: 系统 MUST 提供命令行主入口（整个程序的 main 函数），注册 12 个子命令：init、status、chat、serve、gateway、profile list、profile create、profile show、profile delete、provider list、tool list、session list；每个子命令 `--help` 可用。
- **FR-002**: chat 命令 MUST 提供交互式对话：读 stdin、写 stdout，每收到一行（非退出指令）交给引擎处理并打印回复；用户输入 `/quit`（trim 后）退出循环。
- **FR-003**: chat 命令 MUST 将对话挂到按三元组（channel=`cli` + user + profile）获取或创建的会话上；会话标识的拼接 MUST 只发生在会话管理组件内部这一处，所有入口只提供三元组、不自行拼字符串。
- **FR-004**: 会话管理组件 MUST 提供三个操作：按三元组获取或创建（幂等）、按标识获取、保存。
- **FR-005**: 同一三元组两次获取或创建 MUST 返回同一个会话；channel / user / profile 任一不同 MUST 产生不同会话。
- **FR-006**: 会话数据 MUST 持久化到名为 sessions 的存储（表），字段含：会话标识（主键）、profile 名、channel、用户标识、对话历史（JSON 序列化整体一列）、状态、创建时间、最后活跃时间、归档时间。
- **FR-007**: sessions 表结构 MUST 由手工维护的建表脚本创建（不依赖自动迁移）；对话历史整体序列化为 JSON 存一列，不做按条拆表。
- **FR-008**: 会话保存后（模拟进程重启的）重新读取 MUST 能还原完整对话历史。
- **FR-009**: 命令 MUST 分轻重两类：不调用模型/不跑引擎的命令（init、profile list 等）直接做文件操作、不启动 Spring 上下文；需要模型/引擎的命令（chat、serve、gateway）才启动 Spring 上下文。
- **FR-010**: 启动 Spring 上下文的命令，其启动配置 MUST 显式声明数据访问组件与实体的扫描范围，使各 Maven 模块（不同 Java 包）的仓储接口和实体都被扫到，启动日志显示 repository 数量大于 0。

### 关键实体 *(涉及数据)*

- **会话（Session）**: 用户与 Agent 一次对话的上下文容器。由 channel + user + profile 三元组唯一标识（标识字符串仅在会话管理组件内拼接），包含对话历史（消息列表，整体序列化持久化）、状态（active / archived）、创建时间、最后活跃时间、归档时间。
- **对话消息（Message）**: 会话内的一条消息（如用户消息、助手回复、工具调用记录），作为对话历史的一部分随会话整体持久化。

## 成功标准 *(必填)*

### 可度量结果

- **SC-001**: 同一三元组历次获取或创建得到同一会话（幂等），不同三元组得到不同会话——由自动化测试验证 100% 通过。
- **SC-002**: 会话写入后模拟重启（新建访问上下文重新读取），对话历史 100% 还原——由自动化测试验证通过。
- **SC-003**: 手工建表脚本建出的 sessions 表能存能读，对话历史序列化回读后消息完整——由自动化测试验证通过。
- **SC-004**: 用户在终端可完成一次完整的交互式对话（多轮 + `/quit` 退出）——人工验证通过。
- **SC-005**: 轻命令（init、profile list）秒回、重命令（chat、serve）才启动 Spring——人工验证通过。
- **SC-006**: 12 个子命令均可运行、`--help` 正常——人工验证通过。

## 假设

- 目标用户是开发 OryxOS 的工程师，CLI 是本地交互和调试入口。
- 会话层是后面所有入口（CLI/Web/定时）共用的地基，本节先交付并钉死幂等/隔离/持久化三个性质。
- 三种运行模式（chat/serve/gateway）共享同一份 Profile 配置和 Session 存储。
- 会话历史按条拆表、流式输出、IM Channel 不在本节范围。
- 依赖：前序节已交付 AgentService 引擎入口（process(session, 消息)）、Profile 体系、审计表（llm_calls / tool_invocations）与建表脚本；本节在其上补 CLI 完整形态与会话层。
- 命令分流与 `--help` 的进程级自动化测试成本大于收益，留人工清单。
