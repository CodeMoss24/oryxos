# Data Model: Sandbox 沙箱模块

**Date**: 2026-08-11 | **Feature**: 008-sandbox-module

## 持久化

**无新增 SQLite 表。** 沙箱是运行时校验逻辑，不产生需持久化的业务数据。白名单是配置（application.yaml），不是数据库数据；校验失败记录走既有 `tool_invocations` 表（success=false + error_message），表结构零改动。

## 运行时值对象（无持久化）

### SandboxAction（沙箱动作）

一次"在受控环境里执行某个动作"的意图描述。

| 字段 | 类型 | 说明 |
|------|------|------|
| type | ActionType（枚举） | 动作类型：FILE_READ / FILE_WRITE / SHELL_COMMAND / HTTP_REQUEST（四值在第20节接线时已定死） |
| target | String | 纯字符串目标；具体语义（路径/命令/URL）由 type 决定，实现类自行解释 |

### ActionType（动作类型枚举）

| 值 | 语义 | 路由 |
|----|------|------|
| FILE_READ | 文件读 | checkFilePath（与 FILE_WRITE 共用路径白名单） |
| FILE_WRITE | 文件写 | checkFilePath（读写共用同一份路径白名单，为未来按读写分权限预留枚举位） |
| SHELL_COMMAND | shell 命令 | checkShellCommand（命令首 token） |
| HTTP_REQUEST | HTTP 请求 | checkHttpUrl（解析 host） |

## 配置契约（application.yaml 顶层三键）

| 配置键 | 绑定目标 | 语义 | 空值语义 |
|--------|----------|------|---------|
| `file.allowed_paths` | FileSandboxProperties.allowedPaths（List\<String\>） | 允许的文件路径根（启动时 normalize + toAbsolutePath，相对路径按 cwd 解析） | 空 = 什么都不允许（任何路径都拒绝），不是"不校验" |
| `shell.allowed_commands` | ShellSandboxProperties.allowedCommands（List\<String\>） | 允许的命令首 token 集（Set 预解析） | 空 = 什么都不允许（任何命令都拒绝） |
| `http.allowed_domains` | HttpSandboxProperties.allowedDomains（List\<String\>） | 允许的域名（精确匹配 + `*.` 通配符，带点号边界） | 空 = 什么都不允许（任何域名都拒绝） |

## 校验失败原因（随审计落库）

`SandboxViolationException` 的 message（可读文本，如"命令不在白名单内: rm"）经既有 ToolExecutor 失败路径写入 `tool_invocations.error_message`，回填给模型。无独立数据实体。
