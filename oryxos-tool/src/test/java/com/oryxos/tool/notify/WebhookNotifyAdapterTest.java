package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WebhookNotifyAdapter — HTTP webhook 推送协议验证")
class WebhookNotifyAdapterTest {

  private MockWebServer server;
  private Sandbox sandbox;
  private WebhookNotifyAdapter adapter;

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

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start(freePort());
    sandbox = mock(Sandbox.class);
    adapter = new WebhookNotifyAdapter(sandbox);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  @DisplayName("发送 POST 请求，body 包含 content，URL 来自 NotifyTarget.config 而非硬编码")
  void sendsPostRequestWithContentInBodyUrlFromConfig() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));

    String url = server.url("/webhook").toString();
    var target = new NotifyChannelAdapter.NotifyTarget("webhook", Map.of("url", url));
    adapter.send(target, "hello");

    RecordedRequest req = server.takeRequest();
    assertThat(req.getMethod()).isEqualTo("POST");
    assertThat(req.getPath()).isEqualTo("/webhook");
    assertThat(req.getRequestUrl().toString()).isEqualTo(url);
    String body = req.getBody().readUtf8();
    assertThat(body).contains("hello");
  }

  @Test
  @DisplayName("url 缺失时抛出 IllegalArgumentException")
  void throwsExceptionWhenUrlMissing() {
    var target = new NotifyChannelAdapter.NotifyTarget("webhook", Map.of());

    assertThatThrownBy(() -> adapter.send(target, "content"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  @DisplayName("webhook 返回 5xx 时异常向上抛，不静默吞掉")
  void throwsExceptionOnServerError5xx() {
    server.enqueue(new MockResponse().setResponseCode(500));

    String url = server.url("/error").toString();
    var target = new NotifyChannelAdapter.NotifyTarget("webhook", Map.of("url", url));

    assertThatThrownBy(() -> adapter.send(target, "boom"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("notify failed");
  }
}
