package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.agent.ToolExecutionContext;
import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.session.Session;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 第 30 节验收 harness:per-agent 记忆回归(真实 MarkdownMemoryStore + MemoryServiceImpl)。
 *
 * <p>守点(课件原样 5.2.1):save_memory/recall_memory 按当前 Agent(ToolExecutionContext)落
 * agents/&lt;name&gt;/MEMORY.md、全局 文件不动;无 Agent 上下文回退全局 memory/MEMORY.md(SPI 向后兼容);读路径 buildContext
 * 取 session.profileName()、readAll 取入参,代理后复原现场。
 */
@DisplayName("Per-agent 记忆 — 记忆跟着 Agent 走(第 30 节)")
class PerAgentMemoryTest {

  @TempDir Path workspace;

  private MarkdownMemoryStore store;
  private MemoryServiceImpl service;

  @BeforeEach
  void setUp() {
    store = new MarkdownMemoryStore(workspace.toString());
    service = new MemoryServiceImpl(store);
  }

  private static Path globalFile(Path workspace) {
    return workspace.resolve("memory").resolve("MEMORY.md");
  }

  private static Path agentFile(Path workspace, String name) {
    return workspace.resolve("agents").resolve(name).resolve("MEMORY.md");
  }

  @Test
  @DisplayName("Agent上下文下写记忆_落agents/<name>/MEMORY.md_全局文件不动")
  void appendInAgentContext_writesPerAgentFile_leavesGlobalUntouched() {
    ToolExecutionContext.set("daily-weather");
    try {
      store.append("用户喜欢晴天", MemoryScope.ARCHIVAL);
    } finally {
      ToolExecutionContext.clear();
    }

    Path expected = agentFile(workspace, "daily-weather");
    assertThat(expected).exists();
    assertThat(expected).content().contains("用户喜欢晴天");
    assertThat(globalFile(workspace)).doesNotExist();
  }

  @Test
  @DisplayName("无Agent上下文_回退全局memory/MEMORY.md_契约不变")
  void appendWithoutContext_fallsBackToGlobalFile() {
    store.append("全局偏好", MemoryScope.CORE);

    assertThat(globalFile(workspace)).exists().content().contains("全局偏好");
    assertThat(agentFile(workspace, "any")).doesNotExist();
  }

  @Test
  @DisplayName("buildContext_按session的profileName_读对应Agent的记忆")
  void buildContext_readsMemoryOfTheSessionsAgent() {
    // 写入落在 daily-weather 的专属文件
    ToolExecutionContext.set("daily-weather");
    try {
      store.append("天气偏好", MemoryScope.ARCHIVAL);
    } finally {
      ToolExecutionContext.clear();
    }

    Session session =
        new Session("admin:console:daily-weather", "daily-weather", "admin", "console");
    assertThat(service.buildContext(session)).contains("天气偏好");

    // 别的 Agent 的 session 读不到
    Session other = new Session("admin:console:other", "other", "admin", "console");
    assertThat(service.buildContext(other)).isEmpty();
  }

  @Test
  @DisplayName("readAll_取入参_读指定Agent的完整记忆_不越界")
  void readAll_takesAgentName_readsOnlyThatAgentsMemory() {
    ToolExecutionContext.set("daily-weather");
    try {
      store.append("晴天记录", MemoryScope.ARCHIVAL);
    } finally {
      ToolExecutionContext.clear();
    }

    assertThat(service.readAll("daily-weather")).contains("晴天记录");
    assertThat(service.readAll("other-agent")).isEmpty();
    // 复原后无上下文:不污染同线程后续调用
    assertThat(ToolExecutionContext.get()).isNull();
  }

  @Test
  @DisplayName("recall_按Agent上下文_只搜该Agent的归档区")
  void recall_inAgentContext_searchesOnlyThatAgentsArchive() {
    ToolExecutionContext.set("daily-weather");
    try {
      store.append("北京明日有雨", MemoryScope.ARCHIVAL);
      store.append("用户偏好晴天", MemoryScope.ARCHIVAL);
      // recall 与 save 同路径:都在工具执行上下文内,同样落到 per-agent 文件
      assertThat(store.recallByKeyword("北京")).hasSize(1).allMatch(line -> line.contains("北京"));
    } finally {
      ToolExecutionContext.clear();
    }

    // 全局没有"北京"条目,证明 recall 打的是 per-agent 文件
    assertThat(Files.exists(globalFile(workspace))).isFalse();
  }
}
