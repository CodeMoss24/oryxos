package com.oryxos.core.context;

import static org.junit.jupiter.api.Assertions.*;

import com.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextLoaderTest {

  @TempDir Path tempDir;

  private ContextLoader loader;
  private Profile profile;

  @BeforeEach
  void setUp() throws IOException {
    loader = new ContextLoader(tempDir);
    profile = new Profile();
    profile.setName("test-agent");
    profile.setBootstrap(List.of("AGENTS.md"));
    // Create initial bootstrap file
    Files.writeString(tempDir.resolve("AGENTS.md"), "initial content");
  }

  @Test
  @DisplayName("改文件后下一次 build 立即读到新内容")
  void reloadsFileOnEachBuild() throws IOException {
    String first = loader.loadSystemPrompt(profile, "agent body");
    assertTrue(first.contains("initial content"));

    Files.writeString(tempDir.resolve("AGENTS.md"), "updated content");
    String second = loader.loadSystemPrompt(profile, "agent body");

    assertTrue(second.contains("updated content"));
    assertFalse(second.contains("initial content"));
  }

  @Test
  @DisplayName("Bootstrap 缺失 WARN（不异常）")
  void warnsWhenBootstrapFileMissing() {
    profile.setBootstrap(List.of("NONEXISTENT.md"));

    String result = loader.loadSystemPrompt(profile, "agent body");

    // Should not throw; prompt should still contain agent body
    assertTrue(result.contains("agent body"));
    assertFalse(result.contains("NONEXISTENT.md"));
  }

  @Test
  @DisplayName("system prompt 末尾含当前日期时间")
  void systemPromptEndsWithDateTime() {
    String result = loader.loadSystemPrompt(profile, "agent body");

    assertTrue(result.contains("当前时间"));
  }
}
