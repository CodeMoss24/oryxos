package com.oryxos.core.context;

import com.oryxos.core.profile.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 Profile 的 bootstrap 字段从 .oryxos/ 读取 AGENTS.md、SOUL.md、USER.md(Bootstrap,全量注入);
 * 同时把这个 Agent 自己 AGENT.md 的正文(任务指令)一并交给 PromptBuilder。
 * 每次组装 prompt 时重新加载不缓存,用户修改后立即生效。
 */
@Component
public class ContextLoader {

    private final Path workspace;

    public ContextLoader() {
        this(Path.of(".oryxos"));
    }

    public ContextLoader(Path workspace) {
        this.workspace = workspace;
    }

    /**
     * 加载 system prompt:AGENTS.md + SOUL.md + USER.md(Bootstrap)+ AGENT.md 正文。
     */
    public String loadSystemPrompt(Profile profile, String agentMdBody) {
        List<String> parts = new ArrayList<>();
        for (String bootstrapFile : profile.getBootstrap()) {
            String content = readIfExists(workspace.resolve(bootstrapFile));
            if (content != null) {
                parts.add("# " + bootstrapFile + "\n\n" + content);
            }
        }
        if (agentMdBody != null && !agentMdBody.isBlank()) {
            parts.add("# AGENT.md\n\n" + agentMdBody);
        }
        parts.add("当前时间: " + java.time.LocalDateTime.now());
        return String.join("\n\n---\n\n", parts);
    }

    private String readIfExists(Path path) {
        try {
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
