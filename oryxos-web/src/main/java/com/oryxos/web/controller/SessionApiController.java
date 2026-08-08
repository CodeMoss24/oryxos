package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Session;
import com.oryxos.web.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.*;

/** 会话管理 Controller。 核心 4 个端点:创建会话、发消息、查历史、归档。 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

  private final AgentService agentService;

  /** 核心阶段内存版存储,持久化待 storage 集成后替换 */
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public SessionApiController(AgentService agentService) {
    this.agentService = agentService;
  }

  @PostMapping
  public ApiResponse<Map<String, String>> create(@RequestBody Map<String, String> body) {
    String profile = body.get("profile_name");
    String userId = body.getOrDefault("user_id", "anonymous");
    String channel = body.getOrDefault("channel", "web");
    String sessionId = channel + "+" + userId + "+" + profile;
    Session session = new Session(sessionId, profile, channel, userId);
    sessions.put(sessionId, session);
    return ApiResponse.ok(Map.of("session_id", sessionId));
  }

  @PostMapping("/{id}/messages")
  public ApiResponse<Map<String, String>> sendMessage(
      @PathVariable String id, @RequestBody Map<String, String> body) {
    Session session = sessions.get(id);
    if (session == null) {
      throw new IllegalStateException("session not found: " + id);
    }
    String content = body.get("content");
    String reply = agentService.process(session, content);
    return ApiResponse.ok(Map.of("reply", reply));
  }

  @GetMapping("/{id}")
  public ApiResponse<List<Map<String, String>>> history(@PathVariable String id) {
    Session session = sessions.get(id);
    if (session == null) {
      throw new IllegalStateException("session not found: " + id);
    }
    List<Map<String, String>> messages =
        session.getMessages().stream()
            .map(m -> Map.of("role", m.role(), "content", m.content()))
            .toList();
    return ApiResponse.ok(messages);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, String>> archive(@PathVariable String id) {
    Session session = sessions.get(id);
    if (session != null) {
      session.setStatus("archived");
    }
    return ApiResponse.ok(Map.of("status", "archived"));
  }
}
