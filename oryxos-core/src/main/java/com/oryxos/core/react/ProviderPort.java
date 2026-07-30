package com.oryxos.core.react;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;

import java.util.List;

/**
 * LLM 调用端口(端口与适配器模式)。
 * 实现在 oryxos-provider 模块,基于 Spring AI Alibaba 的 ChatClient。
 *
 * <p>核心阶段不用 Spring AI 的自动 tool 执行,constitution 原则四:
 * 只用 Provider 抽象 + 协议转换 + @Tool schema 生成。
 */
public interface ProviderPort {

    LlmResponse call(Profile profile, String systemPrompt, String memoryBlock,
                     String toolListBlock, List<Message> history);
}
