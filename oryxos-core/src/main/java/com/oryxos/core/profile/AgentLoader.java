package com.oryxos.core.profile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 扫 .oryxos/agents/ 各子目录,deriveProfile 把每个 AGENT.md 的 frontmatter 派生成一个 Profile, 注册到
 * ProfileRegistry。启动时做合法性校验。
 *
 * <p>AGENT.md 由两部分组成:
 *
 * <ul>
 *   <li>frontmatter(YAML,--- 包围):这个 Agent 自己的 profile
 *   <li>正文:任务指令,交给 ContextLoader 注入 system prompt
 * </ul>
 */
@Component
public class AgentLoader {

  private final Path agentsDir;

  public AgentLoader() {
    this(Path.of(".oryxos", "agents"));
  }

  public AgentLoader(Path agentsDir) {
    this.agentsDir = agentsDir;
  }

  /**
   * 扫描 agents/ 下所有子目录,派生 Profile 并注册。
   *
   * @return 扫描到的 Agent 目录数
   */
  public int scanAndRegister(ProfileRegistry registry) {
    if (!Files.isDirectory(agentsDir)) {
      return 0;
    }
    int count = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(agentsDir)) {
      for (Path agentDir : stream) {
        if (!Files.isDirectory(agentDir)) continue;
        Path agentMd = agentDir.resolve("AGENT.md");
        if (!Files.exists(agentMd)) continue;
        try {
          ParsedAgentMd parsed = parseAgentMd(agentMd);
          Profile profile = deriveProfile(agentDir.getFileName().toString(), parsed);
          registry.register(profile);
          count++;
        } catch (Exception e) {
          // 校验失败的 Agent 不阻断启动但记录错误日志
          System.err.println("[AgentLoader] failed to load " + agentDir + ": " + e.getMessage());
        }
      }
    } catch (IOException e) {
      System.err.println("[AgentLoader] scan failed: " + e.getMessage());
    }
    return count;
  }

  /** 把 AGENT.md 的 frontmatter 派生成 Profile。 */
  @SuppressWarnings("unchecked")
  public Profile deriveProfile(String name, ParsedAgentMd parsed) {
    Map<String, Object> front = parsed.frontmatter();
    Profile profile = new Profile();
    profile.setName(name);
    profile.setDescription((String) front.get("description"));

    Map<String, Object> identity = (Map<String, Object>) front.get("identity");
    if (identity != null) {
      profile.setIdentity(
          new Profile.Identity(
              (String) identity.get("agent_name"), (String) identity.get("prompt")));
    }

    Map<String, Object> provider = (Map<String, Object>) front.get("provider");
    if (provider != null) {
      Double temperature = provider.get("temperature") instanceof Number n ? n.doubleValue() : null;
      profile.setProvider(
          new Profile.Provider(
              (String) provider.get("name"), (String) provider.get("model"), temperature));
    }

    List<String> tools = toStringList(front.get("tools"));
    if (tools != null) profile.setTools(tools);
    List<String> skills = toStringList(front.get("skills"));
    if (skills != null) profile.setSkills(skills);
    List<String> mcpServers = toStringList(front.get("mcp_servers"));
    if (mcpServers != null) profile.setMcpServers(mcpServers);
    List<String> bootstrap = toStringList(front.get("bootstrap"));
    if (bootstrap != null) profile.setBootstrap(bootstrap);

    // notify_channels: [{type, url}, ...]
    List<Map<String, String>> rawChannels =
        (List<Map<String, String>>) front.get("notify_channels");
    if (rawChannels != null) {
      List<Profile.NotifyChannel> channels = new ArrayList<>();
      for (Map<String, String> ch : rawChannels) {
        String type = ch.get("type");
        Map<String, String> config = new java.util.LinkedHashMap<>();
        for (var entry : ch.entrySet()) {
          if (!"type".equals(entry.getKey())) {
            config.put(entry.getKey(), resolveEnv(entry.getValue()));
          }
        }
        channels.add(new Profile.NotifyChannel(type, config));
      }
      profile.setNotifyChannels(channels);
    }

    return profile;
  }

  /** Resolve ${ENV_VAR} placeholders in config values. */
  private static String resolveEnv(String value) {
    if (value == null) return null;
    if (value.startsWith("${") && value.endsWith("}")) {
      String envKey = value.substring(2, value.length() - 1);
      String resolved = System.getenv(envKey);
      return resolved != null ? resolved : value;
    }
    return value;
  }

  /** 解析 AGENT.md:分离 frontmatter(YAML)和正文(Markdown)。 */
  @SuppressWarnings("unchecked")
  public ParsedAgentMd parseAgentMd(Path agentMd) throws IOException {
    String content = Files.readString(agentMd);
    String frontmatter;
    String body;
    if (content.startsWith("---")) {
      int end = content.indexOf("\n---", 3);
      if (end > 0) {
        frontmatter = content.substring(3, end).trim();
        body = content.substring(end + 4).trim();
      } else {
        frontmatter = content.substring(3).trim();
        body = "";
      }
    } else {
      frontmatter = "";
      body = content;
    }
    Map<String, Object> front = frontmatter.isBlank() ? Map.of() : new Yaml().load(frontmatter);
    return new ParsedAgentMd(front, body);
  }

  @SuppressWarnings("unchecked")
  private static List<String> toStringList(Object o) {
    if (o == null) return null;
    if (o instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    return List.of(String.valueOf(o));
  }

  public record ParsedAgentMd(Map<String, Object> frontmatter, String body) {}
}
