package com.oryxos.cli;

import picocli.CommandLine.Command;

@Command(name = "provider", description = "列出已配置的 Provider")
public class ProviderCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Providers (from application.yaml):");
        System.out.println("  (Spring context not started — run `oryxos serve` for live provider list)");
    }
}
