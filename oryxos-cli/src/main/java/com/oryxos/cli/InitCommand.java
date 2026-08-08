package com.oryxos.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * oryxos init — 初始化工作区。 创建 .oryxos/ 目录及完整结构(agents/ skills/ output/ memory/ sessions/ logs/ +
 * AGENTS.md SOUL.md USER.md Bootstrap 文件)。 幂等:已存在的目录和文件一律不覆盖。
 */
@Command(name = "init", description = "初始化 OryxOS 工作区(.oryxos/)")
public class InitCommand implements Runnable {

  @Option(
      names = {"-w", "--workspace"},
      defaultValue = ".oryxos",
      description = "工作区路径")
  String workspace;

  @Override
  public void run() {
    try {
      Path root = Path.of(workspace);
      for (String dir : new String[] {"agents", "skills", "output", "memory", "sessions", "logs"}) {
        Files.createDirectories(root.resolve(dir));
      }
      createIfAbsent(root.resolve("AGENTS.md"), "# Agents\n\n项目级 agent 行为说明。\n");
      createIfAbsent(root.resolve("SOUL.md"), "# Soul\n\n默认 agent 人格定义。\n");
      createIfAbsent(root.resolve("USER.md"), "# User\n\n用户偏好。\n");
      createIfAbsent(root.resolve("memory/MEMORY.md"), "# Memory\n\n## 核心记忆\n\n## 归档记忆\n");
      createIfAbsent(root.resolve("mcp_servers.yaml"), "# MCP server 配置\n");
      System.out.println("OryxOS workspace initialized at " + root.toAbsolutePath());
    } catch (Exception e) {
      System.err.println("init failed: " + e.getMessage());
    }
  }

  private void createIfAbsent(Path path, String defaultContent) throws Exception {
    if (!Files.exists(path)) {
      Files.createDirectories(path.getParent());
      Files.writeString(path, defaultContent);
    }
  }
}
