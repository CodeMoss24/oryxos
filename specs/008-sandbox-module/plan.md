# Implementation Plan: Sandbox 沙箱模块

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-sandbox-module/spec.md`

## Summary

把第23节评审定下的"墙"砌成代码：定义不携带实现细节的沙箱抽象（单一入口 + 动作类型四值），实现核心阶段唯一一档应用层白名单校验（文件路径 / shell 命令 / HTTP 域名三类），接进已存在的 FileTools / ShellTools / HttpTools / NotifyTools（含 WebhookNotifyAdapter），校验失败复用既有工具失败审计路径。同时把前序节已交付的沙箱骨架拉回规范形态：动作值对象与动作类型提为顶层类型、配置从 CSV 字符串改为三个独立配置键、修复通配符域名匹配的点号边界漏洞、路径校验补绝对路径标准化。

## Technical Context

**Language/Version**: JDK 21（Spring Boot 3.x 要求）；语言形态受构建门禁约束（P3C/ASM），避开 Java 18+ 增强 switch 的 `default ->` 写法

**Primary Dependencies**: Spring Boot（`spring-boot-starter` 内的 `@ConfigurationProperties`，oryxos-tool pom 已有，无需新增）；测试依赖 JUnit 5 + Mockito（`spring-boot-starter-test`）+ MockWebServer（`com.squareup.okhttp3:mockwebserver:4.12.0` test scope，oryxos-tool pom 已有）；无新增第三方依赖

**Storage**: 无新持久化——沙箱是运行时校验，不新增 SQLite 表；白名单是配置（application.yaml），非数据库数据

**Testing**: 课件"验收 harness"：`WhitelistSandboxTest`（三类校验"允许+拒绝"成对 + 两个绕过回归）+ 四个 Tool 接线回归（白名单外被拦 + 危险动作未发生，副作用断言）；单测默认跑；实现完成的定义是 `mvn clean verify` 全绿

**Target Platform**: Linux（WSL2 开发环境）；模块落位 oryxos-tool（sandbox 包与内置 Tool 同模块，三合一原则）

**Project Type**: 单体 Maven 多模块（9 模块）内的 oryxos-tool 模块增强

**Performance Goals**: 校验为纯内存比较（路径 startsWith / Set.contains / 字符串匹配），单次校验微秒级，无性能目标

**Constraints**: 接口签名不得出现"白名单""容器""镜像"字样（microVM 反向套签名应干净套入）；不得用 SecurityManager（JDK 17 废弃、JDK 21 不可用）；不改 ToolExecutor 审计逻辑；不新增模块；改造前序节工具时只加 enforce 首行与类型引用更新，IO 代码一行不动

**Scale/Scope**: 四个内置 Tool 共 10 个方法接线；一个实现类三类校验；三个配置键；一个测试类 + 四组接线回归

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查结果 |
|------|---------|
| I 单体架构 | ✅ 不新增模块，全部落在 oryxos-tool |
| II 五大能力优先 | ✅ 沙箱是 Tool 能力的组成，属核心能力四；不引入治理层概念 |
| III/IV 自实现 ReAct、Spring AI 边界 | ✅ 不触碰循环引擎与 Spring AI 自动 tool 执行 |
| VII 审计 day one 落库 | ✅ 校验失败复用既有 tool_invocations 失败路径，不新增单独审计 |
| VIII 接口先行 | ✅ 接口单一入口 + 值对象不含实现字样；唯一实现类可被替换为容器/microVM 实现而不改接口 |
| 技术约束·Sandbox 安全策略 | ✅ 仅 WhitelistSandbox 一档；不用 SecurityManager |
| 技术约束·模块结构 | ✅ sandbox 归 oryxos-tool（三合一模块，不拆） |
| 运行环境·敏感配置 | ✅ 白名单是安全配置非敏感凭证，无明文 key 问题（不动现有 key 存储方式） |

**门禁结论**: 通过，无违规项（Phase 0/1 无需回填 Complexity Tracking）

## Project Structure

### Documentation (this feature)

```text
specs/008-sandbox-module/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出（沙箱契约 + 配置契约）
└── tasks.md             # Phase 2 输出（/speckit-tasks，本命令不创建）
```

### Source Code (repository root)

```text
oryxos-tool/src/main/java/com/oryxos/tool/sandbox/
├── Sandbox.java                   # 既有，保持接口形态（新增：值对象/枚举改为引用顶层类型）
├── SandboxAction.java             # 新增：顶层 record（type + target），从 Sandbox 接口内移出
├── ActionType.java                # 新增：顶层 enum（四值），从 Sandbox 接口内移出
├── SandboxViolationException.java # 既有，保持
├── FileSandboxProperties.java     # 新增：@ConfigurationProperties(prefix="file")
├── ShellSandboxProperties.java    # 新增：@ConfigurationProperties(prefix="shell")
├── HttpSandboxProperties.java     # 新增：@ConfigurationProperties(prefix="http")
└── WhitelistSandbox.java          # 改造：构造器改三 props 注入；根路径转绝对；修 matchesDomain 点号边界

oryxos-tool/src/main/java/com/oryxos/tool/
├── builtin/FileTools.java         # 改造：Sandbox.SandboxAction → SandboxAction 引用（enforce 首行已存在）
├── builtin/ShellTools.java        # 改造：同上
├── builtin/HttpTools.java         # 改造：同上
├── notify/NotifyTools.java        # 改造：同上
├── notify/WebhookNotifyAdapter.java # 改造：同上
└── config/ToolConfiguration.java  # 改造：加 @EnableConfigurationProperties(三个 props)（绑定注册 glue）

oryxos-tool/src/test/java/com/oryxos/tool/
├── sandbox/WhitelistSandboxTest.java  # 新增：harness 测试类（三类成对 + 两个绕过回归 + 空白名单）
├── builtin/FileToolsTest.java         # 改造：类型引用 + 接线回归（白名单外写文件不落盘）
├── builtin/ShellToolsTest.java        # 改造：类型引用 + 接线回归（白名单外命令目标文件仍在）
├── builtin/HttpToolsTest.java         # 改造：类型引用 + 接线回归（MockWebServer 请求计数 0）
├── notify/NotifyToolsTest.java        # 改造：类型引用 + 接线回归（真实拒绝沙箱 + mock adapter never）
└── ToolTestFixture.java               # 改造：WhitelistSandbox 构造改 props 形态

oryxos-boot/src/main/resources/application.yaml  # 改造：oryxos.sandbox.* 三键 → 顶层 file.allowed_paths / shell.allowed_commands / http.allowed_domains
```

**Structure Decision**: 单一包结构（sandbox 包与内置 Tool 同模块、同包级），不新建模块、不新建子模块目录。测试类按既有包结构落位（sandbox 测试独立包，接线回归进各 Tool 既有测试类）。

## Phase 0 / Phase 1 产物

- `research.md` — 依赖核实结论 + 三个关键设计决策（配置绑定注册 glue、点号边界修复、相对根转绝对）
- `data-model.md` — 无持久化说明 + 运行时值对象 + 配置契约
- `contracts/sandbox-contract.md` — 沙箱接口契约（单一入口 + 值对象 + 异常语义）
- `contracts/config-contract.md` — 配置契约（三键、语义、空白名单 = 全拒）
- `quickstart.md` — 验收/回归运行指南（mvn 命令 + 人工验证步骤）

## Complexity Tracking

无（Constitution Check 零违规，无需 justify）
