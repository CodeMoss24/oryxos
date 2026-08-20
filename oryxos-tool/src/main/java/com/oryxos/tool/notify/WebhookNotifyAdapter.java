package com.oryxos.tool.notify;

import com.oryxos.tool.sandbox.Sandbox;
import org.springframework.stereotype.Component;

/**
 * 通用 webhook(type: webhook)——企业微信/飞书/钉钉的群机器人都收 webhook,一档覆盖大部分场景, 不逐家接签名算法与 AccessToken
 * 刷新(留扩展阶段)。body 格式:{@code {"content":"..."}}。
 */
@Component("webhook")
public class WebhookNotifyAdapter extends AbstractWebhookAdapter {

  public WebhookNotifyAdapter(Sandbox sandbox) {
    super(sandbox);
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = requireUrl(target, "webhook");
    post(url, "{\"content\":" + quote(content) + "}");
  }
}
