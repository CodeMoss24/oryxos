package com.oryxos.provider;

import com.oryxos.core.exception.ProviderNotFoundException;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.react.LlmResponse;
import com.oryxos.core.react.Prompt;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.react.ToolCall;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.function.FunctionCallingOptions;

/**
 * ProviderService 模块。统一管理所有 LLM Provider,对 ReAct 循环屏蔽不同 LLM 厂商的差异。
 *
 * <p>关键设计点(TechnicalSolution 3.2):维护一份显式的 provider name → ChatModel 的映射, 不靠类型扫描自动来。
 *
 * <p>核心阶段不用 Spring AI 的自动 tool 执行,constitution 原则四: {@code
 * FunctionCallingOptions.withProxyToolCalls(false)} + FunctionCallback.call() 返回空字符串, 实际 Tool 执行由
 * ToolExecutor 控制。
 */
public class ProviderService implements ProviderPort {

  private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

  private final Map<String, ChatModel> providerMap;
  private final ToolSchemaAdapter toolSchemaAdapter;
  private final LlmCallRepository llmCallRepository;

  public ProviderService(
      Map<String, ChatModel> providerMap,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    this.providerMap = Map.copyOf(providerMap);
    this.toolSchemaAdapter = toolSchemaAdapter;
    this.llmCallRepository = llmCallRepository;
  }

  @Override
  public LlmResponse chat(String sessionId, Profile profile, Prompt prompt) {
    String providerName = profile.getProvider().name();
    ChatModel chatModel = providerMap.get(providerName);
    if (chatModel == null) {
      throw new ProviderNotFoundException(providerName);
    }

    long startedAt = System.currentTimeMillis();
    try {
      List<org.springframework.ai.chat.messages.Message> messages = toSpringAiMessages(prompt);

      var options =
          FunctionCallingOptions.builder()
              .withModel(profile.getProvider().model())
              .withFunctionCallbacks(toolSchemaAdapter.toSpringAiTools(prompt.availableTools()))
              .withProxyToolCalls(false) // 关掉 Spring AI 自动执行
              .build();

      org.springframework.ai.chat.prompt.Prompt springPrompt =
          new org.springframework.ai.chat.prompt.Prompt(messages, options);

      ChatResponse response = chatModel.call(springPrompt);
      long durationMs = System.currentTimeMillis() - startedAt;

      com.oryxos.core.react.Usage usage = extractUsage(response);
      var toolCalls = extractToolCalls(response);

      audit(sessionId, providerName, profile.getProvider().model(), usage, true, null, durationMs);

      return new LlmResponse(extractContent(response), toolCalls, usage);
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

  private List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(Prompt prompt) {
    return prompt.messages().stream()
        .map(
            m ->
                switch (m.role()) {
                  case "user" -> new org.springframework.ai.chat.messages.UserMessage(m.content());
                  case "assistant" -> new AssistantMessage(m.content());
                  default -> new org.springframework.ai.chat.messages.SystemMessage(m.content());
                })
        .map(msg -> (org.springframework.ai.chat.messages.Message) msg)
        .toList();
  }

  private String extractContent(ChatResponse response) {
    if (response.getResult() != null && response.getResult().getOutput() != null) {
      return response.getResult().getOutput().getContent();
    }
    return "";
  }

  private List<ToolCall> extractToolCalls(ChatResponse response) {
    List<ToolCall> calls = new ArrayList<>();
    if (response.getResult() != null
        && response.getResult().getOutput() != null
        && response.getResult().getOutput().getToolCalls() != null) {
      for (var tc : response.getResult().getOutput().getToolCalls()) {
        calls.add(new ToolCall(tc.name(), tc.arguments()));
      }
    }
    return calls;
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
