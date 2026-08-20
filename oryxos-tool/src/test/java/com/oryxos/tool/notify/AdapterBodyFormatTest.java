package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 三家群机器人适配器的 body 协议验证:飞书/企微/钉钉 body 格式各不相同,钉钉另有加签 URL 拼接。 全部复用共享管线(白名单校验 + POST + 非 2xx 上抛,已在
 * WebhookNotifyAdapterTest 覆盖)。
 */
@DisplayName("Feishu/WeCom/DingTalk 适配器 — body 协议验证")
class AdapterBodyFormatTest {

  /** 段外固定端口:本机内核临时端口段(44620-48715)会被 IDE 的长连接池占满,随机绑定偶发 EADDRINUSE。 */
  private static int freePort() throws IOException {
    for (int p = 20000; p < 20100; p++) {
      try (ServerSocket s = new ServerSocket(p)) {
        return p;
      } catch (IOException ignored) {
        // 被占,试下一个
      }
    }
    throw new IOException("20000-20100 全部被占");
  }

  @Test
  @DisplayName("feishu: msg_type/text/content.text 三段式 body")
  void feishuBodyFormat() throws Exception {
    assertBody(
        "feishu",
        new FeishuNotifyAdapter(mock(Sandbox.class)),
        "{\"msg_type\":\"text\",\"content\":{\"text\":\"hi\"}}");
  }

  @Test
  @DisplayName("wecom: msgtype/text/text.content body")
  void wecomBodyFormat() throws Exception {
    assertBody(
        "wecom",
        new WeComNotifyAdapter(mock(Sandbox.class)),
        "{\"msgtype\":\"text\",\"text\":{\"content\":\"hi\"}}");
  }

  @Test
  @DisplayName("dingtalk: msgtype/text body,配 secret 时 URL 拼 timestamp+sign")
  void dingtalkBodyAndSignature() throws Exception {
    MockWebServer server = new MockWebServer();
    server.start(freePort());
    try {
      server.enqueue(new MockResponse().setResponseCode(200));
      DingTalkNotifyAdapter adapter = new DingTalkNotifyAdapter(mock(Sandbox.class));

      adapter.send(
          new NotifyChannelAdapter.NotifyTarget(
              "dingtalk", Map.of("url", server.url("/robot/send").toString(), "secret", "SECRET")),
          "hi");

      RecordedRequest req = server.takeRequest();
      assertThat(req.getBody().readUtf8())
          .isEqualTo("{\"msgtype\":\"text\",\"text\":{\"content\":\"hi\"}}");
      assertThat(req.getPath()).contains("timestamp=").contains("sign=");
      assertThat(req.getPath()).doesNotContain("{");
    } finally {
      server.shutdown();
    }
  }

  private static void assertBody(String type, NotifyChannelAdapter adapter, String expectedBody)
      throws Exception {
    MockWebServer server = new MockWebServer();
    server.start(freePort());
    try {
      server.enqueue(new MockResponse().setResponseCode(200));
      adapter.send(
          new NotifyChannelAdapter.NotifyTarget(
              type, Map.of("url", server.url("/hook").toString())),
          "hi");
      RecordedRequest req = server.takeRequest();
      assertThat(req.getMethod()).isEqualTo("POST");
      assertThat(req.getHeader("Content-Type")).startsWith("application/json");
      assertThat(req.getBody().readUtf8()).isEqualTo(expectedBody);
    } finally {
      server.shutdown();
    }
  }
}
