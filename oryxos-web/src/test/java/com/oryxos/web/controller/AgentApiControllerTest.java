package com.oryxos.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.AgentService;
import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.scheduler.ScheduleConfig;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 第 30 节验收 harness:AgentApiController 管理端点薄转发(standalone MockMvc)。
 *
 * <p>守点:create 成功返回 AgentView、name 冲突 → 400 统一 ApiResponse、不存在 → 404、列表、update/delete 薄转发到
 * AgentLifecycleService。错误映射复用 GlobalExceptionHandler(400/404),controller 不自己拼错误响应。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentApiController — 管理端点薄转发(第 30 节)")
class AgentApiControllerTest {

  @Mock private AgentLifecycleService lifecycle;
  @Mock private AgentService agentService;
  @Mock private SessionManager sessionManager;
  @Mock private ProfileRegistry profileRegistry;
  @Mock private AsyncTaskExecutor taskExecutor;
  @Mock private MemoryService memoryService;

  @InjectMocks private AgentApiController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Profile profileNamed(String name) {
    Profile profile = new Profile();
    profile.setName(name);
    profile.setDescription(name + " 的任务");
    profile.setProvider(new Profile.Provider("deepseek", "deepseek-chat", null));
    profile.setTools(List.of("http_get", "notify"));
    profile.setSchedules(
        List.of(new ScheduleConfig(name + "-morning", "0 0 9 * * *", "Asia/Shanghai", "到点了")));
    return profile;
  }

  @Test
  @DisplayName("create_成功返回 AgentView(统一 ApiResponse 信封)")
  void create_returnsAgentView() throws Exception {
    when(lifecycle.create("weather-daily", "每日天气")).thenReturn(profileNamed("weather-daily"));

    mockMvc
        .perform(
            post("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"weather-daily\",\"description\":\"每日天气\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.name").value("weather-daily"))
        .andExpect(jsonPath("$.data.provider").value("deepseek"))
        .andExpect(jsonPath("$.data.schedules[0].cron").value("0 0 9 * * *"));
  }

  @Test
  @DisplayName("create_name冲突_400 统一 ApiResponse、可读原因")
  void create_nameConflict_returns400() throws Exception {
    doThrow(new IllegalArgumentException("Agent 已存在: dupe")).when(lifecycle).create("dupe", "x");

    mockMvc
        .perform(
            post("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"dupe\",\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Agent 已存在: dupe"));
  }

  @Test
  @DisplayName("get_不存在的 Agent_404 统一 ApiResponse")
  void get_notFound_returns404() throws Exception {
    when(lifecycle.find("ghost")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/agents/ghost"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("agent not found: ghost"));
  }

  @Test
  @DisplayName("get_存在的 Agent_返回详情")
  void get_existing_returnsAgentView() throws Exception {
    when(lifecycle.find("weather-daily")).thenReturn(Optional.of(profileNamed("weather-daily")));

    mockMvc
        .perform(get("/api/v1/agents/weather-daily"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("weather-daily"))
        .andExpect(jsonPath("$.data.description").value("weather-daily 的任务"));
  }

  @Test
  @DisplayName("list_返回全部 Agent 视图")
  void list_returnsAllAgentViews() throws Exception {
    when(lifecycle.list()).thenReturn(List.of(profileNamed("a"), profileNamed("b")));

    mockMvc
        .perform(get("/api/v1/agents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)));
  }

  @Test
  @DisplayName("update_覆写 AGENT.md_薄转发并返回新视图")
  void update_passesContentThrough() throws Exception {
    when(lifecycle.update("reconcile", "---\nname: reconcile\n..."))
        .thenReturn(profileNamed("reconcile"));

    mockMvc
        .perform(
            put("/api/v1/agents/reconcile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"---\\nname: reconcile\\n...\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("reconcile"));

    verify(lifecycle).update("reconcile", "---\nname: reconcile\n...");
  }

  @Test
  @DisplayName("delete_薄转发并返回归档消息")
  void delete_passesThrough() throws Exception {
    mockMvc
        .perform(delete("/api/v1/agents/old-agent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.message").value("agent archived: old-agent"));

    verify(lifecycle).delete("old-agent");
  }

  @Test
  @DisplayName("update_非法内容_400 不写坏目录")
  void update_invalidContent_returns400() throws Exception {
    doThrow(new IllegalArgumentException("Agent 'bad': missing required field 'provider.name'"))
        .when(lifecycle)
        .update(any(), any());

    mockMvc
        .perform(
            put("/api/v1/agents/bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"---\\nname: bad\\n...\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("generate-files_薄转发_返回文件草稿")
  void generateFiles_passesDescriptionThrough() throws Exception {
    when(lifecycle.generateFiles("daily-weather", "每天早上推送天气"))
        .thenReturn(Map.of("AGENT.md", "---\nname: daily-weather\n---\n正文"));

    mockMvc
        .perform(
            post("/api/v1/agents/daily-weather/generate-files")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"每天早上推送天气\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data['AGENT.md']").value("---\nname: daily-weather\n---\n正文"));
  }

  @Test
  @DisplayName("generate-files_产出非法_400 可读原因")
  void generateFiles_invalidOutput_returns400() throws Exception {
    doThrow(new IllegalArgumentException("Agent 'bad': missing required field 'provider.name'"))
        .when(lifecycle)
        .generateFiles(any(), any());

    mockMvc
        .perform(
            post("/api/v1/agents/bad/generate-files")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("files_薄转发_保存并返回新视图")
  void saveFiles_passesFileMapThrough() throws Exception {
    when(lifecycle.saveFiles(eq("daily-weather"), any())).thenReturn(profileNamed("daily-weather"));

    mockMvc
        .perform(
            post("/api/v1/agents/daily-weather/files")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":{\"AGENT.md\":\"---\\nname: daily-weather\\n---\\n正文\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("daily-weather"));
  }

  @Test
  @DisplayName("memory_返回该Agent的专属记忆_按入参转发")
  void getMemory_readsPerAgentMemory() throws Exception {
    when(profileRegistry.find("daily-weather"))
        .thenReturn(Optional.of(profileNamed("daily-weather")));
    when(memoryService.readAll("daily-weather")).thenReturn("## 核心记忆\n- 用户喜欢晴天");

    mockMvc
        .perform(get("/api/v1/agents/daily-weather/memory"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memory").value("## 核心记忆\n- 用户喜欢晴天"));

    verify(memoryService).readAll("daily-weather");
  }

  @Test
  @DisplayName("memory_Agent不存在_404")
  void getMemory_agentNotFound_returns404() throws Exception {
    when(profileRegistry.find("ghost")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/agents/ghost/memory"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("agent not found: ghost"));
  }

  @Test
  @DisplayName("session_返回固定会话视图_身份固定admin+console")
  void getSession_returnsFixedSessionView() throws Exception {
    when(profileRegistry.find("daily-weather"))
        .thenReturn(Optional.of(profileNamed("daily-weather")));
    Session session =
        new Session("admin:console:daily-weather", "daily-weather", "admin", "console");
    session.append(new Message("user", "你好", List.of(), null));
    session.append(new Message("assistant", "今天多云转晴", List.of(), null));
    when(sessionManager.getOrCreate("admin", "console", "daily-weather")).thenReturn(session);

    mockMvc
        .perform(get("/api/v1/agents/daily-weather/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value("admin:console:daily-weather"))
        .andExpect(jsonPath("$.data.profileName").value("daily-weather"))
        .andExpect(jsonPath("$.data.messages", hasSize(2)));

    verify(sessionManager).getOrCreate("admin", "console", "daily-weather");
  }

  @Test
  @DisplayName("session_Agent不存在_404")
  void getSession_agentNotFound_returns404() throws Exception {
    when(profileRegistry.find("ghost")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/agents/ghost/session"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("agent not found: ghost"));
  }

  @Test
  @DisplayName("session消息_固定会话发消息触发ReAct_上下文落在同一会话")
  void sendSessionMessage_usesFixedSessionAndProcesses() throws Exception {
    when(profileRegistry.find("daily-weather"))
        .thenReturn(Optional.of(profileNamed("daily-weather")));
    Session session =
        new Session("admin:console:daily-weather", "daily-weather", "admin", "console");
    when(sessionManager.getOrCreate("admin", "console", "daily-weather")).thenReturn(session);
    when(agentService.process(session, "明天天气如何")).thenReturn("明天晴天");
    // 真实执行提交的任务:让 process 真的被调用,便于断言上下文落在这条固定会话
    when(taskExecutor.submit(any(Callable.class)))
        .thenAnswer(
            inv -> {
              Callable<?> callable = inv.getArgument(0);
              return java.util.concurrent.CompletableFuture.completedFuture(callable.call());
            });

    mockMvc
        .perform(
            post("/api/v1/agents/daily-weather/session/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"明天天气如何\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reply").value("明天晴天"));

    verify(sessionManager).getOrCreate("admin", "console", "daily-weather");
    verify(agentService).process(session, "明天天气如何");
  }
}
