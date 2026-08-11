package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("NotifyTools — 通知内置 Tool 验收")
class NotifyToolsTest {

  private Sandbox sandbox;
  private WebhookNotifyAdapter adapter;
  private NotifyTools notifyTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    adapter = mock(WebhookNotifyAdapter.class);
    doNothing().when(sandbox).enforce(any());
    notifyTools = new NotifyTools(sandbox, adapter);
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
  @DisplayName("notify_channels 未配置时返回明确错误，非静默失败")
  void reportsErrorWhenNoNotifyChannelsConfigured() {
    Profile profile = new Profile();
    profile.setName("test");
    ProfileContext.set(profile);

    ToolResult result = notifyTools.execute("{\"content\":\"hi\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("no notify_channels");
  }

  @Test
  @DisplayName("channel 参数缺省时取第一个渠道")
  void usesFirstChannelWhenChannelParamOmitted() {
    Profile.NotifyChannel chA =
        new Profile.NotifyChannel("webhook-a", Map.of("url", "https://a.example.com"));
    Profile.NotifyChannel chB =
        new Profile.NotifyChannel("webhook-b", Map.of("url", "https://b.example.com"));
    ProfileContext.set(profileWithChannels(chA, chB));

    notifyTools.execute("{\"content\":\"hi\"}");

    org.mockito.Mockito.verify(adapter)
        .send(argThat(t -> t.channelType().equals("webhook-a")), eq("hi"));
  }

  @Test
  @DisplayName("发送前必须先过白名单校验——InOrder 钉死顺序，顺序反了就是漏洞")
  void enforceBeforeSendOrderVerifiedWithInOrder() {
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://example.com/webhook"));
    ProfileContext.set(profileWithChannels(ch));

    notifyTools.execute("{\"content\":\"hello\"}");

    InOrder inOrder = inOrder(sandbox, adapter);
    inOrder.verify(sandbox).enforce(argThat(a -> a.type() == ActionType.HTTP_REQUEST));
    inOrder.verify(adapter).send(any(), eq("hello"));
  }

  @Test
  @DisplayName("notify:真实沙箱拒绝时推送未发生——mock adapter 从未收到发送")
  void sandboxBlockedNotifyNeverSends() {
    // 空域名白名单 = 什么都不允许:enforce 抛 SandboxViolationException,adapter 不得被调用
    WhitelistSandbox realSandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of()),
            new ShellSandboxProperties(List.of()),
            new HttpSandboxProperties(List.of()));
    NotifyTools tools = new NotifyTools(realSandbox, adapter);
    Profile.NotifyChannel ch =
        new Profile.NotifyChannel("webhook", Map.of("url", "https://example.com/webhook"));
    ProfileContext.set(profileWithChannels(ch));

    ToolResult result = tools.execute("{\"content\":\"danger\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("不在白名单内");
    verify(adapter, never()).send(any(), any());
  }
}
