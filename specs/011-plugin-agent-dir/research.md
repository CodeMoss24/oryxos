# Phase 0 Research: Plugin Agent Directory

**Date**: 2026-08-16 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本节技术上下文已由课件 §1–2 与 TechnicalSolution §11.1–11.2 充分给定，且大量底座骨架（AgentLoader/ProfileRegistry/ContextLoader/AgentScheduler）已在前序节落地。研究聚焦于"在前序骨架上补本节四样"时需拍板的实现决策，每条给出 Decision / Rationale / Alternatives。

---

## R1. ProfileRegistry 并发结构选型

- **Decision**: `LinkedHashMap` → `java.util.concurrent.ConcurrentHashMap`。`register/remove/exists/find/list` 签名不变（既有 `AgentLoaderTest`/`AgentSchedulerTest` 调用方零改动）。
- **Rationale**: FR-005 要求"可变并发结构"。`ConcurrentHashMap` 满足运行时注册并发可见性，`list()` 返回 `values()` 视图即足够（核心阶段单实例、迭代时不增删的窗口可接受；`registerAll` 启动期单线程、运行时注册是低频事件）。不引入额外锁——`register`/`remove` 单操作原子即可，"两条来源同规矩"靠的是校验逻辑同一段，不是 Map 类型。
- **Alternatives**: ① 保留 LinkedHashMap + `synchronized`：过度串行，且 `list()` 仍需拷贝防 `ConcurrentModificationException`，更绕；② `Collections.synchronizedMap`：整表锁，与 ConcurrentHashMap 比 granularity 差。选 ConcurrentHashMap。

## R2. 缺必填项校验落点与报错契约（FR-006）

- **Decision**: 校验在 `AgentLoader.deriveProfile` 内做（core，不依赖 provider）。必填项 = `name`（目录名传入，必非空）+ `provider.name`（非空）。缺则抛 `IllegalArgumentException`，消息格式：`Agent '<name>': missing required field '<field>'`（点名 Agent + 字段）。`scanAndRegister` 已有 try/catch 包裹，单 Agent 失败记 `System.err` 不阻断其余（与既有坏文件策略一致）。**运行时注册路径调同一段 `deriveProfile`，故同一异常类型 + 同一消息**（FR-005 的"两条来源同规矩"由代码同源保证，harness `ProfileRegistryRuntimeTest` 钉死）。
- **Rationale**: 校验必填项是纯 frontmatter 结构检查，不碰 provider 模块，落 core 干净。报错点名满足课件"缺 name/provider → 加载报错点名"。
- **Alternatives**: ① 校验放 ProfileRegistry.register：但 register 收的是已派生 Profile，frontmatter 结构错误此时已无法定位字段，不如在派生处校验；② 校验放装配层 boot：则 core 内"两条来源同规矩"无法用同一段代码保证。选 deriveProfile 内。
- **tools 未注册告警**：`deriveProfile` 不持有 ToolRegistry（避免给 core 注入循环依赖——ToolRegistry 虽在 core，但 AgentLoader 当前无它）。**Decision**：tools 告警在装配层扫描循环里做（boot 的 `scanAndRegister` 调用点或一个 core 内的 `AgentValidator` 工具方法接收 ToolRegistry）。采用**在 oryxos-core 加一个轻量 `AgentValidator`（或 AgentLoader 上加 `validateTools(Profile, ToolRegistry)` 方法）**，boot 扫描后调、告警不阻断。这样校验逻辑仍归 core、可单测，且 AgentLoader 不在构造期硬依赖 ToolRegistry（方法参数注入）。
  - 最终落法（最小侵入）：`AgentLoader` 加方法 `warnUnregisteredTools(Profile, ToolRegistry)`（包级或 public，返回 void，内部 log.warn），boot 扫描循环每个 Agent 调一次。harness `AgentLoaderTest` 用内存 ToolRegistry 验证告警路径。

## R3. AgentScheduler.registerProfile + 句柄表设计

- **Decision**: 把 `registerAll` 的"遍历 profile.schedules → 注册单条"循环体抽成 `registerProfile(Profile)`（public）。`registerProfile` 内对每条 `ScheduleConfig`：幂等检查（`scheduledTaskIds`）→ `CronTrigger` schedule → `store.register` → `taskRefs.put` + **新增 `scheduledTasks.put(sc.id(), future)`**。`ScheduledFuture<?>` 由 `taskScheduler.schedule(runnable, trigger)` 返回值捕获。
- **Rationale**: FR-005 要求句柄表供下节注销/更新。`taskScheduler.schedule(...)` 返回 `ScheduledFuture`，存进 `Map<String, ScheduledFuture<?>> scheduledTasks`（`ConcurrentHashMap`，与既有 `taskLocks`/`taskRefs`/`scheduledTaskIds` 并存）。`registerAll` 改为 `for profile: registerProfile(profile)`。runOnce/runNow/setEnabled 等既有方法不动。
- **Alternatives**: ① 不抽 registerProfile、句柄表直接在 registerAll 内填：则运行时注册无法复用同一段，违背 FR-005；② 用 `taskRefs` 复合存句柄：污染既有 record 语义。独立 `scheduledTasks` Map 最清晰。
- **注意**：`taskScheduler.schedule(Runnable, Trigger)` 在 Spring 的 `TaskScheduler` 接口上返回 `ScheduledFuture<?>`（核实点——见 H3 写前核实，Mockito mock 的 `TaskScheduler` 在测试里 `when(taskScheduler.schedule(...)).thenReturn(mock(ScheduledFuture.class))`）。

## R4. 资源路径识别（scripts/skills/REFERENCE.md）

- **Decision**: `AgentLoader.parseAgentMd` 已拆 frontmatter/正文。补一个 `AgentDirResources`（record，承载 `scriptsDir`/`skillsDir`/`referenceFile` 三个 `Path`，存在则非 null）由扫描时对目录探测生成。**是否要把它绑进 Profile？** 课件 §2.1 说"记住 scripts/、skills/、REFERENCE.md 等资源所在"——但渐进式披露靠正文指引 + 底座 `read_file`/`shell` 取用，Agent 运行时取资源用的是**相对工作区路径**（如 `read_file("skills/report-format.md")`），底座读文件工具自己解析，不需要 Profile 携带绝对路径。
- **Rationale**: 把资源路径塞进 Profile 会让值对象变重且与"渐进式披露靠底座既有能力"的口径冲突。**Decision**：`AgentDirResources` 仅作 harness 可观测对象（`AgentLoaderTest` 断言"认出 scripts/skills/REFERENCE.md"），不进 Profile、不影响运行时。如需最小化，可不引入新 record，直接在 `AgentLoader` 加一个 `listResources(Path agentDir)` 方法返回资源存在性 map 供测试断言——更轻。**选后者（不引入 AgentDirResources record，加 `listResources` 方法）**，避免新增对外概念（软门禁：交付物点名的是"资源路径识别"，不是新类型）。
- **Alternatives**: 引入 `AgentDirResources` record 绑 Profile：过度设计，且偏离渐进式披露口径。否。

## R5. ContextLoader 渐进式披露确认

- **Decision**: 现读 `ContextLoader.loadSystemPrompt(profile, agentMdBody)`——只注 bootstrap + `agentMdBody`（正文）+ 当前时间，**不碰 skills/scripts/REFERENCE.md**。已满足 FR-003"正文进 prompt、其余不预载"。本节对 ContextLoader **无代码改动**，仅 harness `ProgressiveDisclosureTest` 钉死该不变量（构造多文件 Agent 目录，调 loadSystemPrompt，断言 prompt 含正文、不含子指令/参考/脚本内容）。
- **Rationale**: 17 节已立无缓存 + 只注入正文。本节是确认 + harness 固化，不重写。
- **Alternatives**: 给 ContextLoader 加资源感知：违反 FR-003"不预载"。否。

## R6. provider 真实性校验落装配层（boot）

- **Decision**: 在 `OryxOsApplication.run` 的扫描循环后（`agentLoader.scanAndRegister` 之后、`agentScheduler.registerAll` 之前或之后均可，因不阻断），遍历 `profileRegistry.list()`，对每个 Profile 的 `provider.name` 调 16 节既有 `ProviderService` 校验是否映射到已注册 ChatModel；未映射则 `log.warn`（不阻断）。**core 不出现 `import com.oryxos.provider`**（grep 门禁）。
- **Rationale**: clarify 已定 Option B——不反转依赖方向。boot 已依赖 provider（pom 实测），调 ProviderService 干净。
- **Alternatives**: 不做 provider 真实性校验（Option C）：违背 FR-006"provider 必填"的完整性（必填非空在 core、真实性在 boot，两层互补）。否。

---

## NEEDS CLARIFICATION 解析

spec 无 `[NEEDS CLARIFICATION]` 残留（specify 阶段已清空，clarify 阶段补了 R2/R6 的 provider 校验落点）。本 research 的 R1–R6 即所有实现决策，无遗留未知。
