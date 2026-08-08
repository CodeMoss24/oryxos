package com.oryxos.cli;

import picocli.CommandLine.Command;

/** oryxos serve — 启动 HTTP API 服务(由 Spring Boot 主类承接)。 */
@Command(name = "serve", description = "启动 HTTP API 服务")
public class ServeCommand implements Runnable {
  @Override
  public void run() {
    // 实际由 OryxOsApplication 启动嵌入式 Tomcat
    System.out.println("Starting OryxOS Web Service on port 8080...");
  }
}
