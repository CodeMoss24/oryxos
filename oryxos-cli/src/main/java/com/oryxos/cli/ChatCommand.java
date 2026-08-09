package com.oryxos.cli;

import com.oryxos.channel.cli.CliChannel;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** oryxos chat [--profile <name>] [--message "xxx"] 启动 Spring 上下文,跑 CLI Channel 交互对话。 */
@Component
@Command(name = "chat", description = "交互对话", mixinStandardHelpOptions = true)
public class ChatCommand implements Runnable {

  @Option(
      names = {"-p", "--profile"},
      defaultValue = "default",
      description = "Profile 名")
  String profile;

  @Option(
      names = {"-m", "--message"},
      description = "发单条消息后退出")
  String message;

  private final CliChannel cliChannel;

  public ChatCommand(CliChannel cliChannel) {
    this.cliChannel = cliChannel;
  }

  @Override
  public void run() {
    cliChannel.start(profile, message);
  }
}
