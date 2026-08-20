package com.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.AgentLoader.ParsedAgentMd;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.LlmResponse;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.scheduler.AgentScheduler;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 第 30 节验收 harness:GenerateTest(generate-files / files 生成链路)。
 *
 * <p>守点(课件原样):generate 产出能被 AgentLoader 解析的 AGENT.md 草稿;只生成、不落盘、不注册;LLM 产出非法 → 400 可读原因;Agent
 * 已存在则沿用其 provider;author model 未配置 → 503(IllegalStateException,不发 model=null); saveFiles 先校验
 * AGENT.md 可解析再落盘(非法不写坏目录)、写入即生效(schedules 变更走注销重注册)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Generate — 一句话生成 AGENT.md 草稿与保存生效(第 30 节)")
class GenerateTest {

  @Mock private AgentLoader agentLoader;
  @Mock private ProfileRegistry profileRegistry;
  @Mock private AgentScheduler agentScheduler;
  @Mock private AgentStore agentStore;
  @Mock private ProviderPort providerPort;

  private AgentLifecycleService lifecycle;

  @BeforeEach
  void setUp() {
    lifecycle =
        new AgentLifecycleService(
            agentLoader,
            profileRegistry,
            agentScheduler,
            agentStore,
            providerPort,
            "deepseek",
            "deepseek-chat",
            "deepseek");
  }

  /** 生成链路所需的基础桩:校验路径 parse + derive 都放行。 */
  private void stubValidationOk(String agentName) {
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile profile = new Profile();
    profile.setName(agentName);
    when(agentLoader.deriveProfile(eq(agentName), any())).thenReturn(profile);
  }

  @Test
  @DisplayName("generate_产出可被AgentLoader解析_剥代码围栏_只生成不落盘不注册")
  void generate_returnsParsableDraft_withoutWritingOrRegistering() {
    Profile existing = new Profile();
    existing.setName("daily-weather");
    existing.setProvider(new Profile.Provider("deepseek", "deepseek-chat", null));
    when(profileRegistry.find("daily-weather")).thenReturn(Optional.of(existing));
    stubValidationOk("daily-weather");
    String draft = "```markdown\n---\nname: daily-weather\n---\n正文\n```";
    when(providerPort.chat(eq("author-generator"), any(Profile.class), any()))
        .thenReturn(LlmResponse.text(draft));

    Map<String, String> files = lifecycle.generateFiles("daily-weather", "每天早上推送天气");

    // 只生成:剥掉代码围栏、不落盘、不注册
    assertThat(files.get("AGENT.md")).isEqualTo("---\nname: daily-weather\n---\n正文");
    verify(agentStore, never()).writeAll(anyString(), any());
    verify(agentStore, never()).writeAgentMd(anyString(), anyString());
    verify(profileRegistry, never()).register(any());
  }

  @Test
  @DisplayName("generate_Agent已存在_生成调用沿用该Agent的provider")
  void generate_existingAgent_usesItsOwnProvider() {
    Profile existing = new Profile();
    existing.setName("daily-weather");
    existing.setProvider(new Profile.Provider("kimi", "moonshot-v1", null));
    when(profileRegistry.find("daily-weather")).thenReturn(Optional.of(existing));
    stubValidationOk("daily-weather");
    when(providerPort.chat(eq("author-generator"), any(Profile.class), any()))
        .thenReturn(LlmResponse.text("---\nname: daily-weather\n---\n正文"));

    lifecycle.generateFiles("daily-weather", "每天早上推送天气");

    verify(providerPort)
        .chat(
            eq("author-generator"),
            org.mockito.ArgumentMatchers.argThat(
                p ->
                    "kimi".equals(p.getProvider().name())
                        && "moonshot-v1".equals(p.getProvider().model())),
            any());
  }

  @Test
  @DisplayName("generate_Agent不存在_生成调用用author配置的provider/model")
  void generate_newAgent_usesAuthorConfigProvider() {
    when(profileRegistry.find("fresh-agent")).thenReturn(Optional.empty());
    stubValidationOk("fresh-agent");
    when(providerPort.chat(eq("author-generator"), any(Profile.class), any()))
        .thenReturn(LlmResponse.text("---\nname: fresh-agent\n---\n正文"));

    lifecycle.generateFiles("fresh-agent", "一句话描述");

    verify(providerPort)
        .chat(
            eq("author-generator"),
            org.mockito.ArgumentMatchers.argThat(
                p ->
                    "deepseek".equals(p.getProvider().name())
                        && "deepseek-chat".equals(p.getProvider().model())),
            any());
  }

  @Test
  @DisplayName("generate_author model未配置_503可读错误_不发model=null")
  void generate_missingAuthorModel_throwsBeforeCallingLlm() {
    AgentLifecycleService noModel =
        new AgentLifecycleService(
            agentLoader,
            profileRegistry,
            agentScheduler,
            agentStore,
            providerPort,
            "deepseek",
            "",
            "deepseek");
    when(profileRegistry.find("x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> noModel.generateFiles("x", "描述"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("oryxos.author.model");

    verify(providerPort, never()).chat(anyString(), any(), any());
  }

  @Test
  @DisplayName("generate_LLM产出非法_400可读原因_不落盘不注册")
  void generate_invalidLlmOutput_throwsWithReadableReason() {
    when(profileRegistry.find("bad")).thenReturn(Optional.empty());
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    when(agentLoader.deriveProfile(eq("bad"), any()))
        .thenThrow(
            new IllegalArgumentException("Agent 'bad': missing required field 'provider.name'"));
    when(providerPort.chat(eq("author-generator"), any(Profile.class), any()))
        .thenReturn(LlmResponse.text("---\nname: bad\n---\n缺 provider"));

    assertThatThrownBy(() -> lifecycle.generateFiles("bad", "描述"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider.name");

    verify(agentStore, never()).writeAll(anyString(), any());
    verify(profileRegistry, never()).register(any());
  }

  @Test
  @DisplayName("saveFiles_AGENT.md非法_校验先于落盘_不写坏目录")
  void saveFiles_invalidAgentMd_rejectedBeforeWriting() {
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    when(agentLoader.deriveProfile(eq("broken"), any()))
        .thenThrow(
            new IllegalArgumentException("Agent 'broken': missing required field 'provider.name'"));

    Map<String, String> files = Map.of("AGENT.md", "---\nname: broken\n---\n缺 provider");

    assertThatThrownBy(() -> lifecycle.saveFiles("broken", files))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider.name");

    verify(agentStore, never()).writeAll(anyString(), any());
    verify(agentStore, never()).writeAgentMd(anyString(), anyString());
    verify(profileRegistry, never()).register(any());
  }

  @Test
  @DisplayName("saveFiles_有效文件组_先更新AGENT.md重注册_再写全部文件")
  void saveFiles_validFiles_writesAndReregisters() {
    when(agentLoader.parseAgentMd(anyString())).thenReturn(new ParsedAgentMd(Map.of(), ""));
    Profile fresh = new Profile();
    fresh.setName("daily-weather");
    when(agentLoader.deriveProfile(eq("daily-weather"), any())).thenReturn(fresh);
    when(profileRegistry.find("daily-weather")).thenReturn(Optional.of(fresh));

    Map<String, String> files =
        Map.of("AGENT.md", "---\nname: daily-weather\n---\n正文", "scripts/report.md", "# 报告格式");

    Profile result = lifecycle.saveFiles("daily-weather", files);

    assertThat(result).isSameAs(fresh);
    verify(agentStore).writeAgentMd("daily-weather", "---\nname: daily-weather\n---\n正文");
    verify(agentStore).writeAll("daily-weather", files);
    verify(profileRegistry).register(fresh);
  }
}
