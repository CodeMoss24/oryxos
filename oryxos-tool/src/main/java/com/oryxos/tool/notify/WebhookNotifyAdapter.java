package com.oryxos.tool.notify;

import com.oryxos.tool.sandbox.Sandbox;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 核心阶段唯一实现。用通用 HTTP webhook 承接所有场景—— 企业微信、飞书、钉钉的群机器人都提供 webhook 地址,核心阶段不逐家接专用 API。
 *
 * <p>发送前过 Sandbox.enforce(HTTP_REQUEST, url) 域名白名单校验, 跟 http_post 共享同一份 http.allowed_domains 配置,不新增
 * Sandbox 逻辑。
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private static final Logger log = LoggerFactory.getLogger(WebhookNotifyAdapter.class);

  private final Sandbox sandbox;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public WebhookNotifyAdapter(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = target.config().get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("notify target missing 'url'");
    }
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
    String body = "{\"msg_type\":\"text\",\"content\":{\"text\":" + quote(content) + "}}";
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      log.info("notify webhook responded {}", resp.statusCode());
      if (resp.statusCode() >= 400) {
        throw new RuntimeException(
            "webhook notify failed: HTTP " + resp.statusCode() + " " + resp.body());
      }
    } catch (Exception e) {
      throw new RuntimeException("webhook notify failed", e);
    }
  }

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.append("\"").toString();
  }
}
