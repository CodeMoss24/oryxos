package com.oryxos.cli;

import picocli.CommandLine.Command;

/**
 * oryxos profile — Profile 管理命令组(list / create / show / delete)。
 * 命令组名沿用 profile,操作的是 .oryxos/agents/ 下的 Agent 目录。
 */
@Command(name = "profile",
        subcommands = {
                ProfileCommand.List.class,
                ProfileCommand.Create.class,
                ProfileCommand.Show.class,
                ProfileCommand.Delete.class
        },
        description = "Profile(Agent)管理")
public class ProfileCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Usage: oryxos profile [list|create|show|delete]");
    }

    @Command(name = "list", description = "列出所有 Profile")
    static class List implements Runnable {
        @Override public void run() {
            java.nio.file.Path agents = java.nio.file.Path.of(".oryxos", "agents");
            if (!java.nio.file.Files.isDirectory(agents)) {
                System.out.println("(no agents)");
                return;
            }
            try (var stream = java.nio.file.Files.list(agents)) {
                stream.filter(java.nio.file.Files::isDirectory)
                        .forEach(p -> System.out.println(p.getFileName()));
            } catch (Exception e) {
                System.err.println("list failed: " + e.getMessage());
            }
        }
    }

    @Command(name = "create", description = "创建新 Profile(生成最小 AGENT.md 模板)")
    static class Create implements Runnable {
        @picocli.CommandLine.Parameters(index = "0") String name;

        @Override public void run() {
            try {
                java.nio.file.Path dir = java.nio.file.Path.of(".oryxos", "agents", name);
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path md = dir.resolve("AGENT.md");
                if (!java.nio.file.Files.exists(md)) {
                    String tpl = """
                            ---
                            name: %s
                            description: TODO
                            identity:
                              agent_name: %s
                              prompt: TODO
                            provider:
                              name: deepseek
                              model: deepseek-chat
                            tools: []
                            ---
                            # %s
                            TODO: 写任务指令
                            """.formatted(name, name, name);
                    java.nio.file.Files.writeString(md, tpl);
                }
                System.out.println("Created profile: " + name);
            } catch (Exception e) {
                System.err.println("create failed: " + e.getMessage());
            }
        }
    }

    @Command(name = "show", description = "查看 Profile 详情")
    static class Show implements Runnable {
        @picocli.CommandLine.Parameters(index = "0") String name;

        @Override public void run() {
            try {
                java.nio.file.Path md = java.nio.file.Path.of(".oryxos", "agents", name, "AGENT.md");
                if (java.nio.file.Files.exists(md)) {
                    System.out.println(java.nio.file.Files.readString(md));
                } else {
                    System.err.println("not found: " + name);
                }
            } catch (Exception e) {
                System.err.println("show failed: " + e.getMessage());
            }
        }
    }

    @Command(name = "delete", description = "删除 Profile(整个目录)")
    static class Delete implements Runnable {
        @picocli.CommandLine.Parameters(index = "0") String name;

        @Override public void run() {
            try {
                java.nio.file.Path dir = java.nio.file.Path.of(".oryxos", "agents", name);
                if (java.nio.file.Files.isDirectory(dir)) {
                    deleteRecursive(dir);
                    System.out.println("Deleted: " + name);
                }
            } catch (Exception e) {
                System.err.println("delete failed: " + e.getMessage());
            }
        }

        private static void deleteRecursive(java.nio.file.Path path) throws java.io.IOException {
            if (java.nio.file.Files.isDirectory(path)) {
                try (var stream = java.nio.file.Files.list(path)) {
                    for (var p : stream.toList()) deleteRecursive(p);
                }
            }
            java.nio.file.Files.deleteIfExists(path);
        }
    }
}
