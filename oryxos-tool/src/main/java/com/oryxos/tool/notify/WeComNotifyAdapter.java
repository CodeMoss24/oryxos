package com.oryxos.tool.notify;

import com.oryxos.tool.sandbox.Sandbox;
import org.springframework.stereotype.Component;

/**
 * 企业微信群机器人(type: wecom)。
 *
 * <p>webhook 形态与通用档相同,仅 body 格式不同:{@code {"msgtype":"text","text":{"content":"..."}}}。
 * 注意这是"群机器人"档——"应用消息"(corpid/corpsecret 换 AccessToken)属扩展阶段,不在此。
 */
@Component("wecom")
public class WeComNotifyAdapter extends AbstractWebhookAdapter {

  public WeComNotifyAdapter(Sandbox sandbox) {
    super(sandbox);
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = requireUrl(target, "wecom");
    post(url, "{\"msgtype\":\"text\",\"text\":{\"content\":" + quote(content) + "}}");
  }
}
