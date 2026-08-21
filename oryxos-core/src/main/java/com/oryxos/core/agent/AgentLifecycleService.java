package com.oryxos.core.agent;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.AgentLoader.ParsedAgentMd;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.LlmResponse;
import com.oryxos.core.react.Prompt;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduleConfig;
import com.oryxos.core.session.Message;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Agent 生命周期编排者(第 30 节)——本节唯一的新能力:把 29 节立好的原语(deriveProfile、register/remove、registerProfile +
 * 句柄表)按顺序串起来、失败回滚。没有一件新底层能力,全是既有方法的编排。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>{@link #create}:冲突一步拒(目录都不写)→ 脚手架 → {@link #register(Path)};注册失败回滚已写目录(不留半个 Agent)。
 *   <li>{@link #register(Path)}:唯一注册入口,create 与 WorkspaceWatcher 走同一段代码(解析目录内 AGENT.md → 派生校验 → 注册
 *       → 有 schedules 再注册定时)。
 *   <li>{@link #update}:先校验内容(非法 400,不写坏目录)→ 写盘 → schedules 变更才注销旧句柄、再注册新的(不变则 registerProfile
 *       幂等跳过)。
 *   <li>{@link #delete}:先注销定时 → 移出索引 → 目录归档 .oryxos/archive/(不物理删,可追溯)。
 * </ul>
 *
 * <p>异常口径:校验失败抛 IllegalArgumentException(→400),与启动扫描(AgentLoader.deriveProfile)同一异常类型——API
 * 创建与手工丢目录行为一致。
 */
@Component
public class AgentLifecycleService {

  private final AgentLoader agentLoader;
  private final ProfileRegistry profileRegistry;
  private final AgentScheduler agentScheduler;
  private final AgentStore agentStore;
  private final ProviderPort providerPort;
  private final String authorProviderName;
  private final String authorModel;
  private final String defaultProviderName;

  public AgentLifecycleService(
      AgentLoader agentLoader,
      ProfileRegistry profileRegistry,
      AgentScheduler agentScheduler,
      AgentStore agentStore,
      ProviderPort providerPort,
      @Value("${oryxos.author.provider:}") String authorProviderName,
      @Value("${oryxos.author.model:}") String authorModel,
      @Value("${oryxos.providers[0].name:}") String defaultProviderName) {
    this.agentLoader = agentLoader;
    this.profileRegistry = profileRegistry;
    this.agentScheduler = agentScheduler;
    this.agentStore = agentStore;
    this.providerPort = providerPort;
    this.authorProviderName = authorProviderName;
    this.authorModel = authorModel;
    this.defaultProviderName = defaultProviderName;
  }

  /** 创建 Agent:脚手架完整目录(name + description)→ 派生注册。 失败回滚已写目录;name 冲突第一步就拒,一个目录都不写。 */
  public Profile create(String name, String description) {
    return create(name, description, null);
  }

  /** schedule:创建时即写入 AGENT.md 的定时配置,见 {@link AgentStore.ScheduleDraft}。 */
  public Profile create(String name, String description, AgentStore.ScheduleDraft schedule) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Agent name must not be blank");
    }
    if (profileRegistry.exists(name)) {
      throw new IllegalArgumentException("Agent 已存在: " + name);
    }
    Path agentDir = agentStore.scaffold(name, description, schedule);
    try {
      return register(agentDir);
    } catch (RuntimeException e) {
      // 半成品不入注册表、不上定时、目录删干净——不留半个 Agent
      profileRegistry.remove(name);
      agentStore.delete(agentDir);
      throw e;
    }
  }

  /**
   * 注册一个 Agent 目录(API 创建与 WorkspaceWatcher 共用的一段代码)。
   *
   * <p>解析目录里 AGENT.md → deriveProfile(与启动扫描同一校验)→ 注册 → 有 schedules 再注册定时。 校验失败抛
   * IllegalArgumentException(与启动扫描同一异常类型),目录内容坏就不注册。
   */
  public Profile register(Path agentDir) {
    Path agentMd = agentDir.resolve("AGENT.md");
    ParsedAgentMd parsed;
    try {
      parsed = agentLoader.parseAgentMd(agentMd);
    } catch (IOException e) {
      throw new IllegalArgumentException("failed to parse AGENT.md in " + agentDir, e);
    }
    String name = agentDir.getFileName().toString();
    Profile profile = agentLoader.deriveProfile(name, parsed);
    profileRegistry.register(profile);
    if (hasSchedules(profile)) {
      agentScheduler.registerProfile(profile);
    }
    return profile;
  }

  /**
   * 覆写 AGENT.md(workspace 编辑/文件保存走这里):先校验内容可解析(非法 400,不写坏目录)→ 写盘 → schedules 变更先注销旧句柄再注册新的,写入即生效。
   */
  public Profile update(String name, String agentMdContent) {
    // 先于落盘的校验:解析 + 派生(与启动扫描同一段校验)
    ParsedAgentMd parsed = agentLoader.parseAgentMd(agentMdContent);
    Profile fresh = agentLoader.deriveProfile(name, parsed);
    Optional<Profile> old = profileRegistry.find(name);
    boolean schedulesChanged =
        old.map(o -> !scheduleIds(o).equals(scheduleIds(fresh)))
            .orElse(!fresh.getSchedules().isEmpty());
    if (old.isPresent() && schedulesChanged) {
      agentScheduler.unregisterProfile(old.get());
    }
    agentStore.writeAgentMd(name, agentMdContent);
    profileRegistry.register(fresh);
    if (hasSchedules(fresh)) {
      agentScheduler.registerProfile(fresh); // schedules 不变时幂等跳过,变了时已注销所以重新调度
    }
    return fresh;
  }

  /**
   * 删除 Agent:先注销定时(内存句柄 + scheduled_tasks/task_executions 库记录级联清)→ 移出索引 → 整个目录归档到
   * .oryxos/archive/(不物理删,定义可追溯)。 未注册过的名字跳过注销,只归档。
   */
  public void delete(String name) {
    profileRegistry
        .find(name)
        .ifPresent(
            p -> {
              for (ScheduleConfig sc : p.getSchedules()) {
                agentScheduler.deleteTask(sc.id()); // 句柄取消 + 三个索引清理 + 库记录删除,一步到位
              }
            });
    profileRegistry.remove(name);
    agentStore.archive(name);
  }

  public Optional<Profile> find(String name) {
    return profileRegistry.find(name);
  }

  public List<Profile> list() {
    return new ArrayList<>(profileRegistry.list());
  }

  /**
   * 一句话生成 AGENT.md 草稿(5.2.4):一次 LLM 调用(providerPort.chat,落 llm_calls 审计)→ 剥代码围栏 → AgentLoader 校验可解析
   * (非法 400 可读原因)→ 返回 {相对路径 → 内容} 预览可改。不落盘、不注册。
   *
   * <p>provider 选择:Agent 已存在 → 沿用该 Agent 的 provider(保持可跑);否则 author 配置(缺省取 oryxos.providers 第一个),
   * model 留空 → IllegalStateException(→503,不发 model=null)。
   */
  public Map<String, String> generateFiles(String name, String description) {
    Profile.Provider genProvider =
        profileRegistry.find(name).map(Profile::getProvider).orElseGet(this::authorProvider);
    if (genProvider == null || genProvider.model() == null || genProvider.model().isBlank()) {
      throw new IllegalStateException(
          "author model 未配置:请在 application.yaml 配置 oryxos.author.model(provider 缺省取 oryxos.providers 第一个)");
    }
    Profile genProfile = new Profile();
    genProfile.setName("__generator__");
    genProfile.setProvider(genProvider);
    Prompt prompt =
        new Prompt(
            List.of(
                Message.user(
                    AGENT_AUTHOR_PROMPT
                        + "\n\n请为 Agent '"
                        + name
                        + "' 生成 AGENT.md。用户描述:"
                        + description)));
    LlmResponse response = providerPort.chat("author-generator", genProfile, prompt);
    String content = stripCodeFence(response.content());
    // 校验能否解析成合法定义(与启动扫描同一段校验),非法 → 400 可读原因
    ParsedAgentMd parsed = agentLoader.parseAgentMd(content);
    agentLoader.deriveProfile(name, parsed);
    Map<String, String> files = new LinkedHashMap<>();
    files.put("AGENT.md", content);
    return files;
  }

  /**
   * 只改 AGENT.md frontmatter 的 description / provider.name / provider.model(管理台"基本信息"编辑): 解析 → 改
   * frontmatter Map → 重新 dump YAML → 走 {@link #update}(先校验后落盘,非法 400 不写坏目录,写入即生效)。 skill 绑定不写回
   * AGENT.md(参考版约定:绑定是软连接,与 frontmatter 无关)。
   */
  public Profile updateBasicInfo(String name, String description, String provider, String model) {
    ParsedAgentMd parsed = agentLoader.parseAgentMd(agentStore.read(name));
    // parseAgentMd 的 frontmatter 可能不可变(空 frontmatter 时是 Map.of),统一复制成可变保序 Map
    Map<String, Object> fm = new LinkedHashMap<>(parsed.frontmatter());
    if (description != null) {
      String d = description.strip();
      if (d.isEmpty()) {
        fm.remove("description");
      } else {
        fm.put("description", d);
      }
    }
    if (provider != null && !provider.isBlank()) {
      Map<String, Object> pm = providerMap(fm);
      pm.put("name", provider.strip());
      if (model != null && !model.isBlank()) {
        pm.put("model", model.strip());
      }
      fm.put("provider", pm);
    } else if (model != null && !model.isBlank()) {
      Map<String, Object> pm = providerMap(fm);
      pm.put("model", model.strip());
      fm.put("provider", pm);
    }
    return update(name, dumpFrontmatter(fm, parsed.body()));
  }

  /**
   * 给已存在的 Agent 添加一个定时任务(管理台"添加定时任务"):校验 cron 合法 → 读 AGENT.md → frontmatter 追加 schedules 条目(id 生成
   * <name>-schedule,同 Agent 已有时拒绝)→ 走 update(先校验后落盘,注销旧句柄注册新句柄)。Agent 不存在 → 404。
   */
  public Profile addSchedule(String name, AgentStore.ScheduleDraft draft) {
    if (draft == null || draft.cron() == null || draft.cron().isBlank()) {
      throw new IllegalArgumentException("cron 不能为空");
    }
    if (profileRegistry.find(name).isEmpty()) {
      throw new IllegalArgumentException("Agent 不存在: " + name);
    }
    try {
      CronExpression.parse(draft.cron().trim()); // 先验 cron,非法 400 不写进 AGENT.md
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("非法 cron 表达式: " + draft.cron(), e);
    }
    ParsedAgentMd parsed = agentLoader.parseAgentMd(agentStore.read(name));
    Map<String, Object> fm = new LinkedHashMap<>(parsed.frontmatter());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> schedules =
        fm.get("schedules") instanceof List
            ? new ArrayList<>((List<Map<String, Object>>) fm.get("schedules"))
            : new ArrayList<>();
    String id = name + "-schedule";
    boolean dup = schedules.stream().anyMatch(s -> id.equals(s.get("id")));
    if (dup) {
      throw new IllegalArgumentException("Agent " + name + " 已有一个定时任务(" + id + "),先删除再添加");
    }
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", id);
    entry.put("cron", draft.cron().trim());
    entry.put(
        "zone",
        draft.zone() == null || draft.zone().isBlank() ? "Asia/Shanghai" : draft.zone().trim());
    entry.put(
        "message",
        draft.message() == null || draft.message().isBlank()
            ? "到点了,执行今天的任务。"
            : draft.message().trim());
    schedules.add(entry);
    fm.put("schedules", schedules);
    return update(name, dumpFrontmatter(fm, parsed.body()));
  }

  /**
   * 删除一个定时任务:找所属 Agent → 从 AGENT.md 的 schedules 移除该条目(空则删 key)→ 走 update 重注册剩余任务 → deleteTask
   * 清句柄+库。Agent 已归档(幽灵任务,定义源不在)时直接清库。
   */
  public void removeSchedule(String taskId) {
    Optional<Profile> owner =
        profileRegistry.list().stream()
            .filter(p -> p.getSchedules().stream().anyMatch(sc -> sc.id().equals(taskId)))
            .findFirst();
    if (owner.isEmpty()) {
      // 幽灵任务:定义源(Agent)已不存在,直接清库;deleteTask 对无句柄的 id 幂等
      agentScheduler.deleteTask(taskId);
      return;
    }
    String name = owner.get().getName();
    ParsedAgentMd parsed = agentLoader.parseAgentMd(agentStore.read(name));
    Map<String, Object> fm = new LinkedHashMap<>(parsed.frontmatter());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> schedules =
        fm.get("schedules") instanceof List
            ? new ArrayList<>((List<Map<String, Object>>) fm.get("schedules"))
            : new ArrayList<>();
    boolean removed = schedules.removeIf(s -> taskId.equals(s.get("id")));
    if (!removed) {
      throw new IllegalArgumentException("定时任务不存在: " + taskId);
    }
    if (schedules.isEmpty()) {
      fm.remove("schedules");
    } else {
      fm.put("schedules", schedules);
    }
    update(name, dumpFrontmatter(fm, parsed.body()));
    agentScheduler.deleteTask(taskId); // 注销句柄 + 删库记录(update 的 registerProfile 只 upsert 剩余任务)
  }

  /** frontmatter Map + 正文 → 完整 AGENT.md 文本(updateBasicInfo / addSchedule / removeSchedule 共用)。 */
  private static String dumpFrontmatter(Map<String, Object> fm, String body) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    return "---\n" + new Yaml(opts).dump(fm) + "---\n\n" + body.strip() + "\n";
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> providerMap(Map<String, Object> fm) {
    return fm.get("provider") instanceof Map
        ? new LinkedHashMap<>((Map<String, Object>) fm.get("provider"))
        : new LinkedHashMap<>();
  }

  /** 保存一组文件并生效(5.2.4):先校验 AGENT.md 可解析(非法 400,不写坏目录)→ 覆写后重注册(schedules 变更先注销旧的)→ 其余文件直接写盘。 */
  public Profile saveFiles(String name, Map<String, String> files) {
    String agentMd = files.get("AGENT.md");
    if (agentMd != null) {
      update(name, agentMd); // update 内部:校验先于落盘 → 写 → schedules 变更注销旧注册新
    }
    agentStore.writeAll(name, files);
    return profileRegistry
        .find(name)
        .orElseThrow(() -> new IllegalArgumentException("agent not registered: " + name));
  }

  private Profile.Provider authorProvider() {
    String providerName =
        authorProviderName == null || authorProviderName.isBlank()
            ? defaultProviderName
            : authorProviderName;
    return new Profile.Provider(providerName, authorModel, null);
  }

  /** 剥掉模型偶尔多吐的 ``` 代码围栏。 */
  private static String stripCodeFence(String content) {
    String trimmed = content == null ? "" : content.trim();
    if (trimmed.startsWith("```")) {
      int newline = trimmed.indexOf('\n');
      trimmed = newline > 0 ? trimmed.substring(newline + 1) : trimmed.substring(3);
      trimmed = trimmed.trim();
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
      }
    }
    return trimmed;
  }

  private static boolean hasSchedules(Profile profile) {
    return profile.getSchedules() != null && !profile.getSchedules().isEmpty();
  }

  private static List<String> scheduleIds(Profile profile) {
    return profile.getSchedules().stream().map(ScheduleConfig::id).toList();
  }

  /** 生成用系统提示词(docs/prompt/prompt.md 的 AGENT_AUTHOR_PROMPT,逐字一致)。 */
  private static final String AGENT_AUTHOR_PROMPT =
      "你是 OryxOS 的 Agent 作者助手。根据用户的一句描述,产出一份规范的 OryxOS `AGENT.md`。\n"
          + "\n"
          + "OryxOS 中一个 Agent = 一个目录(`.oryxos/agents/<name>/`),由一份 `AGENT.md` 定义,格式如下:\n"
          + "\n"
          + "```markdown\n"
          + "---\n"
          + "name: <Agent 名,与目录名一致,英文小写连字符>\n"
          + "description: <一句话定位,告诉别人这个 Agent 干什么>\n"
          + "provider:\n"
          + "  name: <deepseek | kimi | mock>\n"
          + "  model: <模型名,如 deepseek-chat>\n"
          + "identity:\n"
          + "  agent_name: <对话中自称的名字>\n"
          + "  prompt: <人格设定,一句话>\n"
          + "schedules:        # 可选,定时自动触发\n"
          + "  - id: <唯一任务 id,如 daily-weather>\n"
          + "    cron: <cron 表达式,如 0 9 * * *>\n"
          + "    zone: <时区,如 Asia/Shanghai>\n"
          + "    message: <到点时触发 Agent 的话,写清楚要做什么>\n"
          + "---\n"
          + "\n"
          + "<正文:给这个 Agent 的任务指令,清晰、可执行,说明目标、步骤与产出格式>\n"
          + "```\n"
          + "\n"
          + "输出要求:\n"
          + "\n"
          + "1. 只输出这份 `AGENT.md` 的完整内容(frontmatter + 正文),不要额外解释。\n"
          + "2. `name` 必须与用户描述的主题一致,小写英文连字符,如 `daily-weather`。\n"
          + "3. `schedules` 的 cron 必须仔细换算用户描述的时间(注意时区),宁可保守也不要乱猜。\n"
          + "4. 不要在 frontmatter 里写 `tools` 或 `notify_channels`——工具全局可用(内置 + MCP 全量),通知渠道在管理台「Notify 渠道」里统一配置,Agent 正文里说明用 notify 工具推送即可。\n"
          + "5. 用户没提到的配置项(如 schedules、identity)不要臆造,留空。\n"
          + "6. 正文用中文写,包含:职责、执行步骤、产出格式、失败时的兜底行为。";
}
