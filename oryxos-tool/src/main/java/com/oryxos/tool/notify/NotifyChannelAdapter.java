package com.oryxos.tool.notify;

import java.util.Map;

/**
 * 通知渠道适配器接口。表达"把一条内容送到某个通知目标"这个意图, 跟入站 Channel 是不同抽象(语义方向相反),不合并。
 *
 * <p>核心阶段只实现 WebhookNotifyAdapter,扩展阶段新增企业微信/飞书/钉钉专用 Adapter。
 */
public interface NotifyChannelAdapter {

  void send(NotifyTarget target, String content);

  record NotifyTarget(String channelType, Map<String, String> config) {}
}
