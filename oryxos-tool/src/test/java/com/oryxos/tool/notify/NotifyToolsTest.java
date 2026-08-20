package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.notify.NotifyChannelDef;
import com.oryxos.core.notify.NotifyChannelRegistry;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotifyTools — 通知内置 Tool 验收")
class NotifyToolsTest {

  private Sandbox sandbox;
  private WebhookNotifyAdapter adapter;
  private NotifyChannelRegistry registry;
  private NotifyTools notifyTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    adapter = mock(WebhookNotifyAdapter.class);
    registry = mock(NotifyChannelRegistry.class);
    when(registry.list()).thenReturn(List.of());
    notifyTools = new NotifyTools(Map.of("webhook", adapter), registry);
  }

  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  private static Profile profileWithChannels(Profile.NotifyChannel... channels) {
    Profile profile = new Profile();
    profile.setName("test");
    profile.setNotifyChannels(List.of(channels));
    return profile;
  }

  @Test
  @DisplayName("notify_channels 未配置且注册表无渠道时返回明确错误,非静默失败")
  void reportsErrorWhenNoNotifyChannelsConfigured() {
    Profile profile = new Profile();
    profile.setName("test");
    ProfileContext.set(profile);

    ToolResult result = notifyTools.execute("{\"content\":\"hi\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("未配置 notify_channels");
  }

  @Test
  @DisplayName("channel 传注册表渠道名:按名解析 {type,url} 交给对应适配器(新模型)")
  void resolvesRegisteredChannelByName() {
    when(registry.find("ops-alerts"))
        .thenReturn(
            Optional.of(
                new NotifyChannelDef(
                    "ops-alerts", "webhook", "https://hooks.example.com/a", null)));
    when(registry.list())
        .thenReturn(List.of(new NotifyChannelDef("ops-alerts", "webhook", "", "")));

    notifyTools.execute("{\"content\":\"hi\",\"channel\":\"ops-alerts\"}");

    verify(adapter)
        .send(
            argThat(
                t ->
                    t.channelType().equals("webhook")
                        && t.config().get("url").equals("https://hooks.example.com/a")),
            eq("hi"));
  }

  @Test
  @DisplayName("channel 传注册表没有的名字:回退按 type 匹配 Profile 内联渠道(兼容老模型)")
  void fallsBackToInlineWhenChannelNotInRegistry() {
    when(registry.find("webhook")).thenReturn(Optional.empty());
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://example.com/webhook"));
    ProfileContext.set(profileWithChannels(ch));

    ToolResult result = notifyTools.execute("{\"content\":\"hi\",\"channel\":\"webhook\"}");

    assertThat(result.success()).isTrue();
    verify(adapter)
        .send(
            argThat(
                t ->
                    t.channelType().equals("webhook")
                        && t.config().get("url").equals("https://example.com/webhook")),
            eq("hi"));
  }

  @Test
  @DisplayName("channel 参数缺省时取第一个内联渠道")
  void usesFirstChannelWhenChannelParamOmitted() {
    Profile.NotifyChannel chA =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://a.example.com"));
    Profile.NotifyChannel chB =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://b.example.com"));
    ProfileContext.set(profileWithChannels(chA, chB));

    notifyTools.execute("{\"content\":\"hi\"}");

    verify(adapter)
        .send(argThat(t -> t.config().get("url").equals("https://a.example.com")), eq("hi"));
  }

  @Test
  @DisplayName("内联渠道类型没有对应适配器实现时明确报错")
  void reportsErrorWhenAdapterTypeUnsupported() {
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("sms", Map.of("url", "https://sms.example.com/send"));
    ProfileContext.set(profileWithChannels(ch));

    ToolResult result = notifyTools.execute("{\"content\":\"hi\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("sms").contains("没有对应的通知实现");
    verify(adapter, never()).send(any(), any());
  }

  @Test
  @DisplayName("真实沙箱拒绝时推送未发生——适配器内部校验在 HTTP 发送之前")
  void sandboxBlockedNotifyNeverSends() {
    // 空域名白名单 = 什么都不允许:adapter 内部 enforce 抛 SandboxViolationException,HTTP 发送不得发生
    WhitelistSandbox realSandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of()),
            new ShellSandboxProperties(List.of()),
            new HttpSandboxProperties(List.of()));
    NotifyTools tools =
        new NotifyTools(Map.of("webhook", new WebhookNotifyAdapter(realSandbox)), registry);
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://example.com/webhook"));
    ProfileContext.set(profileWithChannels(ch));

    ToolResult result = tools.execute("{\"content\":\"danger\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("不在白名单内");
  }

  @Test
  @DisplayName("channel 指定类型但内联渠道里没有该类型时明确报错,不回退默认(避免发错地方)")
  void reportsErrorWhenChannelTypeNotFoundInInline() {
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://a.example.com"));
    ProfileContext.set(profileWithChannels(ch));

    ToolResult result = notifyTools.execute("{\"content\":\"hi\",\"channel\":\"feishu\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("feishu").contains("不存在");
    verify(adapter, never()).send(any(), any());
  }

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
  @DisplayName("sandbox.enforce 由适配器内部完成——真 adapter + mock 沙箱,发送前必然过白名单")
  void sandboxEnforceWiredInsideAdapter() throws IOException {
    MockWebServer server = new MockWebServer();
    server.start(freePort());
    try {
      server.enqueue(new MockResponse().setResponseCode(200));
      doNothing().when(sandbox).enforce(any());
      NotifyTools tools =
          new NotifyTools(Map.of("webhook", new WebhookNotifyAdapter(sandbox)), registry);
      Profile.NotifyChannel ch =
          new Profile.NotifyChannel("webhook", Map.of("url", server.url("/webhook").toString()));
      ProfileContext.set(profileWithChannels(ch));

      ToolResult result = tools.execute("{\"content\":\"hello\"}");

      assertThat(result.success()).isTrue();
      verify(sandbox).enforce(any()); // adapter 发送前必然过白名单
    } finally {
      server.shutdown();
    }
  }
}
