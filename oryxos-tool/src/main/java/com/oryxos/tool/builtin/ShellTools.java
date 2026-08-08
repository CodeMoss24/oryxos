package com.oryxos.tool.builtin;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Shell 内置 Tool。执行 bash 命令,带超时和命令白名单。 */
@Component
public class ShellTools {

  private final Sandbox sandbox;

  public ShellTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  @Component("shell")
  public class ShellTool implements OryxTool {
    @Override
    public String getName() {
      return "shell";
    }

    @Override
    public String getDescription() {
      return "执行 bash 命令(受命令白名单限制)";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},"
          + "\"required\":[\"command\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String command = FileTools.extractField(inputJson, "command");
      try {
        sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.SHELL_COMMAND, command));
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) out.append(line).append("\n");
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
          process.destroyForcibly();
          return ToolResult.failure("command timeout", false);
        }
        return ToolResult.success(out.toString());
      } catch (SandboxViolationException e) {
        return ToolResult.failure(e.getMessage(), false);
      } catch (Exception e) {
        return ToolResult.failure(e.getMessage(), true);
      }
    }
  }
}
