package com.oryxos.core.react;

import com.oryxos.core.profile.Profile;

/**
 * LLM 调用端口(端口与适配器模式)。 实现在 oryxos-provider 模块,基于 Spring AI Alibaba 的 ChatModel。
 *
 * <p>核心阶段不用 Spring AI 的自动 tool 执行,constitution 原则四: 只用 Provider 抽象 + 协议转换 + @Tool schema 生成。
 */
public interface ProviderPort {

  /**
   * 发起一次 LLM 调用。sessionId 用于审计(写入 llm_calls 表)。
   *
   * @param sessionId 会话标识,审计追踪
   * @param profile 运行时配置(选择 provider / model / temperature)
   * @param prompt 本次调用的消息和可用 Tool 列表
   * @return LLM 响应(文本 + Tool 调用请求 + token 用量)
   */
  LlmResponse chat(String sessionId, Profile profile, Prompt prompt);
}
