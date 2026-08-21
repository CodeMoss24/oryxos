package com.oryxos.tool.notify;

import com.oryxos.core.notify.NotifyChannelDef;
import com.oryxos.core.notify.NotifyChannelRegistry;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.builtin.FileTools;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 内置工具 notify:把一条消息推送到全局通知渠道注册表里的某个渠道。
 *
 * <p>31 节起 AGENT.md 不再内联 notify_channels——通知出口的唯一真相源是 {@link NotifyChannelRegistry} (管理台「Notify
 * 渠道」CRUD,Agent 按 channel 名引用)。webhook 地址是运行时配置,不是模型需要知道的信息。 适配器按 type 从 {@code Map<String,
 * NotifyChannelAdapter>} 路由,发送前各适配器自己做 Sandbox HTTP 域名白名单校验。
 */
@Component("notify")
public class NotifyTools implements OryxTool {

  /** channelType → 实现(webhook/wecom/feishu/dingtalk);Spring 按 bean 名收集,key 即 type。 */
  private final Map<String, NotifyChannelAdapter> adapters;

  /** 全局通知渠道注册表:channel 传渠道名按它解析成 {type,url}(唯一真相源)。 */
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
        + "\"channel\":{\"type\":\"string\",\"description\":\"渠道名;缺省用注册表里第一个渠道\"}},"
        + "\"required\":[\"content\"]}";
  }

  @Override
  public ToolResult execute(String inputJson) {
    String content = FileTools.extractField(inputJson, "content");
    if (content.isBlank()) {
      return ToolResult.failure("notify 缺少必填参数 content", false);
    }
    String channel = FileTools.extractField(inputJson, "channel");

    List<NotifyChannelDef> registered = channelRegistry.list();
    NotifyChannelDef target;
    if (channel == null || channel.isBlank()) {
      // 缺省 → 注册表第一个渠道
      if (registered.isEmpty()) {
        return ToolResult.failure("全局通知渠道注册表为空,无处可推——先去管理台「Notify 渠道」新建一个渠道", false);
      }
      target = registered.get(0);
    } else {
      target = channelRegistry.find(channel).orElse(null);
      if (target == null) {
        return ToolResult.failure("通知渠道注册表中不存在名为 " + channel + " 的渠道(不回退默认,避免消息发错地方)", false);
      }
    }
    return sendVia(
        new NotifyChannelAdapter.NotifyTarget(target.type(), Map.of("url", target.url())), content);
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
}
