# Quickstart: Sandbox 沙箱模块验收/回归指南

**Feature**: 008-sandbox-module | **Branch**: 024-lesson24-sandbox

## 前置

- 已按 [config-contract.md](./contracts/config-contract.md) 在 application.yaml 配置三个顶层键（或留空验证"空 = 全拒"语义）

## 自动化验收（硬门禁）

```bash
# 全量门禁——实现完成的定义
mvn clean verify

# 只跑本节测试（oryxos-tool 模块）
mvn test -pl oryxos-tool

# 本节关键回归单测（白名单校验 + 穿越 + 通配符点号边界）
mvn test -pl oryxos-tool -Dtest=WhitelistSandboxTest

# 四个 Tool 接线回归（含"IO 没有发生"副作用断言）
mvn test -pl oryxos-tool -Dtest='FileToolsTest,ShellToolsTest,HttpToolsTest,NotifyToolsTest'
```

**预期**: 全部全绿。`WhitelistSandboxTest` 覆盖：三类"允许+拒绝"成对、`/workspace/../../outside/secret.txt` 穿越被拦、`*.example.com` 命中 api.example.com 且拒绝 evil-example.com、空白名单全拒。

> 静态检查注记（2026-08-11 实测，用户确认"维持现状,如实记录"）：PMD/P3C/SpotBugs/Checkstyle 仅在父 pom `pluginManagement` 定义 executions，未在任一模组 `<plugins>` 声明，`mvn clean verify` 不执行静态检查（既有工程事实）；且 `com.alibaba.p3c:p3c-pmd:2.2.0` 在 Maven Central 不存在（实测 404，最新 2.1.1），P3C 从未实际执行。手动执行 `mvn spotbugs:check -pl oryxos-tool` 报 24 bugs（6 个为本节三个 props record 的 EI_EXPOSE_REP/REP2，18 个为 MCP/Notify 等存量代码），未处理，留给工程化收尾节。

## 人工验证（课件"做完怎么验"剩余项）

1. **集成验证（真实链路）**：启动 `oryxos serve`（或 chat），把 `shell.allowed_commands` 配成只允许 `ls`，触发 Agent 跑一条白名单外命令（如 `echo`→ 或让模型调 shell 执行非白名单命令）：
   - 预期：链路抛 `SandboxViolationException`；SQLite `tool_invocations` 新增一条 `success=false` 记录；`error_message` 是人可读的校验原因（如"命令不在白名单内: xxx"）；模型下一轮能看到该失败并换路走
   - 查询：`sqlite3 .oryxos/oryxos.db "select tool_name,success,error_message from tool_invocations order by id desc limit 5;"`
2. **接口中立性自查（思维练习）**：把 `WhitelistSandbox` 换成假想的 `KataMicroVmSandbox` 实现，`Sandbox.enforce(SandboxAction)` 签名需要加方法吗？不需要才算"墙"立住。
3. **配置边界写进文档**：确认配置说明写明"白名单为空 = 什么都不允许"（本项目配置注释/文档已含）。
4. **回归**：改造后的四个 Tool 原有测试全绿（`mvn test -pl oryxos-tool` 即含）。

## 数据模型

无新增表，运行时值对象与配置契约见 [data-model.md](./data-model.md)。
