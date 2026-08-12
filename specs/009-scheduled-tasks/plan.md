# Implementation Plan: 定时任务模块（第三种触发源）

**Branch**: `009-lesson25-scheduled-tasks` | **Date**: 2026-08-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-scheduled-tasks/spec.md`

## Summary

给 AgentService 补第三条触发路径"钟推"：新增 `ScheduleConfig`（id/cron/zone/message，独立类，替代无 id 的草图 `Profile.Schedule`）与 `AgentScheduler`（基于 Spring `TaskScheduler` + `CronTrigger` 动态注册，不用静态 `@Scheduled`），`AgentLoader` 补 frontmatter `schedules` 解析打通配置驱动。既有 `AgentScheduler` 早期草图（16/17 节时期）按课件骨架改造：`runOnce` 改走 `SessionManager.getOrCreate("scheduler","scheduler",profileName)`（会话身份固定，session_id 拼接归 SessionManager）、锁按任务 id 键、删除运行时增删接口（扩展阶段）与手拼 sessionId。测试按课件"验收 harness"落地 `AgentSchedulerTest` 一个类四个坑。

## Technical Context

**Language/Version**: JDK 21（Spring Boot 3.x）；语言形态受构建门禁约束（P3C/ASM），避开 Java 18+ 增强 switch 的 `default ->` 写法

**Primary Dependencies**: Spring `spring-context` 6.1.14 自带的 `TaskScheduler`/`ThreadPoolTaskScheduler`/`CronTrigger`/`SimpleTriggerContext`（oryxos-core 依赖中已有，本机 jar 已核实构造器与 getter，见 research.md）；测试 JUnit 5 + Mockito `ArgumentCaptor`（`spring-boot-starter-test`，oryxos-core pom 已有）；无新增第三方依赖

**Storage**: 无新增持久化——本节不落 `scheduled_tasks`/`task_executions`（第 28 节补齐，spec 边界已声明）；不新建表、不动建表脚本

**Testing**: 课件"验收 harness"：`AgentSchedulerTest` 一个类覆盖四坑（注册参数带 cron+时区 / 锁被占跳过 / 异常不外抛且 finally 放锁 / 会话三元组固定），其中两个最值钱回归原样落地（锁被占 `verify(never).process`、异常后二进宫 `verify(times(2)).process`）；`AgentLoaderTest` 补 schedules 解析用例（改造的 loader 的回归）；单测默认跑，无 @Tag("integration") 项；实现完成的定义是 `mvn clean verify` 全绿

**Target Platform**: Linux（WSL2 开发环境）；模块落位 oryxos-core（scheduler 包）

**Project Type**: 单体 Maven 多模块（9 模块）内的 oryxos-core 模块增强

**Performance Goals**: 无性能目标——触发路径是薄层（拿锁 → 拼消息 → 交 AgentService），调度能力本身是 Spring 的

**Constraints**: 锁为进程内 `ReentrantLock`（按任务 id 键），不用分布式锁；失败只记日志不外抛；不新增 Profile 字段之外的配置键；`session_id` 拼接只发生在 `SessionManager` 内部（H4 不变量④）；不改 ReActLoop/ToolExecutor/ProviderService 一行；不新增模块

**Scale/Scope**: 两个新类（`ScheduleConfig` record + `AgentScheduler` 改造）、`Profile` 类型微调（`Schedule` → `ScheduleConfig`）、`AgentLoader` 加一段解析、两个测试类、boot 无改动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查结果 |
|------|---------|
| I 单体架构 | ✅ 不新增模块，全部落在 oryxos-core |
| II 五大能力优先 | ✅ 定时任务是"钟推"触发源，属核心能力（Web Service 的入口之一/第三种触发源），不引入治理层 |
| III/IV 自实现 ReAct、Spring AI 边界 | ✅ 不触碰循环引擎；复用既有 AgentService.process 入口 |
| VII 审计 day one 落库 | ✅ 定时触发走 agentService.process 内部既有 llm_calls/tool_invocations 审计路径，不为钟推单独开逻辑 |
| VIII 接口先行 | ✅ AgentScheduler 依赖 TaskScheduler（Spring 抽象）与 SessionManager（既有接口），无新接口；Sandbox 检查点不适用（本节无涉外 IO） |
| 技术约束·定时机制 | ✅ 用 Spring TaskScheduler + CronTrigger 动态注册，不用静态 @Scheduled；并发用进程内锁不用分布式锁 |
| 技术约束·会话身份 | ✅ channel/user 固定 "scheduler"，经 SessionManager.getOrCreate 获取，不手拼 session_id |
| 运行环境·敏感配置 | ✅ schedules 是普通配置非敏感凭证，无明文 key 问题 |

**门禁结论**: 通过，无违规项（Phase 0/1 无需回填 Complexity Tracking）

## Project Structure

### Documentation (this feature)

```text
specs/009-scheduled-tasks/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出（H3 依赖核实 + 六个设计决策）
├── data-model.md        # Phase 1 输出（无持久化 + ScheduleConfig 值对象 + frontmatter 契约）
├── quickstart.md        # Phase 1 输出（验收/回归运行指南）
├── contracts/           # Phase 1 输出（scheduler-contract + config-contract）
└── tasks.md             # Phase 2 输出（/speckit-tasks，本命令不创建）
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/scheduler/
├── ScheduleConfig.java       # 新增：record(String id, String cron, String zone, String message) + zoneId() 访问器
└── AgentScheduler.java       # 改造：按课件骨架重构（注入 TaskScheduler/SessionManager、runOnce(Profile,ScheduleConfig)、锁按任务 id、删除手拼 sessionId 与运行时增删）

oryxos-core/src/main/java/com/oryxos/core/profile/
├── Profile.java              # 改造：内嵌 record Schedule(cron,zone,message) → 引用 ScheduleConfig（类型换、getter/setter 形状不变）
└── AgentLoader.java          # 改造：deriveProfile 补 frontmatter schedules 解析（{id,cron,zone,message} 列表）

oryxos-core/src/test/java/com/oryxos/core/scheduler/
└── AgentSchedulerTest.java   # 新增：harness 四坑（含两个最值钱回归原样落地）

oryxos-core/src/test/java/com/oryxos/core/profile/
└── AgentLoaderTest.java      # 改造：补 schedules 解析用例（完整/缺 id/缺 zone/多条）
```

**Structure Decision**: 单一包结构——scheduler 包内新增 ScheduleConfig + 改造 AgentScheduler（课件落位表"AgentScheduler/ScheduleConfig→oryxos-core"）；Profile 只改类型引用（Profile.Schedule 被 ScheduleConfig 替代），不动字段语义；测试类按既有包结构落位。不新建模块、不新建子模块目录。

## Phase 0 / Phase 1 产物

- `research.md` — H3 依赖核实（CronTrigger 构造器/getExpression/无 zone getter、equals 只比 expression、SimpleTriggerContext 构造器、TaskSchedulingAutoConfiguration 自动装配 bean）+ 六个设计决策（ScheduleConfig 落位、TaskScheduler 注入、时区行为断言、非法 cron 不阻断启动、frontmatter 解析规则、删除草图运行时增删）
- `data-model.md` — 无持久化说明 + ScheduleConfig 值对象 + frontmatter `schedules` 配置契约
- `contracts/scheduler-contract.md` — AgentScheduler 行为契约（注册/并发/失败/会话身份）
- `contracts/config-contract.md` — frontmatter `schedules` 配置契约（键、缺省语义、非法处理）
- `quickstart.md` — 验收/回归运行指南（mvn 命令 + 人工验证步骤）

## Complexity Tracking

无（Constitution Check 零违规，无需 justify）
