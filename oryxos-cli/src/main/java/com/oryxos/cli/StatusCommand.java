package com.oryxos.cli;

import picocli.CommandLine.Command;

@Command(name = "status", description = "查看 OryxOS 配置和运行状态")
public class StatusCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("OryxOS status:");
        System.out.println("  workspace: " + java.nio.file.Path.of(".oryxos").toAbsolutePath());
        System.out.println("  java: " + System.getProperty("java.version"));
        System.out.println("  (Spring context not started — run `oryxos serve` for full status)");
    }
}
