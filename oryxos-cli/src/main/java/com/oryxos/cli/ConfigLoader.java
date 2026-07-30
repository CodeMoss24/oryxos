package com.oryxos.cli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 统一加载 LLM API key、Provider 凭证、MCP server 凭证等敏感配置。
 * 核心阶段做基础版:敏感配置通过环境变量注入或独立的本地配置文件加载,
 * Profile 里用 ${ENV_VAR} 占位,加载时从环境变量解析。
 */
@Component
public class ConfigLoader {

    @Value("${oryxos.workspace:.oryxos}")
    private String workspace;

    /**
     * 从环境变量解析 ${ENV_VAR} 占位。
     */
    public String resolve(String raw) {
        if (raw == null) return null;
        if (!raw.contains("${")) return raw;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (raw.startsWith("${", i)) {
                int end = raw.indexOf('}', i);
                if (end < 0) {
                    sb.append(raw.substring(i));
                    break;
                }
                String key = raw.substring(i + 2, end);
                sb.append(System.getenv(key));
                i = end + 1;
            } else {
                sb.append(raw.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    public Map<String, String> loadProviderEnv() {
        return System.getenv();
    }

    public String getWorkspace() { return workspace; }
}
