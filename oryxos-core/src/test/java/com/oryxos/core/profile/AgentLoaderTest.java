package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
    assertThat(profile.getTools()).containsExactly("http_get", "read_file");
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
