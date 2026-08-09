# Quickstart: Notify 通知模块验证

## Prerequisites

- JDK 21+
- Maven 3.8+
- 项目根目录可运行 `mvn clean verify`

## 运行本节测试

```bash
# 只跑 oryxos-tool 模块的 notify 相关测试
mvn test -pl oryxos-tool -Dtest="com.oryxos.tool.notify.*"

# 全量门禁（含 P3C/SpotBugs/PMD）
mvn clean verify
```

## 预期结果

### WebhookNotifyAdapterTest (3 个测试点)

1. **发送 POST 请求，body 包含 content** — MockWebServer 返回 200，断言收到的请求 body 含 content、URL 来自 NotifyTarget.config 非硬编码
2. **URL 缺失 → IllegalArgumentException** — target.config 无 "url" key，断言抛异常
3. **5xx 响应 → 异常向上抛** — MockWebServer 返回 500，断言 RuntimeException 向上抛

### NotifyToolsTest (3 个测试点)

1. **notify_channels 未配置 → 明确报错** — Profile.notifyChannels 为空，断言 ToolResult.success=false、含 "no notify_channels"
2. **channel 缺省 → 取第一个渠道** — Profile 配 2 个渠道，调 notify(content="hi")（不传 channel），断言推到第一个渠道
3. **enforce 先于 send 调用** — mock Sandbox + Adapter，InOrder 钉死顺序，断言 sandbox.enforce 先于 adapter.send

## 人工验证项

1. 配置真实 webhook URL（企业微信/飞书群机器人），在对话中发"推送测试"，验证消息在群里收到
2. 接口中立性自查：替换为飞书 SDK Adapter 实现，`send(NotifyTarget, String)` 签名是否需要改？（答案：不需要）
