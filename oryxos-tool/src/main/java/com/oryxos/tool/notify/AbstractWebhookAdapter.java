package com.oryxos.tool.notify;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 各 webhook 型渠道适配器的共享管线:发送前过 {@code Sandbox.enforce(HTTP_REQUEST, url)} 域名白名单校验 (跟 http_post 共享同一份
 * http.allowed_domains,不新增 Sandbox 逻辑),HTTP POST 一发一收、非 2xx 上抛。
 *
 * <p>body 格式每家不同,由子类 {@link #send} 自己拼。失败口径:对端非 2xx / 连接失败一律抛 RuntimeException—— "发出去没送到"与"没发出去"对
 * Agent 是同一件事,绝不静默吞掉。
 */
abstract class AbstractWebhookAdapter implements NotifyChannelAdapter {

  private static final Logger log = LoggerFactory.getLogger(AbstractWebhookAdapter.class);

  protected final Sandbox sandbox;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  AbstractWebhookAdapter(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  /** 校验白名单并 POST 一个 JSON body 到 url;非 2xx 抛异常。 */
  protected void post(String url, String body) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      log.info("notify adapter responded {} {}", resp.statusCode(), url);
      if (resp.statusCode() >= 400) {
        throw new RuntimeException("notify failed: HTTP " + resp.statusCode() + " " + resp.body());
      }
    } catch (Exception e) {
      throw new RuntimeException("notify failed", e);
    }
  }

  protected static String quote(String s) {
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

  /** 渠道配置必须带 url。 */
  protected static String requireUrl(NotifyTarget target, String type) {
    String url = target.config().get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException(type + " 渠道缺少 url 配置(notify_channels 条目需要 url 键)");
    }
    return url;
  }
}
