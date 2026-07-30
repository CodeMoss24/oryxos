package com.oryxos.provider;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.react.LlmResponse;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.react.ToolCall;
import com.oryxos.core.session.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProviderService 模块。统一管理所有 LLM Provider,对 ReAct 循环屏蔽不同 LLM 厂商的差异。
 *
 * <p>关键设计点(TechnicalSolution 3.2):维护一份显式的 provider name → ChatModel 的映射,
 * 不靠类型扫描自动来。Spring 容器里会有多个 ChatModel Bean,仅靠类型扫描无法可靠区分哪个是 deepseek、
 * 哪个是 kimi(Bean 类型相同、Bean name 未必等于 provider name)。
 *
 * <p>当前是核心阶段骨架实现:启动时由各 Provider 配置注册进 nameToBeanName 映射,
 * 实际的 ChatModel 调用在 LLM 接入后补全。这里先跑通框架,不引入对具体 Provider SDK 的强依赖。
 */
@Service
public class ProviderService implements ProviderPort {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    /** provider name → Spring Bean name(用于显式查找 ChatModel) */
    private final Map<String, String> nameToBeanName = new ConcurrentHashMap<>();

    public void registerProvider(String providerName, String beanName) {
        nameToBeanName.put(providerName, beanName);
        log.info("Registered LLM provider: {} → {}", providerName, beanName);
    }

    public List<String> listProviders() {
        return new ArrayList<>(nameToBeanName.keySet());
    }

    @Override
    public LlmResponse call(Profile profile, String systemPrompt, String memoryBlock,
                            String toolListBlock, List<Message> history) {
        if (profile.getProvider() == null) {
            throw new IllegalStateException("Profile " + profile.getName() + " has no provider configured");
        }
        String providerName = profile.getProvider().name();
        if (!nameToBeanName.containsKey(providerName)) {
            throw new IllegalStateException("Provider not registered: " + providerName);
        }

        // TODO: 核心阶段先跑通框架。LLM 接入后这里调 ChatModel.call(prompt),
        //   并把 tool_calls 解析成 List<ToolCall>,把 token 使用量写入 llm_calls 表。
        log.info("[ProviderService] call provider={} model={} (stub)", providerName, profile.getProvider().model());
        return LlmResponse.text("[stub response from " + providerName + "]");
    }
}
