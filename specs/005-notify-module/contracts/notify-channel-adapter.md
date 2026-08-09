# Contract: NotifyChannelAdapter

## Interface

```java
package com.oryxos.tool.notify;

public interface NotifyChannelAdapter {
    void send(NotifyTarget target, String content);

    record NotifyTarget(String channelType, java.util.Map<String, String> config) {}
}
```

## Semantics

- `send(NotifyTarget, String)`: 将 `content` 推送到 `target` 指定的通知目标。
- `NotifyTarget.channelType`: 渠道类型标识（如 "webhook"、"feishu"），由实现类解释。
- `NotifyTarget.config`: 渠道配置键值对，具体 keys 由实现类定义（webhook 实现需要 "url"）。

## Error Contract

- `config` 中缺失必要 key → `IllegalArgumentException`
- 推送失败（网络错误、HTTP 非 2xx）→ 异常向上抛，不静默吞
- 实现负责在发送前调用 `Sandbox.enforce(HTTP_REQUEST, url)`

## Implementations

| 实现 | channelType | config keys | 阶段 |
|------|-------------|-------------|------|
| WebhookNotifyAdapter | webhook | url | 核心阶段 |
| (未来) FeishuNotifyAdapter | feishu | url, secret? | 扩展阶段 |
| (未来) WeComNotifyAdapter | wecom | url | 扩展阶段 |
