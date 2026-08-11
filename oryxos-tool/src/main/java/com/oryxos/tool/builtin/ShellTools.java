package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Shell 内置 Tool:shell。执行 bash 命令,带超时和命令白名单。
 *
 * <p>普通方法由 ToolConfiguration 装配成工具;执行第一件事调 Sandbox.enforce(SHELL_COMMAND) 做命令白名单 检查。超时后
 * destroyForcibly 强杀进程,存量超时语义为失败("command timeout")。
 */
@Component
public class ShellTools {

  private final Sandbox sandbox;

  public ShellTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  public String shell(String command) throws IOException, InterruptedException {
    sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, command));
    ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
    pb.redirectErrorStream(true);
    Process process = pb.start();
    StringBuilder out = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) out.append(line).append("\n");
    }
    boolean finished = process.waitFor(30, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("command timeout");
    }
    return out.toString();
  }
}
