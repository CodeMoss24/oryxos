# 配置契约（Config Contract）

**Feature**: 008-sandbox-module | **位置**: oryxos-boot `src/main/resources/application.yaml`

## 三个配置键（顶层）

| 键 | 绑定 | 示例 | 语义 |
|----|------|------|------|
| `file.allowed_paths` | FileSandboxProperties.allowedPaths | `[.oryxos]` 或绝对路径 | 允许的文件路径根列表；启动时 normalize + toAbsolutePath（相对按 cwd） |
| `shell.allowed_commands` | ShellSandboxProperties.allowedCommands | `[ls, cat, echo, date, python, git]` | 允许的命令首 token 集合 |
| `http.allowed_domains` | HttpSandboxProperties.allowedDomains | `["open.feishu.cn"]` | 允许的域名列表；`*.example.com` 匹配子域（带点号边界） |

**空值语义（必须写明在配置说明中）**：白名单配置为空 = "什么都不允许"，而非"不校验"。任何动作都会被拒。

## 绑定注册

三个 record 无 `@Component`，由 `ToolConfiguration`（com.oryxos.tool.config）上的 `@EnableConfigurationProperties` 注册为 Bean；`WhitelistSandbox`（@Component）构造器注入三 props。Boot 模块零改动（扫描已覆盖 com.oryxos）。

## 键值映射（relaxed binding）

`allowedPaths` ↔ `allowed_paths`（Spring relaxed binding 互认）；配置键字面量按课件/TechnicalSolution §6.7 用 `allowed_paths` 写法。

## 迁移

| 旧键（删除） | 新键（新增） |
|-------------|-------------|
| `oryxos.sandbox.file.allowed-paths` | `file.allowed_paths` |
| `oryxos.sandbox.shell.allowed-commands` | `shell.allowed_commands` |
| `oryxos.sandbox.http.allowed-domains` | `http.allowed_domains` |

值语义不变（`.oryxos` / `ls,cat,echo,date,python,git` / `open.feishu.cn`），仅键位置与形态变化。
