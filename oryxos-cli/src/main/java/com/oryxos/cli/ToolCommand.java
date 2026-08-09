package com.oryxos.cli;

import picocli.CommandLine.Command;

@Command(name = "tool", description = "列出已注册的 Tool", mixinStandardHelpOptions = true)
public class ToolCommand implements Runnable {
  @Override
  public void run() {
    System.out.println(
        "Tools (Spring context not started — run `oryxos serve` for live tool list)");
  }
}
