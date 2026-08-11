# 沙箱接口契约（Sandbox Contract）

**Feature**: 008-sandbox-module | **位置**: `com.oryxos.tool.sandbox` 包 | **模块**: oryxos-tool

## 契约目的

"在受控环境里执行一个动作"的抽象入口。接口不携带任何一档实现特有的概念——用最重的 microVM 实现（容器隔离 / microVM）反向套这个签名，也应能干净套入，这是接口中立性的校验办法。

## 接口

```java
public interface Sandbox {
    void enforce(SandboxAction action);
}
```

## 值对象

```java
public record SandboxAction(ActionType type, String target) {}
```

```java
public enum ActionType {
    FILE_READ,     // 文件读 → 路径白名单
    FILE_WRITE,    // 文件写 → 路径白名单（与读共用，枚举位为未来分权预留）
    SHELL_COMMAND, // shell 命令 → 命令首 token 白名单
    HTTP_REQUEST   // HTTP 请求 → 域名白名单（含通配符）
}
```

## 异常语义

```java
public class SandboxViolationException extends RuntimeException
```

- 任意校验失败抛 `SandboxViolationException`，Tool 执行终止
- 异常信息（message）为可读的校验失败原因，经既有 ToolExecutor 失败审计路径写入 `tool_invocations`（success=false、error_message），回填给模型
- 沙箱拒绝与沙箱放行走完全同一条后续路径，唯一差别是 success 值——不为沙箱单独新增处理逻辑

## 实现形态约束（核心阶段唯一实现 WhitelistSandbox）

- 对外唯一入口 `enforce(SandboxAction)`；三个校验方法（checkFilePath / checkShellCommand / checkHttpUrl）为 private，不暴露在接口上——否则接口被这一档实现带偏
- 校验规则：
  - **文件路径**: 目标 `Path.of(raw).normalize().toAbsolutePath()` 后必须以某允许根为前缀（根在构造时同样转绝对，相对配置按 cwd 解析）——防 `../` 穿越爬出白名单目录
  - **shell 命令**: `command.trim().split("\\s+")[0]` 取首 token（容忍前导空白）与允许命令集比对
  - **HTTP 域名**: `URI.create(url).getHost()` 解析 host；`*.example.com` 通配符必须带点号边界（`.example.com` 结尾才命中），形似域名 evil-example.com 不得命中
- 白名单配置为空 = "什么都不允许"，而非"不校验"

## 配置键契约

见 [config-contract.md](./config-contract.md)。

## 升级路径（接口不变）

| 阶段 | 实现 | 升级信号 |
|------|------|---------|
| 核心阶段 | WhitelistSandbox（应用层白名单） | — |
| 扩展一 | 容器隔离（namespace + cgroups + seccomp） | 相对不可信代码 / 多租户 |
| 扩展二 | microVM（Firecracker / Kata / gVisor） | 完全不可信代码 / 规模化多租户 |

升级 = 新增实现类，不改接口、不改调用方（FileTools/ShellTools/HttpTools/NotifyTools 的 enforce 调用一行不动）。
