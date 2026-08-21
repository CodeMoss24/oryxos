package com.oryxos.core.agent;

import com.oryxos.core.runtime.OryxOsRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 目录的唯一落盘出口(第 30 节)。
 *
 * <p>只管文件操作:脚手架四件套、覆写 AGENT.md、写多文件、删除回滚、归档到 .oryxos/archive/、 防目录穿越解析。目录内容校验 不在这里——派生/校验归
 * AgentLoader,编排归 AgentLifecycleService,本类保持零业务判断。
 *
 * <p>真相源是文件系统:API 创建、手工丢目录、启动扫描殊途同归,本类只提供"怎么写"的原语。
 */
@Component
public class AgentStore {

  private static final Logger log = LoggerFactory.getLogger(AgentStore.class);

  private final Path workspaceRoot;

  public AgentStore() {
    this(OryxOsRuntime.workspaceRoot());
  }

  public AgentStore(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
  }

  /** agents/<name> 目录(不保证存在)。 */
  public Path agentDir(String name) {
    return workspaceRoot.resolve("agents").resolve(name);
  }

  /**
   * 脚手架一个完整 Agent 目录:AGENT.md + scripts/ + skills/ + REFERENCE.md(模板内容,可解析)。
   *
   * <p>provider 写 deepseek + deepseek-chat 作模板起点(31 节 Demo 与运营后续手工改),31 节起 tools / notify_channels
   * 不再内联——工具走全局列表、通知出口走全局注册表,模板不写这两个键。正文由 description 派生一句任务指令。 返回 agentDir,供失败回滚。
   */
  /** schedule:创建时即写入 AGENT.md 的定时配置{cron, zone, message},null = 不定时。 */
  public Path scaffold(String name, String description, AgentStore.ScheduleDraft schedule) {
    Path dir = agentDir(name);
    try {
      Files.createDirectories(dir.resolve("scripts"));
      Files.createDirectories(dir.resolve("skills"));
      Files.writeString(dir.resolve("AGENT.md"), scaffoldAgentMd(name, description, schedule));
      Files.writeString(
          dir.resolve("scripts").resolve("README.md"),
          "# scripts\n\n放这个 Agent 的可执行脚本(如 `reconcile.py`),正文用 `shell` 按需调用。\n");
      // 注意:skills/ 是 Skill 绑定的唯一真相目录,只放固定软连接——不放 README 等普通文件,
      // 否则绑定校验器会把非软连接条目报为"损坏绑定"(INVALID_TARGET),导致 replaceBindings 整体拒绝。
      Files.writeString(
          dir.resolve("REFERENCE.md"),
          "# REFERENCE\n\n放参考材料,正文用 `read_file` 按需读取。渐进式披露:目录里的子指令/脚本/参考不预载。\n");
      return dir;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scaffold agent directory: " + dir, e);
    }
  }

  private static String scaffoldAgentMd(
      String name, String description, AgentStore.ScheduleDraft schedule) {
    StringBuilder sb =
        new StringBuilder("---\n")
            .append("name: ")
            .append(name)
            .append("\n")
            .append("description: ")
            .append(description)
            .append("\n")
            .append("provider:\n")
            .append("  name: deepseek\n")
            .append("  model: deepseek-chat\n")
            .append("identity:\n")
            .append("  agent_name: ")
            .append(name)
            .append("\n")
            .append("  prompt: 你是 ")
            .append(name)
            .append(",专注完成自己的任务\n");
    if (schedule != null && schedule.cron() != null && !schedule.cron().isBlank()) {
      sb.append("schedules:\n")
          .append("  - id: ")
          .append(name)
          .append("-schedule\n")
          .append("    cron: ")
          .append(schedule.cron())
          .append("\n")
          .append("    zone: ")
          .append(
              schedule.zone() == null || schedule.zone().isBlank()
                  ? "Asia/Shanghai"
                  : schedule.zone())
          .append("\n")
          .append("    message: ")
          .append(
              schedule.message() == null || schedule.message().isBlank()
                  ? "到点了,执行今天的任务。"
                  : schedule.message())
          .append("\n");
    }
    sb.append("---\n")
        .append("\n")
        .append("你是 ")
        .append(name)
        .append("。你的任务:")
        .append(description)
        .append("\n\n")
        .append("执行步骤:\n")
        .append("1. 明确今天要做什么(目标拆解)。\n")
        .append("2. 用内置工具按需取数(http_get / read_file / shell)。\n")
        .append("3. 汇总产出,按用户要求格式输出。\n\n")
        .append("产出格式:清晰、可执行;失败时说明原因并给出兜底建议。\n");
    return sb.toString();
  }

  /** 创建时可选定时草稿(与 web 层 CreateAgentRequest.ScheduleDraft 同构,避免 core 依赖 web 包)。 */
  public record ScheduleDraft(String cron, String zone, String message) {}

  /** 读取 AGENT.md 全文(管理台"基本信息"编辑先读后改)。目录不存在或不可读抛 IllegalArgumentException(→400)。 */
  public String read(String name) {
    try {
      return Files.readString(agentDir(name).resolve("AGENT.md"));
    } catch (IOException e) {
      throw new IllegalArgumentException("Agent 目录不存在或不可读: " + name, e);
    }
  }

  /** 覆写 AGENT.md(frontmatter + 正文全文,由调用方保证内容可解析)。 */
  public void writeAgentMd(String name, String content) {
    try {
      Path dir = agentDir(name);
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("AGENT.md"), content);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write AGENT.md for agent: " + name, e);
    }
  }

  /** 保存一组文件(保存草稿用)。相对路径必须落在该 Agent 目录内,越界拒绝。 */
  public void writeAll(String name, Map<String, String> files) {
    Path dir = agentDir(name);
    for (Map.Entry<String, String> entry : files.entrySet()) {
      Path target = resolveWithin(dir, entry.getKey());
      try {
        Files.createDirectories(target.getParent());
        Files.writeString(target, entry.getValue());
      } catch (IOException e) {
        throw new IllegalStateException(
            "Failed to write file " + entry.getKey() + " for agent " + name, e);
      }
    }
  }

  /** 删除整个 Agent 目录(create 失败回滚)。目录不存在时静默跳过。 */
  public void delete(Path agentDir) {
    Path dir = agentDir.toAbsolutePath().normalize();
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      // 先删文件再删目录;从深到浅
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  log.warn("Failed to delete {} during rollback", p, e);
                }
              });
      log.info("Deleted agent directory (rollback): {}", dir);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to delete agent directory: " + dir, e);
    }
  }

  /**
   * 归档 Agent 目录到 .oryxos/archive/(删除 Agent 用,不物理删——定义可追溯)。
   *
   * <p>归档目标已存在时加时间戳后缀,不覆盖旧归档(同 Agent 删了再建再删)。
   */
  public void archive(String name) {
    Path source = agentDir(name);
    if (!Files.exists(source)) {
      return;
    }
    try {
      Path archiveRoot = workspaceRoot.resolve("archive");
      Files.createDirectories(archiveRoot);
      Path target = archiveRoot.resolve(name);
      if (Files.exists(target)) {
        target = archiveRoot.resolve(name + "-" + Instant.now().toEpochMilli());
      }
      Files.move(source, target);
      log.info("Archived agent directory {} → {}", name, target);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to archive agent directory: " + name, e);
    }
  }

  /**
   * 防目录穿越:把相对工作区的路径解析为绝对路径,必须落在 workspaceRoot 内(normalize 后 startsWith)。
   *
   * <p>workspace 文件浏览/编辑(WorkspaceApiController)与写多文件共用;越界抛 IllegalArgumentException(→400)。
   */
  public Path resolveWorkspacePath(String relative) {
    Path resolved = workspaceRoot.resolve(relative).normalize();
    if (!resolved.startsWith(workspaceRoot)) {
      throw new IllegalArgumentException("path escapes workspace: " + relative);
    }
    return resolved;
  }

  /** 相对路径必须落在某目录内的解析(写多文件用),越界抛 IllegalArgumentException(→400)。 */
  private static Path resolveWithin(Path baseDir, String relative) {
    Path base = baseDir.toAbsolutePath().normalize();
    Path resolved = base.resolve(relative).normalize();
    if (!resolved.startsWith(base)) {
      throw new IllegalArgumentException("path escapes agent directory: " + relative);
    }
    return resolved;
  }

  /** 工作区根(供 WorkspaceApiController 校验/浏览)。 */
  public Path workspaceRoot() {
    return workspaceRoot;
  }
}
