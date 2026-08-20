package com.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.scheduler.AgentScheduler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 第 30 节验收 harness:WorkspaceWatcher(真实 WatchService + 真实文件,端到端)。
 *
 * <p>守点(课件原样):往 .oryxos/agents/ 手工丢一个 Agent 目录 → 监听事件触发 register(agentDir)、Agent 出现在
 * ProfileRegistry(免重启);删目录 → 注销;单个坏目录不拖垮监听(后续好目录仍被拾取)。
 */
@DisplayName("WorkspaceWatcher — 手工丢目录/删目录即上线注销(第 30 节)")
class WorkspaceWatcherTest {

  @TempDir Path workspace;

  private Path agentsDir;
  private ProfileRegistry registry;
  private AgentScheduler scheduler;
  private AgentLifecycleService lifecycle;
  private WorkspaceWatcher watcher;

  @BeforeEach
  void setUp() throws IOException {
    agentsDir = workspace.resolve("agents");
    Files.createDirectories(agentsDir);
    registry = new ProfileRegistry();
    scheduler = mock(AgentScheduler.class);
    AgentLoader loader = new AgentLoader(agentsDir);
    AgentStore store = new AgentStore(workspace);
    lifecycle =
        new AgentLifecycleService(
            loader,
            registry,
            scheduler,
            store,
            mock(com.oryxos.core.react.ProviderPort.class),
            "deepseek",
            "deepseek-chat",
            "deepseek");
    watcher = new WorkspaceWatcher(lifecycle, agentsDir);
  }

  @AfterEach
  void tearDown() {
    watcher.stop(); // 关 WatchService,守护线程退出,不留泄漏线程
  }

  private static String agentMd(String name) {
    return "---\n"
        + "name: "
        + name
        + "\n"
        + "description: 测试 Agent\n"
        + "provider:\n"
        + "  name: deepseek\n"
        + "  model: deepseek-chat\n"
        + "---\n"
        + "正文";
  }

  private static void dropDirectory(Path dir, String name) throws IOException {
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("AGENT.md"), agentMd(name));
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(condition.getAsBoolean()).as("timeout waiting for watcher").isTrue();
  }

  @Test
  @DisplayName("手工丢Agent目录_监听拾取_免重启出现在ProfileRegistry")
  void droppedDirectory_pickedUpByWatcher_withoutRestart() throws Exception {
    watcher.start();

    // 先建目录再写 AGENT.md,模拟 cp -r 的多文件时序
    Path dir = agentsDir.resolve("dropped-agent");
    dropDirectory(dir, "dropped-agent");

    await(() -> registry.exists("dropped-agent"));
    assertThat(registry.find("dropped-agent")).isPresent();
  }

  @Test
  @DisplayName("手工删目录_监听注销_从ProfileRegistry消失")
  void deletedDirectory_unregisteredByWatcher() throws Exception {
    // 先注册(与 watcher 同一段 register(agentDir),不必等事件)
    Path dir = agentsDir.resolve("doomed");
    dropDirectory(dir, "doomed");
    lifecycle.register(dir);
    assertThat(registry.exists("doomed")).isTrue();

    watcher.start();
    deleteRecursively(dir);

    await(() -> !registry.exists("doomed"));
  }

  @Test
  @DisplayName("单个坏目录不拖垮监听_其后丢的好目录仍被拾取")
  void badDirectory_doesNotBreakWatching() throws Exception {
    watcher.start();

    // 坏目录:缺必填字段 provider.name,注册失败只告警
    Path bad = agentsDir.resolve("bad-agent");
    Files.createDirectories(bad);
    Files.writeString(bad.resolve("AGENT.md"), "---\nname: bad-agent\n---\n正文");
    assertThat(registry.exists("bad-agent")).isFalse();

    // 好目录随后仍被拾取 → 循环没被坏目录拖垮
    Path good = agentsDir.resolve("good-agent");
    dropDirectory(good, "good-agent");

    await(() -> registry.exists("good-agent"));
    assertThat(registry.exists("bad-agent")).isFalse();
  }
}
