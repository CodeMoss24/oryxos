package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.exception.AgentTimeoutException;
import com.oryxos.web.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 调用 Controller(无状态调用)。
 *
 * <p>薄壳:校验 Agent 存在(404)、60 秒超时(504),实际处理走与 CLI 同一个 {@link AgentService#process}。 超时用 Spring 提供的
 * AsyncTaskExecutor(applicationTaskExecutor,虚拟线程)+ 经典 Future.get(timeout) ——不用
 * CompletableFuture、不自建线程池(H4 ⑤)。会话身份经 SessionManager 幂等复用,不在此拼接 session_id(H4 ④)。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

  /** Agent 调用超时上限(课件定值) */
  private static final long INVOKE_TIMEOUT_SECONDS = 60;

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ProfileRegistry profileRegistry;
  private final AsyncTaskExecutor taskExecutor;

  public AgentApiController(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AsyncTaskExecutor taskExecutor) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
    this.taskExecutor = taskExecutor;
  }

  @PostMapping("/{name}/invoke")
  public ApiResponse<Map<String, String>> invoke(
      @PathVariable String name, @RequestBody Map<String, String> body) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    String user = body.getOrDefault("user_id", "anonymous");
    String message = body.get("content");
    Session session = sessionManager.getOrCreate("web", user, name);
    return ApiResponse.ok(Map.of("reply", processWithTimeout(session, message)));
  }

  private String processWithTimeout(Session session, String message) {
    Future<String> future = taskExecutor.submit(() -> agentService.process(session, message));
    try {
      return future.get(INVOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new AgentTimeoutException("agent call timed out after 60s");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("agent call interrupted", e);
    } catch (ExecutionException e) {
      // 引擎异常原样上抛,交统一异常出口映射(503 Provider 故障等)
      throw new IllegalStateException("agent call failed", e.getCause());
    }
  }
}
