package com.oryxos.tool.notify;

import com.oryxos.core.notify.NotifyChannelDef;
import com.oryxos.core.notify.NotifyChannelRegistry;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.builtin.FileTools;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 内置工具 notify:把一条消息推送到当前 Agent 配置好的通知渠道。
 *
 * <p>渠道两模型并存:①全局注册表(管理台「Notify 渠道」维护,channel 传渠道名按名解析,31 节新模型);②兼容老模型 ——Profile 的 notify_channels
 * 内联条目(channel 传渠道类型,或空白用第一条)。webhook 地址是运行时配置, 不是模型需要知道的信息。适配器按 type 从 {@code Map<String,
 * NotifyChannelAdapter>} 路由, 发送前各适配器自己做 Sandbox HTTP 域名白名单校验。
 */
@Component("notify")
public class NotifyTools implements OryxTool {

  /** channel 参数的"用默认渠道"字面量。 */
  private static final String DEFAULT_CHANNEL = "default";

  /** channelType → 实现(webhook/wecom/feishu/dingtalk);Spring 按 bean 名收集,key 即 type。 */
  private final Map<String, NotifyChannelAdapter> adapters;

  /** 全局通知渠道注册表:channel 传渠道名时按它解析成 {type,url},不再依赖 AGENT.md 内联。 */
  private final NotifyChannelRegistry channelRegistry;

  public NotifyTools(
      Map<String, NotifyChannelAdapter> adapters, NotifyChannelRegistry channelRegistry) {
    this.adapters = Map.copyOf(adapters);
    this.channelRegistry = channelRegistry;
  }

  @Override
  public String getName() {
    return "notify";
  }

  @Override
  public String getDescription() {
    // 动态列出当前已注册的渠道名,模型据此选 channel
    List<NotifyChannelDef> registered = channelRegistry.list();
    if (registered.isEmpty()) {
      return "把一条消息推送到指定通知渠道。channel 传渠道名;当前无已注册渠道——去管理台「Notify 渠道」里新建。";
    }
    String names =
        registered.stream().map(NotifyChannelDef::name).collect(Collectors.joining(", "));
    return "把一条消息推送到指定通知渠道。channel 传渠道名,当前可用:" + names;
  }

  @Override
  public String getInputSchema() {
    return "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\",\"description\":\"要推送的内容\"},"
        + "\"channel\":{\"type\":\"string\",\"description\":\"渠道名;缺省用第一个配置的渠道\"}},"
        + "\"required\":[\"content\"]}";
  }

  @Override
  public ToolResult execute(String inputJson) {
    String content = FileTools.extractField(inputJson, "content");
    if (content.isBlank()) {
      return ToolResult.failure("notify 缺少必填参数 content", false);
    }
    String channel = FileTools.extractField(inputJson, "channel");

    // 新模型:channel 是全局注册表里的渠道名 → 按名解析成 {type,url},与 Agent 内联配置无关
    if (!channel.isBlank()) {
      Optional<NotifyChannelDef> registered = channelRegistry.find(channel);
      if (registered.isPresent()) {
        return sendVia(
            new NotifyChannelAdapter.NotifyTarget(
                registered.get().type(), Map.of("url", registered.get().url())),
            content);
      }
      // channel 给了名字但注册表没有 → 落到下面的兼容路径(按 type 匹配 Agent 内联渠道)
    }

    // 兼容老模型:从当前 Profile 的内联 notify_channels 解析(channel 为空或按 type 匹配)
    Profile profile = ProfileContext.get();
    if (profile == null) {
      return ToolResult.failure("当前无 Agent 上下文,无法解析通知渠道", false);
    }
    List<Profile.NotifyChannel> channels = profile.getNotifyChannels();
    if (channels == null || channels.isEmpty()) {
      return ToolResult.failure(
          "Profile " + profile.getName() + " 未配置 notify_channels,无处可推", false);
    }
    Profile.NotifyChannel resolved = resolveChannel(channels, channel);
    if (resolved == null) {
      return ToolResult.failure(
          "notify_channels 中不存在类型为 " + channel + " 的渠道(不回退默认,避免消息发错地方)", false);
    }
    return sendVia(
        new NotifyChannelAdapter.NotifyTarget(resolved.type(), resolved.config()), content);
  }

  /** 按 type 找适配器并发送;失败以 ToolResult.failure 回给 LLM(错误详情进工具结果,不抛到执行器)。 */
  private ToolResult sendVia(NotifyChannelAdapter.NotifyTarget target, String content) {
    NotifyChannelAdapter adapter = adapters.get(target.channelType());
    if (adapter == null) {
      return ToolResult.failure(
          "渠道类型 " + target.channelType() + " 没有对应的通知实现(已装配: " + adapters.keySet() + ")", false);
    }
    try {
      adapter.send(target, content); // 适配器内部先过 Sandbox HTTP 白名单再 POST
      return ToolResult.success("notified");
    } catch (Exception e) {
      return ToolResult.failure(e.getMessage(), false);
    }
  }

  /** channel 空白或 "default" → 第一个渠道;否则按 NotifyChannel.type 匹配。 */
  private static Profile.NotifyChannel resolveChannel(
      List<Profile.NotifyChannel> channels, String channel) {
    if (channel == null || channel.isBlank() || DEFAULT_CHANNEL.equals(channel)) {
      return channels.get(0);
    }
    for (Profile.NotifyChannel candidate : channels) {
      if (channel.equals(candidate.type())) {
        return candidate;
      }
    }
    return null;
  }
}
