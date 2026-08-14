package com.oryxos.core.context;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.runtime.OryxOsRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按 Profile 的 bootstrap 字段从 .oryxos/ 读取 AGENTS.md、SOUL.md、USER.md(Bootstrap,全量注入); 同时把这个 Agent 自己
 * AGENT.md 的正文(任务指令)一并交给 PromptBuilder。 每次组装 prompt 时重新加载不缓存,用户修改后立即生效。
 */
@Component
public class ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

  private final Path workspace;

  public ContextLoader() {
    this(OryxOsRuntime.workspaceRoot());
  }

  public ContextLoader(Path workspace) {
    this.workspace = workspace;
  }

  /** 加载 system prompt:AGENTS.md + SOUL.md + USER.md(Bootstrap)+ AGENT.md 正文。 */
  public String loadSystemPrompt(Profile profile, String agentMdBody) {
    List<String> parts = new ArrayList<>();
    for (String bootstrapFile : profile.getBootstrap()) {
      Path path = workspace.resolve(bootstrapFile);
      if (!Files.exists(path)) {
        log.warn("Bootstrap file not found: {}", path);
        continue;
      }
      String content = readIfExists(path);
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
      log.warn("Failed to read bootstrap file: {}", path, e);
      return null;
    }
    return null;
  }
}
