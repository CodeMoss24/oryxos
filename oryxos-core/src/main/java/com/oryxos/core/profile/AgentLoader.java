package com.oryxos.core.profile;

import com.oryxos.core.runtime.OryxOsRuntime;
import com.oryxos.core.scheduler.ScheduleConfig;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 *
 * <p>第 29 节:补缺必填项校验(provider.name/name 缺则抛 IllegalArgumentException 点名 Agent+字段,单 Agent 失败不阻断
 * 其余)、资源路径识别(scripts/skills/REFERENCE.md,不进 Profile,仅供观测)。
 *
 * <p>第 31 节:frontmatter 不再解析 tools / notify_channels —— 工具走全局 ToolRegistry 全部注册项, 通知出口走全局
 * NotifyChannelRegistry(管理台 CRUD),这两键在 AGENT.md 里写没写都不影响运行。
 */
@Component
public class AgentLoader {

  private final Path agentsDir;

  public AgentLoader() {
    this(OryxOsRuntime.resolve("agents"));
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
    // 第 29 节:必填项校验——name 与 provider.name 缺则抛 IllegalArgumentException 点名。
    // 这是启动扫描与运行时注册共享的同一段校验(同一异常类型 + 同一消息),core 内不依赖 provider 模块。
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Agent '<unknown>': missing required field 'name'");
    }
    Map<String, Object> front = parsed.frontmatter();
    Map<String, Object> provider = (Map<String, Object>) front.get("provider");
    String providerName = provider == null ? null : (String) provider.get("name");
    if (providerName == null || providerName.isBlank()) {
      throw new IllegalArgumentException(
          "Agent '" + name + "': missing required field 'provider.name'");
    }

    Profile profile = new Profile();
    profile.setName(name);
    profile.setDescription((String) front.get("description"));

    Map<String, Object> identity = (Map<String, Object>) front.get("identity");
    if (identity != null) {
      profile.setIdentity(
          new Profile.Identity(
              (String) identity.get("agent_name"), (String) identity.get("prompt")));
    }

    // provider 已在必填校验处提取(非 null,name 非空);这里只取 model/temperature 落 Profile
    if (provider != null) {
      Double temperature = provider.get("temperature") instanceof Number n ? n.doubleValue() : null;
      profile.setProvider(
          new Profile.Provider(providerName, (String) provider.get("model"), temperature));
    }

    // 第 31 节:tools / notify_channels 不再内联(全局 ToolRegistry / NotifyChannelRegistry 是唯一真相源),
    // 写没写这两个键都不再进 Profile。
    List<String> skills = toStringList(front.get("skills"));
    if (skills != null) profile.setSkills(skills);
    List<String> mcpServers = toStringList(front.get("mcp_servers"));
    if (mcpServers != null) profile.setMcpServers(mcpServers);
    List<String> bootstrap = toStringList(front.get("bootstrap"));
    if (bootstrap != null) profile.setBootstrap(bootstrap);

    // schedules: [{id, cron, zone, message}, ...]——缺 id 的条目没有锁键可用,
    // 属配置错误,跳过该条并记日志,不阻断启动(与 notify_channels 同样的失败策略)
    List<Map<String, String>> rawSchedules = (List<Map<String, String>>) front.get("schedules");
    if (rawSchedules != null) {
      List<ScheduleConfig> schedules = new ArrayList<>();
      for (Map<String, String> sc : rawSchedules) {
        String id = sc.get("id");
        if (id == null || id.isBlank()) {
          System.err.println("[AgentLoader] schedule without id skipped for agent " + name);
          continue;
        }
        schedules.add(new ScheduleConfig(id, sc.get("cron"), sc.get("zone"), sc.get("message")));
      }
      profile.setSchedules(schedules);
    }

    return profile;
  }

  /**
   * 第 29 节:识别一个 Agent 目录里的可选资源(scripts/ skills/ REFERENCE.md)是否存在。
   *
   * <p>仅作可观测结果(供 harness 断言"认出资源"),不进 Profile——渐进式披露靠正文指引 + 底座 read_file/shell 按需取用,
   * 运行时取资源用相对工作区路径,底座读文件工具自己解析,不需要 Profile 携带绝对路径。
   *
   * @return key=资源名(scripts/skills/reference),value=该路径是否存在
   */
  public Map<String, Boolean> listResources(Path agentDir) {
    Map<String, Boolean> resources = new LinkedHashMap<>();
    resources.put("scripts", Files.isDirectory(agentDir.resolve("scripts")));
    resources.put("skills", Files.isDirectory(agentDir.resolve("skills")));
    resources.put("reference", Files.exists(agentDir.resolve("REFERENCE.md")));
    return resources;
  }

  /** 解析 AGENT.md:分离 frontmatter(YAML)和正文(Markdown)。 */
  public ParsedAgentMd parseAgentMd(Path agentMd) throws IOException {
    return parseAgentMd(Files.readString(agentMd));
  }

  /**
   * 解析 AGENT.md 内容(字符串):分离 frontmatter 和正文。第 30 节:生成草稿与覆写内容必须先于落盘校验(非法 → 400 不写坏目录), 只给 Path
   * 版本做不到——生成链路没有文件可读。
   */
  @SuppressWarnings("unchecked")
  public ParsedAgentMd parseAgentMd(String content) {
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
