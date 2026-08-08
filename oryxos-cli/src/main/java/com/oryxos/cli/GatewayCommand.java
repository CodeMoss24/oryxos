package com.oryxos.cli;

import picocli.CommandLine.Command;

@Command(name = "gateway", description = "启动多渠道守护进程")
public class GatewayCommand implements Runnable {
  @Override
  public void run() {
    System.out.println("Starting OryxOS gateway (multi-channel daemon)...");
  }
}
