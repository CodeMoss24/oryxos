# 实施计划：ReAct 循环引擎

**分支**: `017-lesson17-react` | **日期**: 2026-08-08 | **规格**: [spec.md](./spec.md)

**输入**: `/specs/003-react-loop/spec.md` 功能规格说明书

## 摘要

实现 ReAct（Reasoning + Acting）循环引擎——OryxOS Agent 的核心调度中枢。循环接收 Session 和用户消息，内部 for 循环最多 maxIterations 次（默认 10），每轮依次组装 Prompt → 调 Provider → 判断是否有 Tool 调用 → 无则返回最终文本 → 有则逐条执行 Tool → 结果回填 Session → 继续。配套交付 PromptBuilder（四部分 Prompt 拼装 + 历史截断）、ToolExecutor（工具调度 + tool_invocations 审计落库）、AgentService（三种触发源统一编排入口 + ProfileContext 生命周期管理）、ContextLoader（Bootstrap 加载 + 无缓存设计）。

## 技术上下文

**语言/版本**: JDK 21（禁止使用 P3C/ASM 解析不了的 Java 18+ 语法形态，如增强 switch 的 `default ->` 写法）

**主要依赖**: Spring Boot 3.x、Spring AI Alibaba（仅 Provider 抽象 + 协议转换 + @Tool schema 生成，禁用自动 tool 执行）、SQLite + Spring Data JPA

**存储**: SQLite（手工建表脚本 `schema.sql`，不依赖 `hibernate.ddl-auto=update`）+ 文件系统（`.oryxos/` Bootstrap 文件）

**测试**: JUnit 5 + Mockito（单测 mock ProviderService/ToolRegistry/MemoryService，不碰网络）；`mvn clean verify` 全绿即完成（含 P3C/SpotBugs/FindSecBugs/PMD 静态检查）

**目标平台**: Linux x86-64（Ubuntu 22.04+ / Debian 11+ / Alibaba Cloud Linux 3 / Rocky Linux），WSL2 开发环境

**项目类型**: Maven 多模块 Java 库 / Spring Boot 单体应用

**性能目标**: 单次 ReAct 循环迭代不增加显著延迟（PromptBuilder 组装 + ToolExecutor 执行在毫秒级完成）；审计写入异步不阻塞主流程（try-catch 包裹，落库失败只记日志不中断循环）

**约束**: 凭证走环境变量占位，不落明文；SQLite 手工建表脚本；避开 P3C/ASM 不兼容语法

**规模/范围**: 6 个核心类 + 5 个测试类 + 1 张数据库表 + 建表脚本补行；代码行数约 500 行（含测试）

## 宪法合规检查

*门禁：Phase 0 研究前必须通过。Phase 1 设计后重新检查。*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. JDK 21 + Spring Boot 3.x 单体 | ✅ PASS | 全部新增代码在现有 Maven 多模块中，不拆新模块 |
| II. 五大核心能力优先 | ✅ PASS | ReAct 循环是核心能力之二，治理层能力全部放扩展 |
| III. 自实现 ReAct Loop | ✅ PASS | ReActLoop 约 50 行，不依赖 Spring AI Agent 抽象 |
| IV. Spring AI 使用边界 | ✅ PASS | ProviderService 已设 `withProxyToolCalls(false)`，Tool 调度由 ToolExecutor 控制 |
| V. Plugin Tool 三档接入 | ✅ PASS | 本节不涉及 Plugin Tool 接入方式 |
| VI. SQLite + MEMORY.md | ✅ PASS | tool_invocations 表用手工建表脚本 |
| VII. 审计 Day One 落库 | ✅ PASS | ToolExecutor 每次执行都写 tool_invocations（成功/失败都记） |
| VIII. 接口先行 | ✅ PASS | 本节修改的接口（ToolExecutor 签名增加 sessionId）与既有抽象一致 |
| IX. 可演示 Demo | ✅ PASS | 本节完成后可配合 ProviderService 做对话版 Demo |

**门禁结果**: 全部 PASS，无违规项。

## 项目结构

### 文档（本节 feature）

```text
specs/003-react-loop/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
└── tasks.md             # /speckit-tasks 命令输出（非 plan 阶段产出）
```

### 源代码（仓库根目录）

```text
oryxos-core/src/main/java/com/oryxos/core/
├── react/
│   ├── ReActLoop.java          # [改造] 循环引擎（约 50 行，签名对齐课件）
│   ├── PromptBuilder.java      # [改造] Prompt 拼装（四部分顺序 + 历史截断）
│   ├── ToolExecutor.java       # [改造] 工具执行 + 审计落库（补 TODO）
├── AgentService.java           # [改造] 统一编排入口（补 Session 持久化）
├── profile/
│   └── ProfileContext.java     # [已有] ThreadLocal Profile 上下文（完整）
├── context/
│   └── ContextLoader.java      # [改造] Bootstrap 加载（补 WARN 日志）

oryxos-storage/src/main/java/com/oryxos/storage/
├── entity/
│   └── ToolInvocationEntity.java   # [已有] tool_invocations JPA 实体（完整）
├── repository/
│   └── ToolInvocationRepository.java  # [已有] Spring Data JPA Repository（完整）
└── resources/
    └── schema.sql                   # [改造] 补 tool_invocations 建表 DDL

oryxos-core/src/test/java/com/oryxos/core/
├── react/
│   ├── ReActLoopTest.java       # [新建] 循环引擎 4 个关键回归测试
│   ├── PromptBuilderTest.java   # [新建] Prompt 拼装 3 个关键回归测试
│   ├── ToolExecutorTest.java    # [新建] 工具执行审计 2 个关键回归测试
├── AgentServiceTest.java        # [新建] 编排 + ProfileContext 泄漏测试
├── context/
│   └── ContextLoaderTest.java   # [新建] 上下文加载 2 个关键回归测试
```

**结构决策**: 全部改动落在 `oryxos-core` 和 `oryxos-storage` 两个模块。ReAct 相关类已在 `oryxos-core` 的 `react` 包下（16 节创建），本节在其上增强；`ToolInvocation` 实体和 Repository 已在 `oryxos-storage` 下（已创建但未接入审计链路）。不新建模块，不修改其他模块的公共接口。

## 复杂度追踪

> 无宪法违规项需要说明。

## 当前代码差距分析

基于对现有代码的审查，以下是需要填补的差距：

### G1. ToolExecutor 未写审计

`ToolExecutor.execute()` 有 TODO 注释，未注入 `ToolInvocationRepository`，未写 `tool_invocations` 记录。需：
- 注入 `ToolInvocationRepository`
- 成功路径写 `success=true`
- 失败路径写 `success=false` + `errorMessage`
- 异常不吞（catch 后审计写入，再上抛或返回错误消息）
- 增加 `sessionId` 参数以关联审计记录

### G2. schema.sql 缺少 tool_invocations 表

当前 `schema.sql` 只有 `llm_calls` 建表语句，需补 `tool_invocations` 表的 DDL。

### G3. AgentService 未持久化 Session

`AgentService.process()` 缺少 `sessionManager.save(session)` 调用。SessionManager 接口尚未创建——需确认前序节是否已定义。若未定义，本节创建最小接口。

### G4. ContextLoader 缺少 WARN 日志

Bootstrap 文件缺失时 `readIfExists()` 静默返回 null，未打 WARN。需增加 `log.warn()`。

### G5. 无测试覆盖

5 个测试类均不存在，需从零编写。课件验收 harness 指定了 2 个关键回归测试（死循环兜底、ProfileContext 泄漏），需原样落地。

### G6. ReActLoop 与课件骨架差异

课件骨架用 `toolExecutor.execute(session.id(), call)` 传 sessionId，当前实现传 `profile`。需对齐为传 sessionId 以支持审计关联。

## Phase 0: 研究结论

见 [research.md](./research.md)

## Phase 1: 设计产出

见 [data-model.md](./data-model.md)、[quickstart.md](./quickstart.md)