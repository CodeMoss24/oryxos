package com.oryxos.tool.sandbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 核心阶段唯一实现。文件操作限制工作目录、Shell 命令白名单、HTTP 域名白名单,
 * 在应用层做校验,不使用 Java SecurityManager(JDK 17 起已废弃、JDK 21 已不可用)。
 *
 * <p>应用层白名单是"劝阻级"防线,防的是模型犯傻误操作,防不住蓄意绕过。
 */
@Component
public class WhitelistSandbox implements Sandbox {

    private final List<String> allowedPaths;
    private final List<String> allowedCommands;
    private final List<String> allowedDomains;

    public WhitelistSandbox(
            @Value("${oryxos.sandbox.file.allowed-paths:.oryxos}") String allowedPathsCsv,
            @Value("${oryxos.sandbox.shell.allowed-commands:ls,cat,echo,date,python,git}") String allowedCommandsCsv,
            @Value("${oryxos.sandbox.http.allowed-domains:}") String allowedDomainsCsv) {
        this.allowedPaths = parseCsv(allowedPathsCsv);
        this.allowedCommands = parseCsv(allowedCommandsCsv);
        this.allowedDomains = parseCsv(allowedDomainsCsv);
    }

    @Override
    public void enforce(SandboxAction action) throws SandboxViolationException {
        switch (action.type()) {
            case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
            case SHELL_COMMAND -> checkShellCommand(action.target());
            case HTTP_REQUEST -> checkHttpUrl(action.target());
        }
    }

    private void checkFilePath(String target) {
        Path normalized = Path.of(target).normalize();
        for (String allowed : allowedPaths) {
            Path allowedPath = Path.of(allowed).normalize();
            if (normalized.startsWith(allowedPath)) {
                return;
            }
        }
        throw new SandboxViolationException("Path not allowed: " + target);
    }

    private void checkShellCommand(String target) {
        String firstToken = target.trim().split("\\s+")[0];
        if (!allowedCommands.contains(firstToken)) {
            throw new SandboxViolationException("Command not allowed: " + firstToken);
        }
    }

    private void checkHttpUrl(String target) {
        String host = extractHost(target);
        for (String allowed : allowedDomains) {
            if (matchesDomain(host, allowed)) {
                return;
            }
        }
        throw new SandboxViolationException("Domain not allowed: " + host);
    }

    private static boolean matchesDomain(String host, String pattern) {
        if (pattern.startsWith("*.")) {
            return host.endsWith(pattern.substring(2));
        }
        return host.equals(pattern);
    }

    private static String extractHost(String url) {
        String s = url;
        int schemeIdx = s.indexOf("://");
        if (schemeIdx > 0) s = s.substring(schemeIdx + 3);
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon > 0) s = s.substring(0, colon);
        return s;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
