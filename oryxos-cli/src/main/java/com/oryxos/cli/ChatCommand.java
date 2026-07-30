package com.oryxos.cli;

import com.oryxos.channel.cli.CliChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * oryxos chat [--profile <name>] [--message "xxx"]
 * 启动 Spring 上下文,跑 CLI Channel 交互对话。
 */
@Command(name = "chat", description = "交互对话")
public class ChatCommand implements Runnable {

    @Option(names = {"-p", "--profile"}, description = "Profile 名")
    String profile;

    @Option(names = {"-m", "--message"}, description = "发单条消息后退出")
    String message;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void run() {
        // 由 Spring Boot 启动后触发,这里直接用注入的 CliChannel
        if (profile == null) {
            System.err.println("--profile is required");
            return;
        }
        CliChannel cliChannel = applicationContext.getBean(CliChannel.class);
        cliChannel.start(profile, message);
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
