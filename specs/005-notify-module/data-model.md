# Data Model: Notify 通知模块

**Created**: 2026-08-09

## 概述

Notify 模块不引入新的数据库表。审计记录通过已有 `tool_invocations` 表（17 节交付）承载，通知配置数据存储在 `Profile` 对象的 `notifyChannels` 字段中（运行时内存对象，持久化由 Profile 加载机制负责）。

## 实体

### NotifyChannelAdapter (接口 — oryxos-tool)

```text
NotifyChannelAdapter
├── send(target: NotifyTarget, content: String): void
└── NotifyTarget (内嵌 record)
    ├── channelType: String   # 渠道类型标识，如 "webhook"
    └── config: Map<String, String>  # 渠道配置，如 {"url": "https://..."}
```

- **契约**: 接口不携带任何具体渠道实现细节（不出现"飞书""钉钉"字样）
- **核心阶段实现**: `WebhookNotifyAdapter`
- **扩展阶段可新增**: 企业微信 Adapter、飞书 Adapter、钉钉 Adapter 等

### WebhookNotifyAdapter (实现类 — oryxos-tool)

```text
WebhookNotifyAdapter implements NotifyChannelAdapter
├── sandbox: Sandbox          # 注入，发送前校验
├── httpClient: HttpClient    # JDK built-in
└── send(target, content):
    ├── 1. 从 target.config 取 url（缺失则抛 IllegalArgumentException）
    ├── 2. sandbox.enforce(SandboxAction(HTTP_REQUEST, url))
    ├── 3. 构造 JSON body
    ├── 4. POST 到 url
    └── 5. 异常向上抛（不吞）
```

### NotifyTools (内置 Tool — oryxos-tool)

```text
NotifyTools implements OryxTool
├── adapter: WebhookNotifyAdapter  # 注入
├── getName(): "notify"
├── getDescription(): String
├── getInputSchema(): JSON Schema   # content (required, string), channel (optional, string)
└── execute(inputJson):
    ├── 1. 从 ProfileContext.get() 取当前 Profile
    ├── 2. 取 profile.notifyChannels → 空则 ToolResult.failure
    ├── 3. channel 参数非空 → 精确匹配 nc.type；空 → 取第一个
    ├── 4. 构造 NotifyTarget(nc.type, nc.config)
    ├── 5. adapter.send(target, content)
    └── 6. ToolResult.success("notified") 或 ToolResult.failure(...)
```

### Profile.NotifyChannel (Profile 字段 — oryxos-core)

```text
Profile
└── notifyChannels: List<NotifyChannel>
    └── NotifyChannel (record)
        ├── type: String         # 渠道类型，如 "webhook"
        └── config: Map<String, String>  # 渠道配置，如 {"url": "${TEAM_WEBHOOK_URL}"}
```

**配置示例** (AGENT.md frontmatter):

```yaml
notify_channels:
  - type: webhook
    url: ${TEAM_WEBHOOK_URL}
```

## 关系

```text
Profile ──1:N──> NotifyChannel
NotifyTools ──uses──> ProfileContext.get() ──> Profile.notifyChannels
NotifyTools ──delegates──> WebhookNotifyAdapter
WebhookNotifyAdapter ──calls──> Sandbox.enforce()
WebhookNotifyAdapter ──uses──> java.net.http.HttpClient

NotifyTools.execute() 由 ToolExecutor 调用
ToolExecutor 写入 tool_invocations (审计路径，不新增)
```

## 无新表

本节不创建 SQLite 表。Notify 执行记录由 `ToolExecutor`（17 节）统一写入已有 `tool_invocations` 表:

- `tool_name` = "notify"
- `success` = true/false（异常时 false）
- `input_json` / `result_json` / `error_message` / `duration_ms` / `created_at`
