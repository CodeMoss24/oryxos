# Implementation Plan: 动态管理 Agent(一句话生成、上传即上线、免重启)

**Branch**: `030-lesson30-agent-lifecycle` | **Date**: 2026-08-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-agent-lifecycle/spec.md`

## Summary

第 30 节把"一个 Agent = 一个目录"实时化:AgentLifecycleService 编排 13 个 REST 端点的创建/查询/更新/删除/生成/会话/记忆;WorkspaceWatcher 用 JDK WatchService 实时监听 `.oryxos/agents/`(启动全量扫 + 之后监听),与 API 创建走**同一段 `register(agentDir)`**;per-agent 记忆(Markdown 档从全局 `MEMORY.md` 改为 `agents/<name>/MEMORY.md`,靠 ToolExecutionContext ThreadLocal 传递 agentName);管理台从只读升级为"Agent 列表 + 详情 5 tab(基本信息/生成/文件/会话/记忆)"。全部复用 26/27/29 节交付,新增编排者 + 监听器 + 端点 + 前端页。

## Technical Context

**Language/Version**: Java 21(JDK 21 + Spring Boot 3.x;虚拟线程,`spring.threads.virtual.enabled=true` 已配)

**Primary Dependencies**: Spring Web(`AgentApiController` 扩展 + `WorkspaceApiController`)、JDK 内置 `java.nio.file.WatchService`(WorkspaceWatcher,无新增第三方依赖)、SnakeYAML(`AgentLoader.parseAgentMd` 已有)、Spring AI Alibaba(仅 `ProviderService.chat` 路径,禁自动 tool 执行)

**Storage**: 文件系统为主——`.oryxos/agents/<name>/`(Agent 目录)、`.oryxos/archive/`(删除归档,运行时创建)、`agents/<name>/MEMORY.md`(per-agent 记忆);SQLite 不新增表(`llm_calls`/`tool_invocations`/`sessions` 复用,审计 day one 由 `ProviderService.chat` 与 `ToolExecutor` 既有路径落库)

**Testing**: JUnit 5 + Mockito;`AgentLifecycleServiceTest`/`WorkspaceWatcherTest`/`GenerateTest`(core 或 memory 模块)、`WorkspaceApiControllerTest`/`AgentApiControllerTest`(web,standalone MockMvc 或 @WebMvcTest);per-agent 记忆/固定会话回归;`mvn clean verify` 全绿(含 P3C/SpotBugs/FindSecBugs/PMD)

**Target Platform**: Linux(WSL2 开发);单进程 Spring Boot 应用

**Project Type**: 多模块单体(9 个 Maven 模块)

**Performance Goals**: 无吞吐指标(核心阶段单实例);目录监听实时性 = WatchService 事件循环毫秒级,免重启

**Constraints**: 语法禁区(P3C/ASM 不解析 Java 18+ 增强 switch `default ->` 写法);`session_id` 只在 `SessionManager` 内拼接;无 Reactor/`CompletableFuture`/自建线程池(WorkspaceWatcher 守护线程与 `AgentScheduler` 同类,课件明示不违反宪法七);防目录穿越(`normalize()` 后 `startsWith(oryxosRoot)`)

**Scale/Scope**: ~10 个 Agent(演示规模);单实例无分布式需求

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 核查 | 结论 |
|------|------|------|
| I JDK 21 + Boot 3 单体,9 模块 | 只加类不改模块划分;`AgentLifecycleService`/`AgentStore`/`WorkspaceWatcher`→oryxos-core,Controller/DTO→oryxos-web | ✅ 不违反 |
| II 五大能力优先,治理层扩展 | 认证/上传/状态位/版本历史全部"先别做"(课件边界),不引入治理层 | ✅ 不违反 |
| III 自实现 ReAct | 不动 ReActLoop;新增编排者调既有 `AgentService` | ✅ 不违反 |
| IV Spring AI 只用一半 | `generate`/`generate-files` 走 `ProviderService.chat`(既有、禁自动 tool 执行);无任何新 Spring AI Agent 抽象 | ✅ 不违反 |
| V 插件 tool 三档 | 不新增 tool 类型;`save_memory`/`recall_memory` 复用,仅落点 per-agent 化 | ✅ 不违反 |
| VI SQLite + MEMORY.md | per-agent 记忆仍是 Markdown 文件(目录从全局改 `agents/<name>/`),不新增表;`LongTermMemoryStore` SPI 契约不变 | ✅ 不违反 |
| VII 审计 day one | 生成链路落 `llm_calls`(`ProviderService.chat` 内建);工具执行落 `tool_invocations`(既有);删除归档不物理删,历史审计可追溯 | ✅ 不违反 |
| VIII 接口先行 | `LongTermMemoryStore` 三档后端契约测试不动;`SessionManager`/`ToolRegistry` 签名不变 | ✅ 不违反 |
| IX 每 story 可演示 | 13 端点 + 管理台 5 tab 皆可演示;真链路人工验收项单列 | ✅ 不违反 |
| H4⑤ 无自建线程池 | `WorkspaceWatcher` 守护线程 = `AgentScheduler` 调度线程同类基础设施线程(课件 §2.1 明示"不违反宪法七") | ✅ 不违反 |

**无违规项,不需要 Complexity Tracking 表。**

## Project Structure

### Documentation (this feature)

```text
specs/012-agent-lifecycle/
├── plan.md              # 本文件
├── research.md          # Phase 0:关键决策(per-agent 记忆、监听架构、脚手架模板)
├── data-model.md        # Phase 1:实体与字段(AgentView/FileNode/DTO/请求响应形状)
├── quickstart.md        # Phase 1:验证指引(harness 判定 + 人工验收)
├── contracts/           # Phase 1:REST 契约(13 端点 + 错误码 + 固定会话/记忆口径)
└── tasks.md             # Phase 2(由 /speckit-tasks 生成)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── agent/
│   ├── AgentLifecycleService.java   # 编排:create(含回滚)/register(agentDir)/get/update/delete/generate 族
│   ├── AgentStore.java              # 写/归档/删 Agent 目录 + 脚手架模板 + 解析防穿越
│   ├── WorkspaceWatcher.java        # WatchService 守护线程:全量扫 + 实时监听 agents/
│   ├── AgentView.java               # Agent 可读视图(供 web 层 DTO 复用,core 内定义)
│   └── FileNode.java                # 目录树节点
├── scheduler/AgentScheduler.java    # [修改] +unregisterProfile
├── runtime/ToolExecutionContext.java # ThreadLocal<String> agentName
└── react/ToolExecutor.java          # [修改] 执行前入 agentName、执行后清(只读路径除外)

oryxos-memory/src/main/java/com/oryxos/memory/
├── MemoryServiceImpl.java           # [修改] buildContext/readAll 委托 store 前后临时置入 agentName
└── MarkdownMemoryStore.java         # [修改] 有 agentName → agents/<name>/MEMORY.md;无 → 回退全局路径

oryxos-web/src/main/java/com/oryxos/web/
├── controller/AgentApiController.java   # [修改] +create/get/update/delete/memory/session/session-messages/generate-files/files
├── controller/WorkspaceApiController.java # 新增 tree/file(GET/POST,防穿越)
├── controller/MemoryApiController.java  # [删除] 全局 /api/v1/memory 移除(5.2.1)
└── dto/
    ├── AgentView.java                 # [移动?] 见下——AgentView 定位于 oryxos-web dto(课件交付物),core 只回 Profile
    ├── CreateAgentRequest.java / UpdateAgentRequest.java / GenerateFilesRequest.java
    ├── SaveFilesRequest.java / FileContentRequest.java / SessionView.java(复用既有)
    └── FileNode.java                  # web 层树节点(或 core 定义 web 引用,按依赖方向定)
```

> **AgentView/FileNode 依赖方向裁决(与课件交付物对齐)**:`AgentView` 与 `FileNode` 是 web 层 DTO(课件落位表只把 `AgentLifecycleService` 放 core);core 的 `AgentLifecycleService` 暴露 `Profile` 与树构建结果,web 层 `AgentApiController`/`WorkspaceApiController` 负责转 `AgentView`/`FileNode`。避免 core → web 反向依赖。

```text
oryxos-boot/src/main/resources/application.yaml   # [修改] +oryxos.author.provider/model
docs/prompt/prompt.md                            # [新增] AGENT_AUTHOR_PROMPT 系统提示词
oryxos-web/src/main/frontend/src/App.vue          # [修改] 管理台:Agent 列表 + 新建 + 详情 5 tab
```

**Structure Decision**: 严守 9 模块边界:编排/监听/存储落 core(不依赖 web);per-agent 记忆只改 memory 模块既有类(SPI 不动);端点与 DTO 落 web;配置与前端各自落位。`WorkspaceApiController` 树构建逻辑留在 web 层(纯文件枚举,不引 core 新依赖)。

## Complexity Tracking

> 无 Constitution 违规,本表不填。
