package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.core.scheduler.ScheduleConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AgentLoader — Profile loading from AGENT.md frontmatter")
class AgentLoaderTest {

  @TempDir Path agentsDir;

  @Test
  @DisplayName("合法 AGENT.md 全字段解析为 Profile")
  void legalAgentMdFullFieldParse() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                description: "Daily weather reporter"
                identity:
                  agent_name: "Weather Bot"
                  prompt: "You report weather."
                provider:
                  name: deepseek
                  model: deepseek-chat
                  temperature: 0.7
                tools:
                  - http_get
                  - read_file
                skills:
                  - weather-skill
                mcp_servers:
                  - weather-mcp
                channels:
                  - cli
                bootstrap:
                  - AGENTS.md
                  - SOUL.md
                ---
                # Weather Agent
                Report daily weather.
                """);

    int count = loader.scanAndRegister(registry);
    assertThat(count).isEqualTo(1);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getName()).isEqualTo("weather");
    assertThat(profile.getDescription()).isEqualTo("Daily weather reporter");
    assertThat(profile.getIdentity().agentName()).isEqualTo("Weather Bot");
    assertThat(profile.getProvider().name()).isEqualTo("deepseek");
    assertThat(profile.getProvider().model()).isEqualTo("deepseek-chat");
    assertThat(profile.getProvider().temperature()).isEqualTo(0.7);
    // 31 节:tools 不再进 Profile(frontmatter 里写了也忽略——工具走全局列表)
    assertThat(profile.getSkills()).containsExactly("weather-skill");
    assertThat(profile.getMcpServers()).containsExactly("weather-mcp");
    assertThat(profile.getBootstrap()).containsExactly("AGENTS.md", "SOUL.md");
  }

  @Test
  @DisplayName("frontmatter 只有必填字段时仍能解析成功")
  void minimalFrontmatterParse() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "minimal",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                # Minimal agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("minimal").orElseThrow();

    assertThat(profile.getProvider().name()).isEqualTo("deepseek");
    assertThat(profile.getProvider().model()).isEqualTo("deepseek-chat");
  }

  @Test
  @DisplayName("坏 AGENT.md 不阻断其余加载")
  void badFileDoesNotBlockRemainingLoads() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();

    // Write a bad directory with no AGENT.md
    Path badDir = agentsDir.resolve("bad-agent");
    try {
      Files.createDirectory(badDir);
    } catch (Exception ignored) {
    }

    writeAgent(
        "good-agent",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                # Good agent
                """);

    int count = loader.scanAndRegister(registry);
    // Only good-agent loaded; bad-agent skipped silently
    assertThat(count).isEqualTo(1);
    assertThat(registry.find("good-agent")).isPresent();
    assertThat(registry.find("bad-agent")).isEmpty();
  }

  @Test
  @DisplayName("空 agents 目录不抛异常")
  void emptyAgentsDirDoesNotThrow() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    assertThatNoException().isThrownBy(() -> loader.scanAndRegister(registry));
  }

  @Test
  @DisplayName("agents 目录不存在时返回 0")
  void nonExistentDirReturnsZero() {
    var loader = new AgentLoader(agentsDir.resolve("nonexistent"));
    var registry = new ProfileRegistry();
    assertThat(loader.scanAndRegister(registry)).isEqualTo(0);
  }

  @Test
  @DisplayName("frontmatter schedules 全字段解析为 ScheduleConfig")
  void schedulesFullFieldParse() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                schedules:
                  - id: daily-weather
                    cron: "0 9 * * *"
                    zone: Asia/Shanghai
                    message: 到点了，查一下今天的天气
                ---
                # Weather Agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getSchedules()).hasSize(1);
    var sc = profile.getSchedules().get(0);
    assertThat(sc.id()).isEqualTo("daily-weather");
    assertThat(sc.cron()).isEqualTo("0 9 * * *");
    assertThat(sc.zone()).isEqualTo("Asia/Shanghai");
    assertThat(sc.message()).isEqualTo("到点了，查一下今天的天气");
  }

  @Test
  @DisplayName("schedules 条目缺 id 被跳过，其余条目照常解析")
  void scheduleWithoutIdIsSkippedOthersSurvive() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                schedules:
                  - cron: "0 9 * * *"
                  - id: hourly-digest
                    cron: "0 * * * *"
                    message: 报一下进展
                ---
                # Weather Agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getSchedules()).hasSize(1);
    assertThat(profile.getSchedules().get(0).id()).isEqualTo("hourly-digest");
  }

  @Test
  @DisplayName("schedules 条目缺 zone 存 null，缺 message 存 null")
  void scheduleWithoutZoneKeepsNull() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                schedules:
                  - id: daily-weather
                    cron: "0 9 * * *"
                ---
                # Weather Agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getSchedules()).hasSize(1);
    var sc = profile.getSchedules().get(0);
    assertThat(sc.zone()).isNull();
    assertThat(sc.message()).isNull();
  }

  @Test
  @DisplayName("一个 Agent 声明多条 schedules 全部解析")
  void multipleSchedulesAllParsed() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                schedules:
                  - id: daily-weather
                    cron: "0 9 * * *"
                    zone: Asia/Shanghai
                    message: 早报
                  - id: evening-digest
                    cron: "0 18 * * *"
                    zone: Asia/Shanghai
                    message: 晚报
                ---
                # Weather Agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getSchedules()).hasSize(2);
    assertThat(profile.getSchedules().stream().map(ScheduleConfig::id))
        .containsExactly("daily-weather", "evening-digest");
  }

  @Test
  @DisplayName("无 schedules 键时为空列表")
  void noSchedulesKeyYieldsEmptyList() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "weather",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                # Weather Agent
                """);

    loader.scanAndRegister(registry);
    var profile = registry.find("weather").orElseThrow();

    assertThat(profile.getSchedules()).isEmpty();
  }

  @Test
  @DisplayName("ParseAgentMd分离 frontmatter 和正文")
  void parseAgentMdSeparatesFrontmatterAndBody() throws Exception {
    var loader = new AgentLoader(agentsDir);
    Path md =
        writeAgent(
            "test",
            """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                # Task instructions
                Do the thing.
                """);

    var parsed = loader.parseAgentMd(md);
    assertThat(parsed.frontmatter()).containsKey("provider");
    assertThat(parsed.body()).contains("Do the thing");
  }

  @Test
  @DisplayName("缺 provider.name 必填项 → 报错点名 Agent 和字段")
  void missingProviderName_throwsNamingAgentAndField() throws Exception {
    var loader = new AgentLoader(agentsDir);
    var parsed =
        loader.parseAgentMd(
            writeAgentMd(
                "no-provider",
                """
            ---
            description: "no provider"
            ---
            # body
            """));
    assertThatThrownBy(() -> loader.deriveProfile("no-provider", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent 'no-provider': missing required field 'provider.name'");
  }

  @Test
  @DisplayName("provider.name 为空串 → 报错点名")
  void blankProviderName_throwsNamingAgentAndField() throws Exception {
    var loader = new AgentLoader(agentsDir);
    var parsed =
        loader.parseAgentMd(
            writeAgentMd(
                "blank-provider",
                """
            ---
            provider:
              name: ""
              model: deepseek-chat
            ---
            # body
            """));
    assertThatThrownBy(() -> loader.deriveProfile("blank-provider", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent 'blank-provider': missing required field 'provider.name'");
  }

  @Test
  @DisplayName("缺 provider 整段 → 报错点名")
  void missingProviderSection_throwsNamingAgentAndField() throws Exception {
    var loader = new AgentLoader(agentsDir);
    var parsed =
        loader.parseAgentMd(
            writeAgentMd(
                "no-provider-section",
                """
                    ---
                    description: "no provider section"
                    ---
                    # body
                    """));
    assertThatThrownBy(() -> loader.deriveProfile("no-provider-section", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent 'no-provider-section': missing required field 'provider.name'");
  }

  @Test
  @DisplayName("name 为空 → 报错点名(unknown)")
  void blankName_throwsNamingUnknown() throws Exception {
    var loader = new AgentLoader(agentsDir);
    var parsed =
        loader.parseAgentMd(
            writeAgentMd(
                "some",
                """
                    ---
                    provider:
                      name: deepseek
                      model: deepseek-chat
                    ---
                    # body
                    """));
    assertThatThrownBy(() -> loader.deriveProfile("  ", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent '<unknown>': missing required field 'name'");
  }

  @Test
  @DisplayName("缺 provider 必填项的 Agent 不阻断其余加载")
  void missingRequiredField_doesNotBlockRemainingLoads() {
    var loader = new AgentLoader(agentsDir);
    var registry = new ProfileRegistry();
    writeAgent(
        "broken",
        """
                ---
                description: "no provider"
                ---
                # body
                """);
    writeAgent(
        "good",
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                # body
                """);

    int count = loader.scanAndRegister(registry);
    assertThat(count).isEqualTo(1);
    assertThat(registry.find("good")).isPresent();
    assertThat(registry.find("broken")).isEmpty();
  }

  @Test
  @DisplayName("listResources 识别 scripts/skills/REFERENCE.md 是否存在")
  void listResources_identifiesOptionalResources() {
    var loader = new AgentLoader(agentsDir);
    Path dir = agentsDir.resolve("rich");
    try {
      Files.createDirectories(dir.resolve("scripts"));
      Files.createDirectories(dir.resolve("skills"));
      Files.writeString(dir.resolve("REFERENCE.md"), "# ref");
      Files.writeString(
          dir.resolve("AGENT.md"),
          """
              ---
              provider:
                name: deepseek
                model: deepseek-chat
              ---
              # body
              """);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    var resources = loader.listResources(dir);
    assertThat(resources).containsEntry("scripts", true);
    assertThat(resources).containsEntry("skills", true);
    assertThat(resources).containsEntry("reference", true);
  }

  @Test
  @DisplayName("listResources 对无可选资源的目录全判 false")
  void listResources_allFalseWhenAbsent() {
    var loader = new AgentLoader(agentsDir);
    Path dir = agentsDir.resolve("bare");
    try {
      Files.createDirectories(dir);
      Files.writeString(
          dir.resolve("AGENT.md"),
          """
              ---
              provider:
                name: deepseek
                model: deepseek-chat
              ---
              # body
              """);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    var resources = loader.listResources(dir);
    assertThat(resources).containsEntry("scripts", false);
    assertThat(resources).containsEntry("skills", false);
    assertThat(resources).containsEntry("reference", false);
  }

  private Path writeAgentMd(String name, String content) {
    try {
      Path dir = agentsDir.resolve(name);
      Files.createDirectories(dir);
      Path md = dir.resolve("AGENT.md");
      Files.writeString(md, content);
      return md;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Path writeAgent(String name, String content) {
    try {
      Path dir = agentsDir.resolve(name);
      Files.createDirectories(dir);
      Path md = dir.resolve("AGENT.md");
      Files.writeString(md, content);
      return md;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
