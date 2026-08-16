# Implementation Plan: Plugin Agent Directory (一个目录定义一个会自己跑的 Agent)

**Branch**: `029-lesson29-plugin-agent` | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-plugin-agent-dir/spec.md`

## Summary

第 29 节把"定义一个 Agent"退化成往 `.oryxos/agents/` 丢一个自足目录。底座（16~28 节，全部吃 `Profile`）一行不动，只给 Profile 多加一个来源：从 Agent 目录主文件的 frontmatter 派生 Profile。本节在既有扫描/派生骨架上补四样——**资源路径识别 + 缺必填项校验报错点名 + 运行时注册（索引改可变并发 Map、调度器抽 `registerProfile` + 句柄表）+ harness 六测**——并产出示例 Agent 目录 `daily-reconcile/`（四部分俱全）。provider 真实性校验落装配层 boot，不反转 core→provider 依赖方向。

## Technical Context

**Language/Version**: JDK 21（Spring Boot 3.x 要求）

**Primary Dependencies**: Spring Boot 3.x（`spring-boot-starter` 已在 oryxos-core）、Spring AI Alibaba（Provider 抽象，本节不动）、SnakeYAML（已在 oryxos-core，解析 frontmatter）、Spring `TaskScheduler`（25 节已在用，本节 `registerProfile` 复用）、`@EnableScheduling`（boot 已开）。动手前 `mvn dependency:tree -pl oryxos-core -am` 核实 snakeyaml + spring-context 锁定存在。

**Storage**: SQLite + Spring Data JPA（本节不新增表——`scheduled_tasks`/`task_executions` 28 节已建；Profile 是内存值对象不落库）；`.oryxos/agents/` 文件系统为唯一真相源。手工建表脚本，不用 `hibernate.ddl-auto=update`。

**Testing**: JUnit 5 + AssertJ + Mockito（既有）。六测类默认跑；集成冒烟打 `@Tag("integration")` CI 跳过。实现完成定义 = `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）。

**Target Platform**: Linux 主流发行版（本机 WSL2）。

**Project Type**: Maven 多模块单体（9 模块），单二进制。

**Performance Goals**: 无新增性能面；扫描为启动一次性、运行时注册单 Agent O(1)。

**Constraints**: ① oryxos-core 零内部依赖、不得反向依赖 oryxos-provider（架构硬约束，grep 门禁）；② 不新造 Tool、不做跨 Agent 共享能力库、无全局索引；③ 不启用 Spring AI 自动 tool 执行（宪法 IV）；④ 测试方法名英文、课件原文进 `@DisplayName`；⑤ 避开 P3C/ASM 解析不了的 Java 18+ 语法（如增强 switch 的 `default ->`）。

**Scale/Scope**: 单实例部署；N 个 Agent 目录（核心阶段量级小，并发 Map 足够）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I 单体 9 模块 | 改动落在 oryxos-core（既有类）+ oryxos-boot（装配）+ `.oryxos/`（示例目录），不新增模块 | ✅ 通过 |
| II 五大核心优先 | 本节是"定义 Agent"机制（第 11 章），不引入治理层 | ✅ 通过 |
| III 自实现 ReAct | 不碰 ReActLoop | ✅ 通过 |
| IV Spring AI 边界 | 不碰 Provider 抽象用法、不启用自动 tool 执行；ToolRegistry 查询是自实现路径 | ✅ 通过 |
| V Plugin Tool 三档 | 一个目录=一个 Agent（方式一零代码主推），不新造 Tool | ✅ 通过 |
| VI SQLite + MEMORY.md | 不新增表、不动 ddl-auto；Agent 目录走文件系统 | ✅ 通过 |
| VII 审计 day one | 不动审计表；运行时注册走同一 `AgentService.process`，审计路径不变 | ✅ 通过 |
| VIII 接口先行 | ProfileRegistry/AgentScheduler 既有类加方法，不引入需先定接口的新抽象（运行时注册是既有类的能力扩展，非新隔离抽象） | ✅ 通过 |
| IX 可演示 Demo | daily-reconcile 目录作手动路径参照物，可钟推+人推 | ✅ 通过 |

**依赖方向门禁**：oryxos-core 不得出现 `import com.oryxos.provider`。provider 真实性校验（provider name→ChatModel）落 oryxos-boot。ToolRegistry 在 core，tools 未注册能力告警直接 `toolRegistry.find(name)` 查。**无违反。**

## Project Structure

### Documentation (this feature)

```text
specs/011-plugin-agent-dir/
├── spec.md              # /speckit-specify 产出
├── plan.md              # 本文件
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
└── tasks.md             # /speckit-tasks 产出（下一步）
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── profile/
│   ├── AgentLoader.java          # 改：补资源路径识别 + 缺必填项校验报错点名 + tools 未注册告警
│   ├── Profile.java              # 不改（值对象已齐）
│   ├── ProfileRegistry.java      # 改：LinkedHashMap → ConcurrentHashMap，register/remove/exists 已有签名、确认并发语义
│   └── AgentDirResources.java    # 新（可选 record）：承载 scripts/skills/REFERENCE.md 路径识别结果
├── context/
│   └── ContextLoader.java        # 既有，确认不预载子指令/参考/脚本（只注正文）——无代码改动或仅注释确认
└── scheduler/
    └── AgentScheduler.java       # 改：抽 registerProfile(Profile)，新增 scheduledTasks Map<String,ScheduledFuture<?>>，registerAll 改走 registerProfile

oryxos-boot/src/main/java/com/oryxos/boot/
└── OryxOsApplication.java        # 改（装配层）：扫描后做 provider name→ChatModel 校验（不阻断其余），复用 16 节显式映射

.oryxos/agents/daily-reconcile/   # 新（示例 Agent 目录，四部分俱全）
├── AGENT.md
├── scripts/reconcile.py
├── skills/report-format.md
└── REFERENCE.md

oryxos-core/src/test/java/com/oryxos/core/
├── profile/AgentLoaderTest.java          # 既有，补资源识别 + 缺必填项报错点名用例
├── profile/DeriveProfileTest.java        # 新
├── profile/AgentScanRegisterTest.java    # 新
├── profile/ProfileRegistryRuntimeTest.java # 新
├── scheduler/AgentSchedulerRegisterTest.java # 新
└── context/ProgressiveDisclosureTest.java # 新
```

**Structure Decision**: 选既有多模块布局，本节零新增模块、零新增 Maven 依赖。改动集中在 oryxos-core 的 profile/scheduler/context 既有类 + oryxos-boot 装配层 + `.oryxos/agents/` 示例目录。

## Complexity Tracking

> 无 Constitution 违反需辩护。表空。
