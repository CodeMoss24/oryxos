package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.MessageRequest;
import com.oryxos.web.dto.MessageResponse;
import com.oryxos.web.exception.InvalidRequestException;
import com.oryxos.web.exception.SessionNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理 Controller(5 个端点)。
 *
 * <p>薄壳:只做参数校验、响应包装、错误处理;发消息与 CLI 走同一个 {@link AgentService#process}。 session_id 不在此拼接——统一走 {@link
 * SessionManager}(H4 ④)。
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

  /** 单条消息上限(课件定值) */
  private static final int MAX_MESSAGE_LENGTH = 32 * 1024;

  /** 会话历史返回上限(课件定值) */
  private static final int MAX_HISTORY_SIZE = 100;

  private final AgentService agentService;
  private final SessionManager sessionManager;

  public SessionApiController(AgentService agentService, SessionManager sessionManager) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  @PostMapping
  public ApiResponse<Map<String, String>> create(@RequestBody Map<String, String> body) {
    String profile = body.get("profile_name");
    if (profile == null || profile.isBlank()) {
      throw new InvalidRequestException("profile_name is required");
    }
    String userId = body.getOrDefault("user_id", "anonymous");
    String channel = body.getOrDefault("channel", "web");
    Session session = sessionManager.getOrCreate(channel, userId, profile);
    return ApiResponse.ok(Map.of("session_id", session.getSessionId()));
  }

  @PostMapping("/{id}/messages")
  public ApiResponse<MessageResponse> send(
      @PathVariable String id, @RequestBody MessageRequest req) {
    if (req.content() == null || req.content().length() > MAX_MESSAGE_LENGTH) {
      throw new InvalidRequestException("消息为空或超过 32KB");
    }
    Session session = getExisting(id);
    String reply = agentService.process(session, req.content());
    return ApiResponse.ok(new MessageResponse(reply));
  }

  @GetMapping("/{id}")
  public ApiResponse<List<Map<String, String>>> history(@PathVariable String id) {
    Session session = getExisting(id);
    List<Map<String, String>> messages =
        session.getMessages().stream()
            .skip(Math.max(0, session.getMessages().size() - MAX_HISTORY_SIZE))
            .map(m -> Map.of("role", m.role(), "content", m.content()))
            .toList();
    return ApiResponse.ok(messages);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, String>> archive(@PathVariable String id) {
    Session session = getExisting(id);
    session.setStatus("archived");
    sessionManager.save(session);
    return ApiResponse.ok(Map.of("status", "archived"));
  }

  /** 会话列表(只读扩展端点):供管理台"会话列表"页与运维查询。 */
  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    List<Map<String, Object>> summaries =
        sessionManager.listAll().stream().map(SessionApiController::toSummary).toList();
    return ApiResponse.ok(summaries);
  }

  private Session getExisting(String id) {
    return sessionManager
        .get(id)
        .orElseThrow(() -> new SessionNotFoundException("session not found: " + id));
  }

  private static Map<String, Object> toSummary(Session s) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("session_id", s.getSessionId());
    summary.put("profile_name", s.getProfileName());
    summary.put("channel", s.getChannel());
    summary.put("user_id", s.getUserId());
    summary.put("status", s.getStatus());
    summary.put("last_active_at", s.getLastActiveAt());
    return summary;
  }
}
