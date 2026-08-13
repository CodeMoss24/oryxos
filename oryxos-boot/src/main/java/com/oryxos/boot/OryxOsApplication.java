package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.ProfileRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import picocli.CommandLine;
import picocli.spring.boot.autoconfigure.PicocliAutoConfiguration;

/**
 * OryxOS Spring Boot 启动模块。
 *
 * <p>main 里按命令分流(CLAUDE.md 九):不需要 Spring 上下文的"轻命令"直接走 Picocli 执行并 return, 不起 Spring;需要
 * LLM/HTTP/调度的"重命令"才 SpringApplication.run,启动后由 CommandLineRunner 派发。
 *
 * <p>轻命令集:init / status / profile / provider / tool / session 以及无参数(打印 usage)。 重命令集:chat / serve /
 * gateway。
 */
// @EnableScheduling:提供 AgentScheduler(25 节)依赖的 TaskScheduler Bean——26 节 WebSmokeIT 首跑暴露该装配缺口
@SpringBootApplication(scanBasePackages = "com.oryxos", exclude = PicocliAutoConfiguration.class)
@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")
@EntityScan(basePackages = "com.oryxos.storage.entity")
@EnableScheduling
public class OryxOsApplication implements CommandLineRunner {

  /** 不需要 Spring 上下文的命令前缀(直接走文件/配置,启动快) */
  private static final Set<String> LIGHT_COMMANDS =
      Set.of(
          "init",
          "status",
          "profile",
          "provider",
          "tool",
          "session",
          "-h",
          "--help",
          "-V",
          "--version");

  private final CommandLine commandLine;
  private final AgentLoader agentLoader;
  private final ProfileRegistry profileRegistry;

  public OryxOsApplication(
      CommandLine commandLine, AgentLoader agentLoader, ProfileRegistry profileRegistry) {
    this.commandLine = commandLine;
    this.agentLoader = agentLoader;
    this.profileRegistry = profileRegistry;
  }

  public static void main(String[] args) {
    if (isLight(args)) {
      // 不起 Spring,直接 Picocli 执行后退出
      int exitCode = new CommandLine(new OryxOsCli()).execute(args);
      System.exit(exitCode);
      return;
    }
    // 重命令:起 Spring 上下文,由 CommandLineRunner 派发
    SpringApplication.run(OryxOsApplication.class, args);
  }

  private static boolean isLight(String[] args) {
    if (args.length == 0) return true;
    return LIGHT_COMMANDS.contains(args[0]);
  }

  @Override
  public void run(String... args) {
    // 仅当 .oryxos/agents/ 存在时扫描注册,避免 init 未跑时空扫
    Path agentsDir = Path.of(".oryxos", "agents");
    if (Files.isDirectory(agentsDir)) {
      agentLoader.scanAndRegister(profileRegistry);
    }
    // 重命令路径一定有参数(chat/serve/gateway),直接派发
    commandLine.execute(args);
  }
}
