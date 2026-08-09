# Implementation Plan: Notify 通知模块

**Branch**: `019-lesson19-Notify` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-notify-module/spec.md`

## Summary

为 OryxOS 补上出站通知能力（Notify 模块）。入站有 Channel Adapter 负责"消息怎么进来"，出站需要对称的 NotifyChannelAdapter 负责"结果怎么主动送出去"。核心阶段只实现通用 HTTP webhook 推送（WebhookNotifyAdapter），接口先行设计确保扩展阶段新增企业微信/飞书/钉钉专用 Adapter 时签名不变。实现代码已就位（NotifyChannelAdapter 接口、WebhookNotifyAdapter、NotifyTools、Profile.NotifyChannel），本节重点是补齐课件验收 harness 要求的测试。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, java.net.http.HttpClient (JDK built-in), OkHttp MockWebServer (test), JUnit 5, Mockito

**Storage**: N/A（本模块无新表——Notify 审计走已有 tool_invocations 表）

**Testing**: JUnit 5 + Mockito + MockWebServer（OkHttp mockwebserver 3.x）

**Target Platform**: Linux server (JDK 21+)

**Project Type**: Maven 多模块单体应用

**Performance Goals**: Webhook 发送在 30s 超时内完成；单元测试秒级跑完

**Constraints**: 不引入新第三方 HTTP 客户端（用 JDK built-in HttpClient）；Sandbox.enforce 必须先于 HTTP 请求执行；避开 P3C/ASM 不兼容的 Java 18+ 语法

**Scale/Scope**: 核心阶段 1 个接口 + 1 个实现 + 1 个内置 Tool + Profile 扩充 1 个字段；2 个测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 状态 | 说明 |
|---|------|------|------|
| I | JDK 21 + Spring Boot 3.x 单体 | ✅ | 所有代码在 oryxos-tool 模块内，不新增模块 |
| II | 五大核心能力优先 | ✅ | Notify 属核心能力四（Tool 体系），无治理层内容 |
| III | 自实现 ReAct Loop | ✅ | NotifyTools 实现 OryxTool 接口，由 ReActLoop+ToolExecutor 调度 |
| IV | Spring AI 使用边界 | ✅ | NotifyTools 不使用 Spring AI 自动 tool 执行 |
| V | Plugin Tool 三档接入 | ✅ | Notify 是内置 Tool；扩展阶段 MCP 方式二与 notify 并存 |
| VI | SQLite + MEMORY.md | ✅ | 无新表，审计走已有 tool_invocations |
| VII | 审计 Day One 落库 | ✅ | Notify 工具执行由 ToolExecutor 统一写入 tool_invocations |
| VIII | 接口先行 | ✅ | NotifyChannelAdapter 接口不携带渠道细节 |
| IX | 可演示 Demo | ✅ | Notify 是天气/日报 Demo 的出站依赖 |

**Gate Result**: 全部通过，无需违规记录。

## Project Structure

### Documentation (this feature)

```text
specs/005-notify-module/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-tool/
├── src/main/java/com/oryxos/tool/notify/
│   ├── NotifyChannelAdapter.java    # 接口 + NotifyTarget 内嵌 record（已存在）
│   ├── WebhookNotifyAdapter.java    # 核心阶段唯一实现（已存在）
│   └── NotifyTools.java             # notify 内置 Tool（已存在）
├── src/test/java/com/oryxos/tool/notify/
│   ├── WebhookNotifyAdapterTest.java  # 第一批测试（本节补）
│   └── NotifyToolsTest.java           # 第二批测试（本节补）

oryxos-core/
├── src/main/java/com/oryxos/core/profile/
│   └── Profile.java                 # NotifyChannel record + notifyChannels 字段（已存在）
├── src/main/java/com/oryxos/core/profile/
│   └── ProfileContext.java          # ThreadLocal（17 节已交付）
└── src/main/java/com/oryxos/core/tool/
    ├── OryxTool.java                # Tool 抽象接口（17 节已交付）
    └── ToolResult.java              # 执行结果 record（17 节已交付）

oryxos-tool/
├── src/main/java/com/oryxos/tool/sandbox/
│   ├── Sandbox.java                 # Sandbox 接口 + SandboxAction + ActionType（已存在）
│   ├── WhitelistSandbox.java        # 白名单实现（已存在）
│   └── SandboxViolationException.java # 违规异常（已存在）
```

**Structure Decision**: 全部通知相关代码（接口、实现、NotifyTools）集中在 `oryxos-tool` 模块的 `com.oryxos.tool.notify` 包，符合 CLAUDE.md 定义的"三合一模块"落位规则。Profile 的 NotifyChannel 字段已在 oryxos-core 的 Profile 类中。

## Complexity Tracking

> 本节不涉及 Constitution 违规，无复杂度跟踪项。
