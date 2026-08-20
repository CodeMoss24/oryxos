package com.oryxos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.agent.AgentStore;
import com.oryxos.web.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 第 30 节验收 harness:WorkspaceApiController(真实临时工作区 + 真实 AgentStore,防穿越用真实 resolveWorkspacePath)。
 *
 * <p>守点(课件原样):tree 结构可钻进 Agent 目录(agents/&lt;name&gt;/scripts/... 可展开);file?path= 目录穿越 → 400;
 * 正常文件返回内容;写文件落盘;编辑 agents/&lt;name&gt;/AGENT.md 走 lifecycle.update(写+校验+重注册)。
 */
@DisplayName("WorkspaceApiController — 工作区浏览与编辑(第 30 节)")
class WorkspaceApiControllerTest {

  @TempDir Path workspace;

  private AgentStore agentStore;
  private AgentLifecycleService lifecycle;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    agentStore = new AgentStore(workspace);
    lifecycle = Mockito.mock(AgentLifecycleService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new WorkspaceApiController(agentStore, lifecycle))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("tree_返回agents与archive目录树_可钻进Agent子目录")
  void tree_returnsAgentAndArchiveDirs_recursiveIntoAgentDir() throws Exception {
    Path agent = workspace.resolve("agents/daily-weather");
    Files.createDirectories(agent.resolve("scripts"));
    Files.writeString(agent.resolve("AGENT.md"), "---\nname: daily-weather\n---\n正文");
    Files.writeString(agent.resolve("scripts/report.md"), "# 报告格式");
    Files.createDirectories(workspace.resolve("archive/old-agent"));
    Files.writeString(workspace.resolve("archive/old-agent/AGENT.md"), "---\nname: old\n---");

    mockMvc
        .perform(get("/api/v1/workspace/tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(".oryxos"))
        .andExpect(jsonPath("$..children[?(@.name=='agents')]").exists())
        .andExpect(jsonPath("$..children[?(@.name=='daily-weather')]").exists())
        .andExpect(jsonPath("$..children[?(@.name=='scripts')]").exists())
        .andExpect(jsonPath("$..children[?(@.name=='archive')]").exists());
  }

  @Test
  @DisplayName("file_正常文件_返回内容")
  void readFile_returnsContent() throws Exception {
    Files.createDirectories(workspace.resolve("agents/daily-weather"));
    Files.writeString(
        workspace.resolve("agents/daily-weather/AGENT.md"), "---\nname: daily-weather\n---\n正文");

    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents/daily-weather/AGENT.md"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("---\nname: daily-weather\n---\n正文"));
  }

  @Test
  @DisplayName("file_目录穿越_400不读盘")
  void readFile_traversal_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "../../etc/passwd"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("path escapes workspace: ../../etc/passwd"));
  }

  @Test
  @DisplayName("file_不存在的文件_400")
  void readFile_missing_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents/ghost/AGENT.md"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("写文件_普通文件直接落盘")
  void writeFile_writesToDisk() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspace/file")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"agents/daily-weather/REFERENCE.md\",\"content\":\"参考文档\"}"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.message").value("file saved: agents/daily-weather/REFERENCE.md"));

    assertThat(Files.readString(workspace.resolve("agents/daily-weather/REFERENCE.md")))
        .isEqualTo("参考文档");
  }

  @Test
  @DisplayName("写文件_目录穿越_400不落盘")
  void writeFile_traversal_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspace/file")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"../../tmp/evil\",\"content\":\"x\"}"))
        .andExpect(status().isBadRequest());

    assertThat(Files.exists(Path.of("/tmp/evil"))).isFalse();
  }

  @Test
  @DisplayName("写文件_编辑agents/<name>/AGENT.md_走lifecycle.update_重注册生效")
  void writeFile_agentMd_goesThroughLifecycleUpdate() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspace/file")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"path\":\"agents/daily-weather/AGENT.md\",\"content\":\"---\\nname: daily-weather\\n---\\n新正文\"}"))
        .andExpect(status().isOk());

    verify(lifecycle).update("daily-weather", "---\nname: daily-weather\n---\n新正文");
  }
}
