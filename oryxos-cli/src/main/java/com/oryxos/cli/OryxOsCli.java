package com.oryxos.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Picocli 命令行入口,整个 OryxOS 的 main 函数,注册 12 个子命令。
 *
 * <p>不需要 Spring 上下文的命令(init、profile list)直接走文件操作启动快; 需要 LLM 调用的命令(chat、serve、gateway)启动 Spring 上下文。
 */
@Command(
    name = "oryxos",
    mixinStandardHelpOptions = true,
    version = "OryxOS 1.0.0-SNAPSHOT",
    description = "OryxOS — Java-native enterprise Agent OS",
    subcommands = {
      InitCommand.class,
      StatusCommand.class,
      ChatCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class
    })
@Component
public class OryxOsCli implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OryxOsCli()).execute(args);
    System.exit(exitCode);
  }
}
