package com.oryxos.tool.notify;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.sandbox.Sandbox;
import org.springframework.stereotype.Component;

/** 把消息推送到 Profile 配置好的通知渠道。 LLM 大多数时候只需要传 content,地址是运行时配置(从 ProfileContext 取 notify_channels)。 */
@Component("notify")
public class NotifyTools implements OryxTool {

  private final Sandbox sandbox;
  private final WebhookNotifyAdapter adapter;

  public NotifyTools(Sandbox sandbox, WebhookNotifyAdapter adapter) {
    this.sandbox = sandbox;
    this.adapter = adapter;
  }

  @Override
  public String getName() {
    return "notify";
  }

  @Override
  public String getDescription() {
    return "推送消息到通知渠道（飞书群）。调用此工具才会真正发送消息，不要用文字模拟或描述推送结果。";
  }

  @Override
  public String getInputSchema() {
    return "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"},"
        + "\"channel\":{\"type\":\"string\"}},\"required\":[\"content\"]}";
  }

  @Override
  public ToolResult execute(String inputJson) {
    String content = FileTools.extractField(inputJson, "content");
    String channel = FileTools.extractField(inputJson, "channel");
    Profile profile = ProfileContext.get();
    if (profile == null
        || profile.getNotifyChannels() == null
        || profile.getNotifyChannels().isEmpty()) {
      return ToolResult.failure("no notify_channels configured", false);
    }
    for (Profile.NotifyChannel nc : profile.getNotifyChannels()) {
      if (!channel.isBlank() && !channel.equals(nc.type())) continue;
      try {
        sandbox.enforce(
            new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, nc.config().get("url")));
        adapter.send(new NotifyChannelAdapter.NotifyTarget(nc.type(), nc.config()), content);
        return ToolResult.success("notified");
      } catch (Exception e) {
        return ToolResult.failure(e.getMessage(), false);
      }
    }
    if (!channel.isBlank()) {
      return ToolResult.failure("channel not found: " + channel, false);
    }
    return ToolResult.success("notified");
  }
}
