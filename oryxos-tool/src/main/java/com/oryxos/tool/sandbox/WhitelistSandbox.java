package com.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 核心阶段唯一实现。文件操作限制工作目录、Shell 命令白名单、HTTP 域名白名单, 在应用层做校验,不使用 Java SecurityManager(JDK 17 起已废弃、JDK 21
 * 已不可用)。
 *
 * <p>应用层白名单是"劝阻级"防线,防的是模型犯傻误操作,防不住蓄意绕过。三个校验方法都是 private——对外只有 enforce 一个入口,接口不被这一档 实现带偏。
 *
 * <p>路径校验:目标 normalize().toAbsolutePath() 后必须 startsWith 某个允许根;允许根在构造时同样转绝对(相对配置按当前工作目录解析), 基准一致才能防
 * `../` 路径穿越。
 */
@Component
public class WhitelistSandbox implements Sandbox {

  private final List<Path> allowedRoots;
  private final Set<String> allowedCommands;
  private final List<String> allowedDomainPatterns;

  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.allowedRoots =
        fileProps.allowedPaths().stream()
            .map(Path::of)
            .map(Path::normalize)
            .map(Path::toAbsolutePath)
            .toList();
    this.allowedCommands = Set.copyOf(shellProps.allowedCommands());
    this.allowedDomainPatterns = List.copyOf(httpProps.allowedDomains());
  }

  @Override
  public void enforce(SandboxAction action) {
    switch (action.type()) {
      case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
      case SHELL_COMMAND -> checkShellCommand(action.target());
      case HTTP_REQUEST -> checkHttpUrl(action.target());
    }
  }

  private void checkFilePath(String rawPath) {
    Path target = Path.of(rawPath).normalize().toAbsolutePath();
    boolean allowed = allowedRoots.stream().anyMatch(target::startsWith);
    if (!allowed) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  private void checkShellCommand(String command) {
    String firstToken = command.trim().split("\\s+")[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  private void checkHttpUrl(String url) {
    String host = URI.create(url).getHost();
    boolean allowed =
        allowedDomainPatterns.stream().anyMatch(pattern -> matchesDomain(host, pattern));
    if (!allowed) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  private boolean matchesDomain(String host, String pattern) {
    if (pattern.startsWith("*.")) {
      // 带点号边界:*.example.com → .example.com 结尾才命中,形似域名 evil-example.com 不得绕过
      return host.endsWith(pattern.substring(1));
    }
    return host.equals(pattern);
  }
}
