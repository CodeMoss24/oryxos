package com.oryxos.tool.sandbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oryxos.core.exception.SandboxViolationException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 白名单沙箱 harness。安全模块的测试重点不是"放行对不对",是"绕得过绕不过":每类校验"允许+拒绝"成对,再补绕过场景(路径穿越、 形似域名)。配置构造直接走三个 props
 * record,不经 Spring 容器(校验逻辑本身与装配无关)。
 */
class WhitelistSandboxTest {

  private static WhitelistSandbox sandbox(
      List<String> paths, List<String> commands, List<String> domains) {
    return new WhitelistSandbox(
        new FileSandboxProperties(paths),
        new ShellSandboxProperties(commands),
        new HttpSandboxProperties(domains));
  }

  // ---------- 文件路径组 ----------

  @Test
  @DisplayName("文件路径:白名单内放行")
  void filePathAllowed() {
    WhitelistSandbox sandbox = sandbox(List.of("/workspace"), List.of(), List.of());
    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/workspace/a.txt")));
    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, "/workspace/b.txt")));
  }

  @Test
  @DisplayName("文件路径:白名单外拒绝")
  void filePathOutsideBlocked() {
    WhitelistSandbox sandbox = sandbox(List.of("/workspace"), List.of(), List.of());
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/etc/passwd")));
  }

  @Test
  @DisplayName("相对路径穿越必须被拦")
  void relativePathTraversalBlocked() {
    // 白名单只有 /workspace,构造 .. 序列爬到白名单之外
    WhitelistSandbox sandbox = sandbox(List.of("/workspace"), List.of(), List.of());
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.FILE_READ, "/workspace/../../outside/secret.txt")));
  }

  @Test
  @DisplayName("相对白名单根在启动时按当前工作目录解析为绝对路径")
  void relativeWhitelistRootResolvedAgainstCwd() {
    // 白名单 "."(相对),目标为 cwd 下的绝对路径:根与目标同基准,放行
    WhitelistSandbox sandbox = sandbox(List.of("."), List.of(), List.of());
    Path cwd = Path.of(".").toAbsolutePath().normalize();
    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.FILE_READ, cwd.resolve("pom.xml").toString())));
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.FILE_READ, cwd.resolve("../etc/passwd").toString())));
  }

  // ---------- Shell 命令组 ----------

  @Test
  @DisplayName("Shell 命令:白名单内放行")
  void shellCommandAllowed() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("echo", "ls"), List.of());
    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "ls /tmp")));
  }

  @Test
  @DisplayName("Shell 命令:白名单外拒绝")
  void shellCommandOutsideBlocked() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("echo", "ls"), List.of());
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "rm -rf /")));
  }

  @Test
  @DisplayName("Shell 命令:首 token 带前导空格先 trim 再比对")
  void shellCommandLeadingWhitespaceTrimmed() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("echo"), List.of());
    assertDoesNotThrow(
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "  echo hi")));
  }

  @Test
  @DisplayName("Shell 命令:大小写变体不做折叠,按原样比对")
  void shellCommandCaseVariantNotFold() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of("echo"), List.of());
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "ECHO hi")));
  }

  // ---------- HTTP 域名组 ----------

  @Test
  @DisplayName("HTTP 域名:精确匹配放行")
  void httpExactDomainAllowed() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("api.example.com"));
    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")));
  }

  @Test
  @DisplayName("HTTP 域名:白名单外拒绝")
  void httpDomainOutsideBlocked() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("api.example.com"));
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, "https://evil.com/x")));
  }

  @Test
  @DisplayName("通配符域名_不能被形似域名绕过")
  void wildcardDomainNotBypassedBySimilarDomain() {
    // 白名单:*.example.com——api.example.com 是子域放行;evil-example.com 以 "example.com" 结尾但不是子域!
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of("*.example.com"));
    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")));
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://evil-example.com/x")));
  }

  // ---------- 空白名单 = 什么都不允许 ----------

  @Test
  @DisplayName("空白名单 = 什么都不允许,而非不校验")
  void emptyWhitelistDeniesEverything() {
    WhitelistSandbox sandbox = sandbox(List.of(), List.of(), List.of());
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.FILE_READ, "/workspace/a.txt")));
    assertThrows(
        SandboxViolationException.class,
        () -> sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "echo hi")));
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")));
  }
}
