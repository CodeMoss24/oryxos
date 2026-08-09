# Feature Specification: Notify 通知模块

**Feature Branch**: `019-lesson19-Notify`

**Created**: 2026-08-09

**Status**: Draft

**Input**: User description: "第19节需求：Notify 通知模块——为 OryxOS 补上'Agent 主动把结果送出去'的出站通知能力"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent 定时触发后主动推送结果到群 (Priority: P1)

定时天气 Agent 到点自动触发，完成查询和分析后，通过通知渠道将结果推送至配置好的企业 IM 群（企业微信/飞书/钉钉）。群成员无需打开终端或主动查询，如同收到同事消息一样自然获取结果。

**Why this priority**: 这是 Notify 模块存在的根本原因——没有"主动推送"能力，所有定时触发的 Agent 产出都只能烂在 Session 里无人看到。P1 覆盖端到端通知链路（配置→发送→送达）。

**Independent Test**: 配置一个 webhook 渠道后，Agent 执行过程中调用 notify 工具，验证消息送达目标 webhook 地址，且请求内容包含正确的消息体。

**Acceptance Scenarios**:

1. **Given** Agent Profile 已配置一个 webhook 类型的 notify_channel（含 url），**When** Agent 调用 notify(content="今日天气：晴，25°C，建议穿薄外套")，**Then** 系统向该 url 发送 HTTP POST，body 中包含指定 content，HTTP 响应 2xx 表示发送成功。
2. **Given** Agent Profile 未配置任何 notify_channel，**When** Agent 调用 notify，**Then** 系统返回明确错误信息（非静默失败），Agent 可知晓推送未执行。
3. **Given** Agent Profile 配置了多个 notify_channel（如企业微信 + 飞书），**When** Agent 调用 notify 且未指定 channel 参数，**Then** 系统推送到第一个（默认）渠道。

---

### User Story 2 - 对话中手动触发推送 (Priority: P2)

用户在交互式对话中直接要求 Agent 推送一条测试消息，Agent 调用 notify 工具完成推送并反馈结果。覆盖"人推"场景下的通知能力——用户不需要离开对话界面即可验证通知链路是否畅通。

**Why this priority**: 这是通知能力在"人推"场景下的自然延伸，也是运维和调试的常用入口。优先级低于 P1 因为核心价值在钟推场景，但手动补跑验证同一链路是三个验收 Demo 的共同要求。

**Independent Test**: 在 CLI 对话中输入"把'测试消息'推送一下"，Agent 调用 notify，观察返回"已推送"。

**Acceptance Scenarios**:

1. **Given** Agent Profile 已配置 notify_channel，**When** 用户在对话中说"推送：测试消息"，Agent 调用 notify(content="测试消息")，**Then** Agent 返回成功确认，消息送达 webhook 目标。
2. **Given** Agent Profile 已配置多个渠道，**When** 用户指定渠道名"推送到飞书群"，Agent 调用 notify(content="...", channel="feishu")，**Then** 仅向指定渠道推送，不影响其他渠道。

---

### User Story 3 - 渠道配置变更无需改 Agent 指令 (Priority: P3)

运营方将通知渠道从企业微信 webhook 更换为飞书 webhook，只需修改 Profile 的 notify_channels 配置字段，Agent 的 AGENT.md 正文和对话指令完全不变。接口中立性确保换渠道不影响 Agent 行为。

**Why this priority**: 这是"接口先行"设计习惯的直接价值体现——验证 NotifyChannelAdapter 接口签名的稳定性。P3 因为这是架构质量属性而非直接用户功能，但它是扩展阶段平滑升级的前提。

**Independent Test**: 创建两个仅 notify_channels 配置不同的 Profile，同一段 Agent 指令分别用两个 Profile 运行，验证各自推送到各自配置的目标。

**Acceptance Scenarios**:

1. **Given** 两个 Profile A（企业微信 webhook）和 B（飞书 webhook）使用相同的 Agent 指令，**When** Agent 分别在两个 Profile 下调用 notify，**Then** A 推送至企业微信、B 推送至飞书，Agent 指令无需任何修改。
2. **Given** NotifyChannelAdapter 的实现被替换为企业微信专用 SDK Adapter（扩展阶段），**When** 调用方使用 send(NotifyTarget, String)，**Then** 接口签名不需要任何修改——NotifyTarget 的 channelType 和 config 足以承载新实现的配置需求。

---

### Edge Cases

- Webhook 目标返回 4xx（如 400 Bad Request、404 Not Found）时，系统如何处理？
- Webhook 目标返回 5xx（如 500 Internal Server Error、503 Service Unavailable）时，异常是否向上抛而不被静默吞掉？
- Webhook 目标不可达（DNS 解析失败、连接超时、连接被拒绝）时，错误信息是否明确记录？
- channel 参数传入一个 Profile 中不存在的渠道名时，系统如何处理？
- NotifyTarget.config 中 url 缺失或为空时，系统如何处理？
- 同一 Profile 下配置了多个同 type 的渠道时，channel 参数如何区分？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供通知渠道适配器接口（NotifyChannelAdapter），定义 send(NotifyTarget, String) 方法签名，接口不携带任何具体渠道实现细节（不出现"企业微信""飞书""钉钉"等字样）。
- **FR-002**: 系统必须提供 NotifyTarget 数据结构，包含 channelType（渠道类型标识）和 config（键值对配置，如 url），由具体实现类自行解释 config 内容。
- **FR-003**: 系统必须提供 WebhookNotifyAdapter 作为核心阶段唯一实现，将 content 包成 JSON 通过 HTTP POST 发送到 NotifyTarget.config 中的 url。
- **FR-004**: WebhookNotifyAdapter 在发送 HTTP 请求前必须先调用 Sandbox.enforce(HTTP_REQUEST, url) 进行安全校验，复用已有的域名白名单机制。
- **FR-005**: 系统必须提供 notify 内置工具（NotifyTools），接受 content（必填）和 channel（可选）两个参数。
- **FR-006**: NotifyTools 必须从当前 ProfileContext 读取 notify_channels 配置来确定推送目标；channel 参数缺省时使用第一个配置的渠道。
- **FR-007**: 当 Profile 的 notify_channels 为空或未配置时，NotifyTools 必须返回明确错误（非静默失败），让 Agent 知晓推送未被发送。
- **FR-008**: Webhook 推送失败（HTTP 4xx/5xx、网络错误）时，异常必须向上抛，不可静默吞掉——由 ToolExecutor 现有审计路径写入 tool_invocations（success=false）。
- **FR-009**: Profile 数据结构必须包含 notify_channels 字段，每项含 type（渠道类型标识）和 config（Map<String,String>，渠道特定配置）。
- **FR-010**: NotifyTarget.config 中 url 缺失或为空时，系统必须抛出明确的参数校验错误。

### Key Entities

- **NotifyChannelAdapter（接口）**: 通知渠道适配器抽象，表达"将一条内容送到某个通知目标"的意图。核心阶段仅 WebhookNotifyAdapter 一档实现，扩展阶段可新增企业微信/飞书/钉钉等专用 Adapter，接口签名不变。
- **NotifyTarget**: 通知目标描述，包含 channelType（如 "webhook"、"feishu"）和 config（渠道特定键值对，如 {"url": "https://..."}）。
- **NotifyChannel（Profile 字段）**: Agent Profile 中的通知渠道声明，包含 type 和 config，由运营方配置，LLM 不可见具体 url。
- **WebhookNotifyAdapter（实现类）**: 通用 HTTP webhook 推送实现，发送前过 Sandbox.enforce 域名白名单校验，与 http_post 共享同一份 http.allowed_domains 配置。
- **NotifyTools（内置工具）**: 面向 LLM 暴露的 notify 工具，从 ProfileContext 获取当前 Agent 的通知配置，委托 NotifyChannelAdapter 执行实际发送。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 所有自动化测试（含 WebhookNotifyAdapterTest 和 NotifyToolsTest 的全部测试用例）通过 `mvn test` 以零失败运行。
- **SC-002**: WebhookNotifyAdapter 用 MockWebServer 验证：发送的 POST body 包含 content 内容、URL 来源于 NotifyTarget.config 而非硬编码。
- **SC-003**: WebhookNotifyAdapter 在目标返回 5xx 时异常向上抛——通过 MockWebServer 返回 500，断言调用方收到异常。
- **SC-004**: NotifyTools 在 Profile 未配置 notify_channels 时返回明确错误（success=false），Agent 不会误以为已发送。
- **SC-005**: NotifyTools 的 sandbox.enforce 调用严格先于 adapter.send 调用——通过 InOrder 验证顺序，顺序反了即为安全漏洞。
- **SC-006**: NotifyTools 在 channel 参数缺省时使用第一个配置的渠道。
- **SC-007**: 接口签名中立性验证（人工项）：假设换为企业微信官方 SDK Adapter，NotifyChannelAdapter.send(NotifyTarget, String) 签名无需修改。
- **SC-008**: 端到端验证（人工项）：真实 webhook URL 配置后，notify 调用能实际送达消息。

## Assumptions

- 核心阶段只实现通用 HTTP webhook 推送（WebhookNotifyAdapter），不逐家对接企业微信/飞书/钉钉的专用 SDK（签名算法、AccessToken 刷新等放扩展阶段）。
- Sandbox 接口已由前序节交付（Sandbox.enforce 方法可用），Notify 模块仅按接口调用，不新增沙箱逻辑。白名单具体实现在 24 节展开。
- ProfileContext（ThreadLocal）已由 17 节 AgentService 交付，NotifyTools 从 ProfileContext.get() 获取当前 Agent 的 Profile。
- ToolResult 和 OryxTool 接口已由 oryxos-core 定义，NotifyTools 遵循 OryxTool 接口实现。
- ToolExecutor 的审计路径（tool_invocations 写入）已由 17 节建立，NotifyTools 不新增审计逻辑——执行过程中的异常由 ToolExecutor 统一捕获并写 success=false。
- HTTP 客户端使用 JDK 内置 java.net.http.HttpClient，避免引入额外依赖。
- 测试使用 OkHttp MockWebServer 进行 HTTP 模拟（不依赖外部网络）。
- 富文本卡片消息、邮件/SMS 通知等非 webhook 渠道全部放扩展阶段。
- NotifyTools 的 @Tool 注册机制完整接线依赖 20 节，本节先实现核心逻辑和第一批测试，第二批测试（NotifyToolsTest）可通过 mock Sandbox 和 Adapter 运行。
