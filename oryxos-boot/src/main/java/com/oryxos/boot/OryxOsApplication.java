package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import com.oryxos.core.agent.WorkspaceWatcher;
import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ProviderPort;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final AgentScheduler agentScheduler;
  private final ProviderPort providerPort;
  private final ToolRegistry toolRegistry;
  private final WorkspaceWatcher workspaceWatcher;

  public OryxOsApplication(
      CommandLine commandLine,
      AgentLoader agentLoader,
      ProfileRegistry profileRegistry,
      AgentScheduler agentScheduler,
      ProviderPort providerPort,
      ToolRegistry toolRegistry,
      WorkspaceWatcher workspaceWatcher) {
    this.commandLine = commandLine;
    this.agentLoader = agentLoader;
    this.profileRegistry = profileRegistry;
    this.agentScheduler = agentScheduler;
    this.providerPort = providerPort;
    this.toolRegistry = toolRegistry;
    this.workspaceWatcher = workspaceWatcher;
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
    Path agentsDir = com.oryxos.core.runtime.OryxOsRuntime.resolve("agents");
    if (Files.isDirectory(agentsDir)) {
      agentLoader.scanAndRegister(profileRegistry);
      // 第 29 节:两层校验(core 必填项已在 deriveProfile 内做)。这里在装配层补:
      // ① provider name→已注册 ChatModel 映射校验(复用 16 节 connectivity 显式映射),
      //   未映射 log.warn 不阻断;core 不反向依赖 provider,故落 boot。
      // ② tools 未注册能力告警(ToolRegistry 在 core,AgentLoader 方法参数注入)。
      validateRegisteredAgents();
      // 定时任务登记必须在 agents 扫描+校验之后——registerAll 读 ProfileRegistry,
      // 早于扫描跑会扫到空集(原 @PostConstruct 触发太早,28 节接入持久化后暴露)。
      agentScheduler.registerAll();
      // 第 30 节:监听 agents/ 目录——此后手工丢目录/删目录实时生效(免重启),扫描注册与事件注册同一段代码
      workspaceWatcher.start();
    }
    // 重命令路径一定有参数(chat/serve/gateway),直接派发
    commandLine.execute(args);
  }

  /**
   * 装配层校验:provider 真实性(16 节显式映射)。均非阻断——非法 Agent 在 core 必填校验处已被 scanAndRegister 跳过, 这里只对已注册的合法 Agent
   * 做软告警,让运维知晓配置缺口。31 节起 tools 走全局列表,不再有 per-Agent 工具告警。
   */
  private void validateRegisteredAgents() {
    Logger log = LoggerFactory.getLogger(OryxOsApplication.class);
    java.util.Map<String, Boolean> connectivity = providerPort.connectivity();
    for (Profile profile : profileRegistry.list()) {
      String providerName = profile.getProvider() == null ? null : profile.getProvider().name();
      if (providerName != null && !connectivity.containsKey(providerName)) {
        log.warn(
            "Agent '{}' declares provider '{}' not in the registered ChatModel mapping — load continues but LLM calls will fail at runtime",
            profile.getName(),
            providerName);
      }
    }
  }
}
