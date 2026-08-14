package com.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** oryxos serve — 启动 HTTP API 服务(由 Spring Boot 主类承接)。 */
@Command(name = "serve", description = "启动 HTTP API 服务", mixinStandardHelpOptions = true)
public class ServeCommand implements Runnable {

  /**
   * 透传给 Spring Boot 的端口。SpringApplication.run 阶段已把 --server.port 消费为属性并完成绑定, 这里声明同名选项仅为让 Picocli 不把
   * start.sh 传来的参数当未知参数报错(见 OryxOsApplication#run)。
   */
  @Option(
      names = "--server.port",
      hidden = true,
      description = "HTTP 服务端口(默认 8080,由 Spring Boot 消费)")
  private String serverPort;

  @Override
  public void run() {
    // 实际由 OryxOsApplication 启动嵌入式 Tomcat
    System.out.println(
        "Starting OryxOS Web Service on port "
            + (serverPort == null ? "8080" : serverPort)
            + "...");
  }
}
