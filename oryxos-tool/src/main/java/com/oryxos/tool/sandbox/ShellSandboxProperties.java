package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** shell 命令白名单配置:shell.allowed_commands(命令首 token 比对)。空 = 什么都不允许,而非不校验。 */
@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {}
