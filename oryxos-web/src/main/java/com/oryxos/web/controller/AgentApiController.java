package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.dto.AgentView;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.CreateAgentRequest;
import com.oryxos.web.dto.SessionView;
import com.oryxos.web.dto.UpdateAgentRequest;
import com.oryxos.web.exception.AgentTimeoutException;
import com.oryxos.web.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 管理 Controller(第 30 节扩展)。
 *
 * <p>调用端点(26 节):薄壳——校验 Agent 存在(404)、60 秒超时(504),实际处理走与 CLI 同一个 {@link AgentService#process}。 超时用
 * Spring 提供的 AsyncTaskExecutor(applicationTaskExecutor,虚拟线程)+ 经典 Future.get(timeout) ——不用
 * CompletableFuture、不自建线程池 (H4 ⑤)。会话身份经 SessionManager 幂等复用,不在此拼接 session_id(H4 ④)。
 *
 * <p>管理端点(30 节):create/get/list/update/delete 薄转发给 {@link AgentLifecycleService}; 错误统一交
 * GlobalExceptionHandler 映射(400/404/503),统一 ApiResponse 信封。core 返回 Profile,web 层剪成 {@link
 * AgentView} 对外。
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
  private final AgentLifecycleService lifecycle;
  private final MemoryService memoryService;

  public AgentApiController(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AsyncTaskExecutor taskExecutor,
      AgentLifecycleService lifecycle,
      MemoryService memoryService) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
    this.taskExecutor = taskExecutor;
    this.lifecycle = lifecycle;
    this.memoryService = memoryService;
  }

  /** 创建:脚手架完整 Agent 目录 + 派生注册,失败回滚(不留半个 Agent);name 冲突/非法 → 400。 */
  @PostMapping
  public ApiResponse<AgentView> create(@RequestBody CreateAgentRequest request) {
    Profile profile = lifecycle.create(request.name(), request.description());
    return ApiResponse.ok(AgentView.from(profile));
  }

  /** 列表。 */
  @GetMapping
  public ApiResponse<List<AgentView>> list() {
    return ApiResponse.ok(lifecycle.list().stream().map(AgentView::from).toList());
  }

  /** 单个详情;不存在 → 404。 */
  @GetMapping("/{name}")
  public ApiResponse<AgentView> get(@PathVariable String name) {
    AgentView view =
        lifecycle
            .find(name)
            .map(AgentView::from)
            .orElseThrow(() -> new ResourceNotFoundException("agent not found: " + name));
    return ApiResponse.ok(view);
  }

  /** 覆写 AGENT.md;schedules 变更先注销旧句柄再注册新的;内容非法 → 400 不写坏目录。 */
  @PutMapping("/{name}")
  public ApiResponse<AgentView> update(
      @PathVariable String name, @RequestBody UpdateAgentRequest request) {
    Profile profile = lifecycle.update(name, request.content());
    return ApiResponse.ok(AgentView.from(profile));
  }

  /** 删除:注销定时 → 移出索引 → 目录归档(不物理删)。 */
  @DeleteMapping("/{name}")
  public ApiResponse<Map<String, String>> delete(@PathVariable String name) {
    lifecycle.delete(name);
    return ApiResponse.ok(Map.of("message", "agent archived: " + name));
  }

  /**
   * 一句话生成文件草稿:LLM 产出 AGENT.md(剥围栏、校验可解析)→ 返回 {相对路径 → 内容} 预览可改,不落盘不注册。 产出非法 → 400;author model 未配 →
   * 503。
   */
  @PostMapping("/{name}/generate-files")
  public ApiResponse<Map<String, String>> generateFiles(
      @PathVariable String name, @RequestBody Map<String, String> body) {
    Map<String, String> files = lifecycle.generateFiles(name, body.getOrDefault("description", ""));
    return ApiResponse.ok(files);
  }

  /** 保存一组文件并生效:先校验 AGENT.md 可解析(非法 400 不写坏目录)→ 写入 → 重注册,返回新视图。 */
  @PostMapping("/{name}/files")
  public ApiResponse<AgentView> saveFiles(
      @PathVariable String name, @RequestBody Map<String, Map<String, String>> body) {
    Map<String, String> files = body.getOrDefault("files", Map.of());
    Profile profile = lifecycle.saveFiles(name, files);
    return ApiResponse.ok(AgentView.from(profile));
  }

  /** 这个 Agent 的专属记忆(30 节 5.2.1,替代原全局 GET /api/v1/memory);不存在 → 404。 */
  @GetMapping("/{name}/memory")
  public ApiResponse<Map<String, String>> getMemory(@PathVariable String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    return ApiResponse.ok(Map.of("memory", memoryService.readAll(name)));
  }

  /** 这个 Agent 的固定管理台会话(channel=admin, user=console, getOrCreate 幂等);不存在 → 404。 */
  @GetMapping("/{name}/session")
  public ApiResponse<SessionView> getSession(@PathVariable String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    Session session = sessionManager.getOrCreate("admin", "console", name);
    return ApiResponse.ok(SessionView.from(session));
  }

  /** 往固定会话发消息、触发 ReAct(同 invoke 入口,但落在固定会话里累积上下文);不存在 → 404。 */
  @PostMapping("/{name}/session/messages")
  public ApiResponse<Map<String, String>> sendSessionMessage(
      @PathVariable String name, @RequestBody Map<String, String> body) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    Session session = sessionManager.getOrCreate("admin", "console", name);
    return ApiResponse.ok(
        Map.of("reply", processWithTimeout(session, body.getOrDefault("content", ""))));
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
