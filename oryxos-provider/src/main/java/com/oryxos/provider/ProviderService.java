package com.oryxos.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.exception.ProviderNotFoundException;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.react.LlmResponse;
import com.oryxos.core.react.Prompt;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.react.ToolCall;
import com.oryxos.core.session.Message;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ProviderService 模块。统一管理所有 LLM Provider,对 ReAct 循环屏蔽不同 LLM 厂商的差异。
 *
 * <p>关键设计点(TechnicalSolution 3.2):维护一份显式的 provider name → ChatModel 的映射, 不靠类型扫描自动来。
 *
 * <p>核心阶段不用 Spring AI 的自动 tool 执行,constitution 原则四: {@code
 * FunctionCallingOptions.withProxyToolCalls(false)} + FunctionCallback.call() 返回空字符串, 实际 Tool 执行由
 * ToolExecutor 控制。
 *
 * <p>工具调用路径:当有可用工具时,绕过 Spring AI FunctionCallingOptions 直接构造带 tools[] 的 API 请求发送给 LLM Provider。
 * Spring AI 1.0.0-M4 的 proxyToolCalls=false 会连 tools 都不发给 LLM,导致模型收不到工具定义无法发起 Function Calling。
 */
public class ProviderService implements ProviderPort {

  private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final Map<String, ChatModel> providerMap;
  private final Map<String, ProviderProperties.ProviderEntry> providerConfigs;
  private final RestClient.Builder restClientBuilder;
  private final ToolSchemaAdapter toolSchemaAdapter;
  private final LlmCallRepository llmCallRepository;

  public ProviderService(
      Map<String, ChatModel> providerMap,
      Map<String, ProviderProperties.ProviderEntry> providerConfigs,
      RestClient.Builder restClientBuilder,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    this.providerMap = Map.copyOf(providerMap);
    this.providerConfigs = Map.copyOf(providerConfigs);
    this.restClientBuilder = restClientBuilder;
    this.toolSchemaAdapter = toolSchemaAdapter;
    this.llmCallRepository = llmCallRepository;
  }

  @Override
  public Map<String, Boolean> connectivity() {
    Map<String, Boolean> result = new java.util.LinkedHashMap<>();
    providerConfigs.forEach((name, entry) -> result.put(name, probeReachable(entry.baseUrl())));
    return result;
  }

  /** 对 base-url 做带超时轻量探测:任何 HTTP 响应(含 4xx/5xx)视为"地址可达",连接失败/超时视为断开。 */
  private boolean probeReachable(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return false;
    }
    // TODO(Sandbox接线):涉外 HTTP IO 首行应过 Sandbox.enforce(HTTP_REQUEST, baseUrl)。
    // 与 Mem0MemoryStore 同口径留接线位:oryxos-provider 不依赖 oryxos-tool,接线随 Sandbox 覆盖面扩展一起做。

    // clone 而非直接改注入的 builder——探测的短超时不能污染 chat 路径共享的 builder
    RestClient probeClient =
        restClientBuilder.clone().requestFactory(probeRequestFactory()).baseUrl(baseUrl).build();
    try {
      probeClient.get().retrieve().toBodilessEntity();
      return true;
    } catch (RestClientResponseException e) {
      return true; // 4xx/5xx 也是"地址可达"
    } catch (RuntimeException e) {
      log.debug("Provider connectivity probe failed for {}", baseUrl, e);
      return false;
    }
  }

  private ClientHttpRequestFactory probeRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(2000);
    return factory;
  }

  @Override
  public LlmResponse chat(String sessionId, Profile profile, Prompt prompt) {
    String providerName = profile.getProvider().name();
    long startedAt = System.currentTimeMillis();
    try {
      ChatModel chatModel = providerMap.get(providerName);
      // 放在 try 内:provider 未配置也算一次失败的 LLM 调用,落审计(第 27 节对账 H4 ②)
      if (chatModel == null) {
        throw new ProviderNotFoundException(providerName);
      }
      LlmResponse result;
      if (chatModel instanceof MockChatModel) {
        // 第 27 节:mock 走 ChatModel 路径(工具脚本在 MockChatModel 内部驱动,ReAct 循环不感知)
        result = chatWithoutTools(sessionId, profile, prompt, chatModel, startedAt);
      } else if (prompt.availableTools() != null && !prompt.availableTools().isEmpty()) {
        result = chatWithTools(sessionId, profile, prompt, startedAt);
      } else {
        result = chatWithoutTools(sessionId, profile, prompt, chatModel, startedAt);
      }

      long durationMs = System.currentTimeMillis() - startedAt;
      audit(
          sessionId,
          providerName,
          profile.getProvider().model(),
          result.usage(),
          true,
          null,
          durationMs);
      return result;
    } catch (RuntimeException e) {
      long durationMs = System.currentTimeMillis() - startedAt;
      audit(
          sessionId,
          providerName,
          profile.getProvider().model(),
          null,
          false,
          e.getMessage(),
          durationMs);
      throw e;
    }
  }

  /** 有工具:绕过 Spring AI,直接构造带 tools[] 的 API 请求 */
  private LlmResponse chatWithTools(
      String sessionId, Profile profile, Prompt prompt, long startedAt) {
    var entry = providerConfigs.get(profile.getProvider().name());
    String apiKey = entry != null ? entry.apiKey() : "";
    String baseUrl = entry != null ? entry.baseUrl() : "";
    String model = profile.getProvider().model();

    RestClient restClient = restClientBuilder.build();

    List<Map<String, Object>> tools = toolSchemaAdapter.toOpenAiTools(prompt.availableTools());

    Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", promptToMapList(prompt));
    requestBody.put("tools", tools);
    requestBody.put("tool_choice", "auto");
    requestBody.put("stream", false);

    try {
      String requestJson = objectMapper.writeValueAsString(requestBody);
      log.debug("LLM request with {} tools, body length: {}", tools.size(), requestJson.length());

      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          restClient
              .post()
              .uri(baseUrl + "/v1/chat/completions")
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .body(requestJson)
              .retrieve()
              .body(Map.class);

      return parseOpenAiResponse(response);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize LLM request", e);
    }
  }

  /**
   * 无工具:走 Spring AI ChatModel 路径(保持原有逻辑)。MockChatModel 也走此路径——它第一轮会在 ChatResponse 的 generations 里带出
   * save_memory 工具调用,因此这里统一做工具调用提取。
   */
  private LlmResponse chatWithoutTools(
      String sessionId, Profile profile, Prompt prompt, ChatModel chatModel, long startedAt) {
    List<org.springframework.ai.chat.messages.Message> messages = toSpringAiMessages(prompt);

    // 模型名必须进请求 options:19 节重构把 16 节原版的 withModel 丢了,无工具路径会发 model 缺失的请求,
    // 真 provider 直接 400 "missing field model"(ProviderSmokeIT 显式跑才暴露)。
    org.springframework.ai.chat.prompt.Prompt springPrompt =
        new org.springframework.ai.chat.prompt.Prompt(
            messages,
            org.springframework.ai.openai.OpenAiChatOptions.builder()
                .withModel(profile.getProvider().model())
                .build());

    ChatResponse response = chatModel.call(springPrompt);

    var usage = extractUsage(response);
    return new LlmResponse(extractContent(response), extractToolCalls(response), usage);
  }

  /** 从 ChatResponse 的 generations 里提取 tool calls(真实 provider 无工具路径不会返回,只有 mock 会有)。 */
  private List<ToolCall> extractToolCalls(ChatResponse response) {
    List<ToolCall> toolCalls = new ArrayList<>();
    if (response.getResults() != null) {
      for (Generation g : response.getResults()) {
        AssistantMessage output = g.getOutput();
        if (output.getToolCalls() != null) {
          for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
            toolCalls.add(new ToolCall(tc.id(), tc.name(), tc.arguments()));
          }
        }
      }
    }
    return toolCalls;
  }

  @SuppressWarnings("unchecked")
  private LlmResponse parseOpenAiResponse(Map<String, Object> response) {
    List<Map<String, Object>> choices =
        (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
    if (choices.isEmpty()) {
      return new LlmResponse("", List.of(), com.oryxos.core.react.Usage.EMPTY);
    }

    Map<String, Object> choice = choices.get(0);
    Map<String, Object> message = (Map<String, Object>) choice.get("message");

    String content = (String) message.getOrDefault("content", "");
    if (content == null) content = "";

    // 提取 tool_calls
    List<ToolCall> toolCalls = new ArrayList<>();
    List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
    if (rawToolCalls != null) {
      for (Map<String, Object> tc : rawToolCalls) {
        String callId = (String) tc.get("id");
        Map<String, Object> function = (Map<String, Object>) tc.get("function");
        if (function != null) {
          String name = (String) function.get("name");
          String arguments = (String) function.get("arguments");
          toolCalls.add(new ToolCall(callId, name, arguments != null ? arguments : "{}"));
        }
      }
    }

    // 提取 usage
    Map<String, Object> usageMap = (Map<String, Object>) response.get("usage");
    com.oryxos.core.react.Usage usage = com.oryxos.core.react.Usage.EMPTY;
    if (usageMap != null) {
      int promptTokens = usageMap.get("prompt_tokens") instanceof Number n ? n.intValue() : 0;
      int completionTokens =
          usageMap.get("completion_tokens") instanceof Number n ? n.intValue() : 0;
      int totalTokens = usageMap.get("total_tokens") instanceof Number n ? n.intValue() : 0;
      usage = new com.oryxos.core.react.Usage(promptTokens, completionTokens, totalTokens);
    }

    return new LlmResponse(content, toolCalls, usage);
  }

  private List<Map<String, Object>> promptToMapList(Prompt prompt) {
    return prompt.messages().stream()
        .map(
            m -> {
              Map<String, Object> msg = new java.util.LinkedHashMap<>();
              msg.put("role", m.role());
              if (m.content() != null) {
                msg.put("content", m.content());
              }
              // assistant 消息携带 tool_calls
              if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                  Map<String, Object> tcm = new java.util.LinkedHashMap<>();
                  tcm.put("id", tc.id() != null ? tc.id() : "call_" + java.util.UUID.randomUUID());
                  tcm.put("type", "function");
                  Map<String, Object> func = new java.util.LinkedHashMap<>();
                  func.put("name", tc.name());
                  func.put("arguments", tc.argumentsJson());
                  tcm.put("function", func);
                  tcs.add(tcm);
                }
                msg.put("tool_calls", tcs);
              }
              // tool 消息携带 tool_call_id
              if ("tool".equals(m.role()) && m.toolCallId() != null) {
                msg.put("tool_call_id", m.toolCallId());
              }
              return msg;
            })
        .toList();
  }

  private List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(Prompt prompt) {
    return prompt.messages().stream().map(ProviderService::toSpringAiMessage).toList();
  }

  /**
   * 会话恢复保真转换:assistant 必须带 toolCalls、tool 必须带 toolCallId——丢任何一项,含工具历史的会话 下一条消息就会被 Provider
   * 拒单("Messages with role 'tool' must be a response to a preceding message with
   * 'tool_calls'",审计表实证)。
   */
  private static org.springframework.ai.chat.messages.Message toSpringAiMessage(Message m) {
    if ("user".equals(m.role())) {
      return new org.springframework.ai.chat.messages.UserMessage(m.content());
    }
    if ("assistant".equals(m.role())) {
      List<AssistantMessage.ToolCall> toolCalls =
          m.toolCalls() == null
              ? List.of()
              : m.toolCalls().stream()
                  .map(
                      tc ->
                          new AssistantMessage.ToolCall(
                              tc.id(), "function", tc.name(), tc.argumentsJson()))
                  .toList();
      return new AssistantMessage(m.content(), Map.of(), toolCalls);
    }
    if ("tool".equals(m.role())) {
      return new ToolResponseMessage(
          List.of(new ToolResponseMessage.ToolResponse(m.toolCallId(), null, m.content())));
    }
    return new org.springframework.ai.chat.messages.SystemMessage(m.content());
  }

  private String extractContent(ChatResponse response) {
    if (response.getResult() != null && response.getResult().getOutput() != null) {
      return response.getResult().getOutput().getContent();
    }
    return "";
  }

  private com.oryxos.core.react.Usage extractUsage(ChatResponse response) {
    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      var u = response.getMetadata().getUsage();
      return new com.oryxos.core.react.Usage(
          u.getPromptTokens() != null ? u.getPromptTokens().intValue() : 0,
          u.getGenerationTokens() != null ? u.getGenerationTokens().intValue() : 0,
          u.getTotalTokens() != null ? u.getTotalTokens().intValue() : 0);
    }
    return com.oryxos.core.react.Usage.EMPTY;
  }

  private void audit(
      String sessionId,
      String provider,
      String model,
      com.oryxos.core.react.Usage usage,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      LlmCallEntity entity = new LlmCallEntity();
      entity.setSessionId(sessionId);
      entity.setProvider(provider);
      entity.setModel(model);
      entity.setDurationMs(durationMs);
      entity.setSuccess(success);
      entity.setErrorMessage(errorMessage);
      entity.setCreatedAt(Instant.now());
      if (usage != null) {
        entity.setPromptTokens(usage.promptTokens());
        entity.setCompletionTokens(usage.completionTokens());
        entity.setTotalTokens(usage.totalTokens());
      }
      llmCallRepository.save(entity);
    } catch (Exception e) {
      log.error("Failed to write audit record for session {} provider {}", sessionId, provider, e);
    }
  }

  /** Exposed for testing. */
  Map<String, ChatModel> providerMap() {
    return providerMap;
  }
}
