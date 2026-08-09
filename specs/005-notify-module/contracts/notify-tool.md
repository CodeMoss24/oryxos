# Contract: NotifyTools (notify)

## Tool Identity

- **Name**: `notify`
- **Type**: 内置 Tool（OryxTool 实现，归 oryxos-tool）
- **Registration**: `@Component("notify")` → ToolRegistry

## Input Schema (JSON)

```json
{
  "type": "object",
  "properties": {
    "content": {
      "type": "string",
      "description": "要推送的消息内容"
    },
    "channel": {
      "type": "string",
      "description": "可选，目标渠道类型。不传时取 Profile 第一个 notify_channel"
    }
  },
  "required": ["content"]
}
```

## Behavior

1. 从 `ProfileContext.get()` 取当前 Profile
2. 取 `profile.getNotifyChannels()`:
   - 空 → `ToolResult.failure("no notify_channels configured", false)`
3. `channel` 参数:
   - 非空 → 匹配 `nc.type().equals(channel)` 的第一个渠道；无匹配 → `ToolResult.failure("channel not found", false)`
   - 空 → 取第一个渠道
4. 构造 `NotifyTarget(nc.type(), nc.config())`
5. 委托 `adapter.send(target, content)`
6. 成功 → `ToolResult.success("notified")`
7. 异常 → `ToolResult.failure(exception.getMessage(), false)`

## Audit

Notify 执行由 `ToolExecutor` 统一写入 `tool_invocations`:
- `tool_name` = "notify"
- 成功/失败均记录
