package com.oryxos.cli;

import picocli.CommandLine.Command;

@Command(name = "session", description = "列出会话历史")
public class SessionCommand implements Runnable {
  @Override
  public void run() {
    System.out.println(
        "Sessions (Spring context not started — run `oryxos serve` for live session list)");
  }
}
