package com.oryxos.tool.notify;

import com.oryxos.tool.sandbox.Sandbox;
import org.springframework.stereotype.Component;

/**
 * 飞书 / Lark 自定义机器人(type: feishu)。
 *
 * <p>二者协议相同、仅域名不同(open.feishu.cn / open.larksuite.com),URL 来自配置故一个实现覆盖两者。 body 格式:{@code
 * {"msg_type":"text","content":{"text":"..."}}};签名校验为可选项,未开启时拿 URL 即可推。
 */
@Component("feishu")
public class FeishuNotifyAdapter extends AbstractWebhookAdapter {

  public FeishuNotifyAdapter(Sandbox sandbox) {
    super(sandbox);
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = requireUrl(target, "feishu");
    post(url, "{\"msg_type\":\"text\",\"content\":{\"text\":" + quote(content) + "}}");
  }
}
