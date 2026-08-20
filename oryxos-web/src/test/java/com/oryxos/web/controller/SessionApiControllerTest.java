package com.oryxos.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 会话管理五端点切片测试(课件验收 harness):超 32KB→400、会话不存在→404、正常请求处理引擎恰被调一次 (Controller 没夹带私货)、历史只返回最近 100
 * 条、归档落库、会话列表只读端点。
 */
@WebMvcTest
@Import({SessionApiController.class, GlobalExceptionHandler.class})
@DisplayName("SessionApiControllerTest — 会话管理五端点:校验、404、引擎恰被调一次")
class SessionApiControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AgentService agentService;

  @MockBean private SessionManager sessionManager;

  private Session session;

  @BeforeEach
  void setUp() {
    session = new Session("s-1", "p1", "web", "user1");
  }

  @Test
  @DisplayName("创建会话_返回 SessionManager 生成的 session_id")
  void createReturnsSessionIdFromSessionManager() throws Exception {
    when(sessionManager.getOrCreate("web", "anonymous", "p1")).thenReturn(session);

    mockMvc
        .perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profile_name\":\"p1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.session_id").value("s-1"));
  }

  @Test
  @DisplayName("创建会话_profile_name 缺失返回 400")
  void createMissingProfileNameReturns400() throws Exception {
    mockMvc
        .perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("发消息_超过32KB返回400且引擎不被调用")
  void sendMessageOver32KbReturns400WithoutCallingEngine() throws Exception {
    String oversized = "a".repeat(32 * 1024 + 1);

    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + oversized + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("消息为空或超过 32KB"));

    verifyNoInteractions(agentService);
  }

  @Test
  @DisplayName("发消息_content 为 null 返回 400")
  void sendMessageNullContentReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("发消息_恰好32KB视为合法_引擎被调用")
  void sendMessageExactly32KbIsAccepted() throws Exception {
    String boundary = "a".repeat(32 * 1024);
    when(sessionManager.get("s-1")).thenReturn(Optional.of(session));
    when(agentService.process(any(Session.class), anyString())).thenReturn("reply-ok");

    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + boundary + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reply").value("reply-ok"));

    verify(agentService, times(1)).process(eq(session), eq(boundary));
  }

  @Test
  @DisplayName("发消息_会话不存在返回404且引擎不被调用")
  void sendMessageSessionNotFoundReturns404() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hi\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    verifyNoInteractions(agentService);
  }

  @Test
  @DisplayName("发消息_正常请求 agentService.process 恰被调一次(Controller 没夹带私货)")
  void sendMessageCallsEngineExactlyOnce() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.of(session));
    when(agentService.process(any(Session.class), anyString())).thenReturn("reply-1");

    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.reply").value("reply-1"));

    verify(agentService, times(1)).process(eq(session), eq("hello"));
  }

  @Test
  @DisplayName("内部异常细节_绝不能出现在500响应里——统一话术且不泄漏(课件关键回归)")
  void internalErrorDetailsNeverLeakInto500Response() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.of(session));
    when(agentService.process(any(), any()))
        .thenThrow(new RuntimeException("jdbc:sqlite:/data/oryxos.db connect failed"));

    mockMvc
        .perform(
            post("/api/v1/sessions/s-1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hi\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("内部错误"))
        .andExpect(content().string(not(containsString("jdbc:sqlite"))));
  }

  @Test
  @DisplayName("查历史_只返回最近100条")
  void historyReturnsOnlyLast100Messages() throws Exception {
    for (int i = 1; i <= 150; i++) {
      session.append(Message.user("msg-" + i));
    }
    when(sessionManager.get("s-1")).thenReturn(Optional.of(session));

    mockMvc
        .perform(get("/api/v1/sessions/s-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.messages.length()").value(100))
        .andExpect(jsonPath("$.data.messages[0].content").value("msg-51"))
        .andExpect(jsonPath("$.data.messages[99].content").value("msg-150"));
  }

  @Test
  @DisplayName("查历史_会话不存在返回404")
  void historySessionNotFoundReturns404() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/sessions/s-1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @DisplayName("归档_置 archived 并持久化")
  void archiveSetsStatusAndPersists() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.of(session));

    mockMvc
        .perform(delete("/api/v1/sessions/s-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("archived"));

    verify(sessionManager, times(1)).save(session);
    org.assertj.core.api.Assertions.assertThat(session.getStatus()).isEqualTo("archived");
  }

  @Test
  @DisplayName("归档_会话不存在返回404")
  void archiveSessionNotFoundReturns404() throws Exception {
    when(sessionManager.get("s-1")).thenReturn(Optional.empty());

    mockMvc
        .perform(delete("/api/v1/sessions/s-1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @DisplayName("会话列表_返回摘要DTO数组(第27节升级:listRecent+驼峰字段+status过滤)")
  void listReturnsSessionSummaries() throws Exception {
    Session s2 = new Session("s-2", "p2", "cli", "user2");
    when(sessionManager.listRecent(anyInt())).thenReturn(List.of(s2, session));

    mockMvc
        .perform(get("/api/v1/sessions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].sessionId").value("s-2"))
        .andExpect(jsonPath("$.data[0].profileName").value("p2"))
        .andExpect(jsonPath("$.data[0].channel").value("cli"))
        .andExpect(jsonPath("$.data[0].userId").value("user2"))
        .andExpect(jsonPath("$.data[0].status").value("active"))
        .andExpect(jsonPath("$.data[1].sessionId").value("s-1"));

    // ?status= 过滤:只保留匹配状态的会话
    session.setStatus("archived");
    mockMvc
        .perform(get("/api/v1/sessions").param("status", "archived"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].sessionId").value("s-1"));
  }
}
