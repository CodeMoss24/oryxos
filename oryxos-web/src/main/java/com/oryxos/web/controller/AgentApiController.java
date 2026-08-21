package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.agent.AgentExecutionService;
import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.agent.AgentStore;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.skill.AgentSkillBindingService;
import com.oryxos.core.skill.BoundSkillDescriptor;
import com.oryxos.core.skill.SkillCatalog;
import com.oryxos.core.skill.SkillCatalogEntry;
import com.oryxos.web.controller.dto.AgentSkillBindingsView;
import com.oryxos.web.controller.dto.ReplaceSkillBindingsRequest;
import com.oryxos.web.dto.AgentChatView;
import com.oryxos.web.dto.AgentView;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.ChatMessageView;
import com.oryxos.web.dto.CreateAgentRequest;
import com.oryxos.web.dto.ExecutionView;
import com.oryxos.web.dto.TriggerResponse;
import com.oryxos.web.dto.UpdateAgentBasicRequest;
import com.oryxos.web.dto.UpdateAgentRequest;
import com.oryxos.web.exception.AgentTimeoutException;
import com.oryxos.web.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
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

  /** 会话聚合视图消息上限(与单会话历史端点的 100 条口径一致) */
  private static final int MAX_CHAT_MESSAGES = 100;

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ProfileRegistry profileRegistry;
  private final AsyncTaskExecutor taskExecutor;
  private final AgentLifecycleService lifecycle;
  private final MemoryService memoryService;
  private final AgentExecutionService executionService;
  private final AgentSkillBindingService skillBindings;
  private final SkillCatalog skillCatalog;

  public AgentApiController(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AsyncTaskExecutor taskExecutor,
      AgentLifecycleService lifecycle,
      MemoryService memoryService,
      AgentExecutionService executionService) {
    this(
        agentService,
        sessionManager,
        profileRegistry,
        taskExecutor,
        lifecycle,
        memoryService,
        executionService,
        null,
        null);
  }

  public AgentApiController(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AsyncTaskExecutor taskExecutor,
      AgentLifecycleService lifecycle,
      MemoryService memoryService,
      AgentExecutionService executionService,
      AgentSkillBindingService skillBindings) {
    this(
        agentService,
        sessionManager,
        profileRegistry,
        taskExecutor,
        lifecycle,
        memoryService,
        executionService,
        skillBindings,
        null);
  }

  @Autowired
  public AgentApiController(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AsyncTaskExecutor taskExecutor,
      AgentLifecycleService lifecycle,
      MemoryService memoryService,
      AgentExecutionService executionService,
      AgentSkillBindingService skillBindings,
      SkillCatalog skillCatalog) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
    this.taskExecutor = taskExecutor;
    this.lifecycle = lifecycle;
    this.memoryService = memoryService;
    this.executionService = executionService;
    this.skillBindings = skillBindings;
    this.skillCatalog = skillCatalog;
  }

  /** 创建:脚手架完整 Agent 目录 + 派生注册,失败回滚(不留半个 Agent);name 冲突/非法 → 400。 */
  @PostMapping
  public ApiResponse<AgentView> create(@RequestBody CreateAgentRequest request) {
    CreateAgentRequest.ScheduleDraft draft = request.schedule();
    AgentStore.ScheduleDraft schedule =
        draft == null
            ? null
            : new AgentStore.ScheduleDraft(draft.cron(), draft.zone(), draft.message());
    Profile profile = lifecycle.create(request.name(), request.description(), schedule);
    bindRequestedSkills(request.name(), request.skillBindings());
    return ApiResponse.ok(view(profile));
  }

  /** 创建后补建前端勾选的 Skill 绑定(单个失败抛 400 前已创建的绑定不回收,列表可重新绑定;幂等跳过已存在的)。 */
  private void bindRequestedSkills(String agent, List<String> skills) {
    if (skills == null || skills.isEmpty()) {
      return;
    }
    for (String skill : skills) {
      if (skill == null || skill.isBlank()) {
        continue;
      }
      skillBindings.bind(agent, skill.trim());
    }
  }

  /** 列表。 */
  @GetMapping
  public ApiResponse<List<AgentView>> list() {
    return ApiResponse.ok(lifecycle.list().stream().map(this::view).toList());
  }

  /** 单个详情;不存在 → 404。 */
  @GetMapping("/{name}")
  public ApiResponse<AgentView> get(@PathVariable String name) {
    AgentView view =
        lifecycle
            .find(name)
            .map(this::view)
            .orElseThrow(() -> new ResourceNotFoundException("agent not found: " + name));
    return ApiResponse.ok(view);
  }

  /** 覆写 AGENT.md;schedules 变更先注销旧句柄再注册新的;内容非法 → 400 不写坏目录。 */
  @PutMapping("/{name}")
  public ApiResponse<AgentView> update(
      @PathVariable String name, @RequestBody UpdateAgentRequest request) {
    Profile profile = lifecycle.update(name, request.content());
    return ApiResponse.ok(view(profile));
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

  /**
   * 保存一组文件并生效:先校验 AGENT.md 可解析(非法 400 不写坏目录)→ 写入 → 重注册,返回新视图; skillBindings(可选)为前端勾选的 Skill
   * 绑定,保存后逐个建立固定软连接。
   */
  @SuppressWarnings("unchecked")
  @PostMapping("/{name}/files")
  public ApiResponse<AgentView> saveFiles(
      @PathVariable String name, @RequestBody Map<String, Object> body) {
    Object filesRaw = body.getOrDefault("files", Map.of());
    Map<String, String> files = filesRaw instanceof Map ? (Map<String, String>) filesRaw : Map.of();
    Profile profile = lifecycle.saveFiles(name, files);
    Object bindingsRaw = body.get("skillBindings");
    if (bindingsRaw instanceof List<?> list) {
      bindRequestedSkills(
          name, (List<String>) list.stream().filter(String.class::isInstance).toList());
    }
    return ApiResponse.ok(view(profile));
  }

  /** 这个 Agent 的专属记忆(30 节 5.2.1,替代原全局 GET /api/v1/memory);不存在 → 404。 */
  @GetMapping("/{name}/memory")
  public ApiResponse<Map<String, String>> getMemory(@PathVariable String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    return ApiResponse.ok(Map.of("memory", memoryService.readAll(name)));
  }

  /**
   * 这个 Agent 的会话聚合视图:固定管理台会话(admin:console, getOrCreate 幂等)优先, 再按 lastActiveAt 倒序并入该 Agent
   * 最近其他会话(invoke 手动 / scheduler 定时)的消息——整块拼接不切碎一轮, 总消息 ≤100 条。不存在 → 404。
   */
  @GetMapping("/{name}/session")
  public ApiResponse<AgentChatView> getSession(@PathVariable String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    Session fixed = sessionManager.getOrCreate("admin", "console", name);
    List<ChatMessageView> merged = new ArrayList<>();
    List<String> sources = new ArrayList<>();
    appendSession(merged, sources, fixed, fixed.getSessionId(), MAX_CHAT_MESSAGES);
    // 固定会话之外的同 Agent 会话:按活跃倒序(listAll 已排序), 逐个整块并入直到上限
    for (Session s : sessionManager.listRecent(100)) {
      if (merged.size() >= MAX_CHAT_MESSAGES) break;
      if (!name.equals(s.getProfileName()) || fixed.getSessionId().equals(s.getSessionId()))
        continue;
      appendSession(merged, sources, s, s.getSessionId(), MAX_CHAT_MESSAGES - merged.size());
    }
    return ApiResponse.ok(
        new AgentChatView(fixed.getSessionId(), fixed.getProfileName(), merged, sources));
  }

  /** 把某个会话的消息整块追加进聚合列表(不切碎 tool 往返);sessions 记录非空的消息来源。 */
  private static void appendSession(
      List<ChatMessageView> out, List<String> sources, Session s, String source, int limit) {
    if (limit <= 0 || s.getMessages().isEmpty()) {
      return;
    }
    sources.add(source);
    for (Message m : s.getMessages()) {
      if (out.size() >= limit) break;
      out.add(ChatMessageView.from(m, source));
    }
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

  /**
   * 修改基本信息(只改 AGENT.md frontmatter 的 description / provider.name / provider.model,正文与其余配置保留): 校验非法
   * → 400, 写入即生效。不存在 → 404。
   */
  @PutMapping("/{name}/basic")
  public ApiResponse<AgentView> updateBasic(
      @PathVariable String name, @RequestBody UpdateAgentBasicRequest request) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    Profile profile =
        lifecycle.updateBasicInfo(name, request.description(), request.provider(), request.model());
    return ApiResponse.ok(view(profile));
  }

  /**
   * 异步触发一次执行(管理台"立即执行"):先落 RUNNING 记录立即返回 executionId,真正的 ReAct 在虚拟线程后台跑(不占 HTTP 线程、不超时), 结束回填状态。
   * 内容可空,缺省"请按你的职责执行一次任务。"。不存在 → 404。
   */
  @PostMapping("/{name}/trigger")
  public ApiResponse<TriggerResponse> trigger(
      @PathVariable String name, @RequestBody(required = false) Map<String, String> body) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    String message = body == null ? null : body.get("content");
    String content = message == null || message.isBlank() ? "请按你的职责执行一次任务。" : message;
    Session session = sessionManager.getOrCreate("invoke", "default", name);
    long executionId =
        executionService.triggerAsync(
            name, "manual", session.getSessionId(), () -> agentService.process(session, content));
    return ApiResponse.ok(TriggerResponse.running(executionId));
  }

  /** 执行历史(agent_executions 表,手动+定时都记;按开始时间倒序最多 50 条)。不存在 → 404。 */
  @GetMapping("/{name}/executions")
  public ApiResponse<List<ExecutionView>> executions(@PathVariable String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("agent not found: " + name);
    }
    return ApiResponse.ok(
        executionService.history(name, 50).stream().map(ExecutionView::from).toList());
  }

  /** 该 Agent 当前绑定的 Skill 检查结果:绑定 + 问题(软连接不存在/越界等)。 */
  @GetMapping("/{name}/skills")
  public ApiResponse<AgentSkillBindingsView> skills(@PathVariable String name) {
    requireAgent(name);
    return ApiResponse.ok(AgentSkillBindingsView.from(requireBindings().inspect(name)));
  }

  /** 绑定一个已安装 Skill(建受控软连接);Skill 不存在 → 404,catalog 不可达 → 400。 */
  @PutMapping("/{name}/skills/{skill}")
  public ApiResponse<AgentSkillBindingsView> bind(
      @PathVariable String name, @PathVariable String skill) {
    requireAgent(name);
    requireSkillsExist(List.of(skill));
    validateCatalog(List.of(skill));
    requireBindings().bind(name, skill);
    return skills(name);
  }

  /** 解绑一个 Skill(删软连接,幂等——没绑也没关系)。 */
  @DeleteMapping("/{name}/skills/{skill}")
  public ApiResponse<AgentSkillBindingsView> unbind(
      @PathVariable String name, @PathVariable String skill) {
    requireAgent(name);
    requireBindings().unbind(name, skill);
    return skills(name);
  }

  /** 整组替换绑定(原子:先校验全部存在且 catalog 可达,失败不动)。 */
  @PutMapping("/{name}/skills")
  public ApiResponse<AgentSkillBindingsView> replaceSkills(
      @PathVariable String name, @RequestBody ReplaceSkillBindingsRequest request) {
    requireAgent(name);
    List<String> desired = request == null ? List.of() : request.skills();
    requireSkillsExist(desired);
    validateCatalog(desired);
    return ApiResponse.ok(
        AgentSkillBindingsView.from(requireBindings().replaceBindings(name, desired)));
  }

  /** Profile → 对外视图;skills 取绑定检查实况(未装配绑定服务时为空列表)。 */
  private AgentView view(Profile profile) {
    List<String> skills =
        skillBindings == null
            ? List.of()
            : skillBindings.inspect(profile.getName()).bindings().stream()
                .map(BoundSkillDescriptor::name)
                .toList();
    return AgentView.from(profile, skills);
  }

  private void requireAgent(String name) {
    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("Agent 不存在: " + name);
    }
  }

  private AgentSkillBindingService requireBindings() {
    if (skillBindings == null) {
      throw new IllegalStateException("Agent Skill 绑定服务未装配");
    }
    return skillBindings;
  }

  private void requireSkillsExist(List<String> names) {
    if (names == null) {
      return;
    }
    AgentSkillBindingService bindings = requireBindings();
    for (String name : names) {
      if (!bindings.skillExists(name)) {
        throw new ResourceNotFoundException("Skill 不存在: " + name);
      }
    }
  }

  private void validateCatalog(List<String> names) {
    if (names == null || names.isEmpty()) {
      return;
    }
    if (skillCatalog == null) {
      throw new IllegalStateException("Skill catalog 不可用");
    }
    Map<String, SkillCatalogEntry> candidates = new LinkedHashMap<>();
    for (SkillCatalogEntry entry : skillCatalog.query("", null)) {
      if (candidates.putIfAbsent(entry.name(), entry) != null) {
        throw new IllegalArgumentException("Skill catalog 存在同名公共/私有冲突: " + entry.name());
      }
    }
    for (String name : names) {
      SkillCatalogEntry entry = candidates.get(name);
      if (entry == null || !entry.installed()) {
        throw new IllegalArgumentException("Skill 不在可访问且已安装的 catalog 中: " + name);
      }
    }
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
