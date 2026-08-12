# Research: 定时任务模块（第三种触发源）

**Date**: 2026-08-12 | **Feature**: 009-scheduled-tasks

## H3 依赖核实（本机 jar 实证，非记忆）

| 核实项 | 结果 | 影响 |
|--------|------|------|
| `CronTrigger(String, ZoneId)` 构造器 | ✅ 存在于 spring-context 6.1.14 | 时区 + cron 一起传（坑四） |
| `CronTrigger.getExpression()` | ✅ 公开 | 注册参数断言可抓 cron 字符串 |
| `CronTrigger` 的 zone getter | ❌ 无公开 getter | 时区断言不能直接读字段 |
| `CronTrigger.equals()` | ⚠️ 字节码实证只比较 `expression`（经 CronExpression.equals），**不含 zoneId** | **禁用**等值断言验证时区——两个不同时区的 trigger 会误判相等 |
| `SimpleTriggerContext(Instant, Instant, Instant)` 构造器 | ✅ 存在；`TriggerContext.lastScheduledExecution()` 为抽象方法 | 行为断言方案成立（见 D3） |
| `TaskSchedulingAutoConfiguration` 自动装配 | ✅ 在 spring-boot-autoconfigure 的 AutoConfiguration.imports 中注册；`spring.task.scheduling.pool.size` 配置元数据存在 | boot 应用上下文默认有 `ThreadPoolTaskScheduler` bean（bean 名 "taskScheduler"），注入 `TaskScheduler` 可行 |
| oryxos-core pom 测试依赖 | ✅ `spring-boot-starter-test`（JUnit5 + Mockito，含 ArgumentCaptor） | 测试零新增依赖 |
| `AgentLoaderTest` 存在 | ✅ `oryxos-core/src/test/java/com/oryxos/core/profile/AgentLoaderTest.java` | schedules 解析回归加进既有测试类 |

## 设计决策

### D1: `ScheduleConfig` 落位独立类，替代 `Profile.Schedule`

- **Decision**: 新增 `com.oryxos.core.scheduler.ScheduleConfig` record（`id`/`cron`/`zone`/`message` + `zoneId()` 访问器），`Profile.schedules` 字段类型改为 `List<ScheduleConfig>`，删除 `Profile.Schedule`（cron/zone/message 无 id 的草图形态）
- **Rationale**: 课件交付物点名 `ScheduleConfig`（id/cron/zone/message）；锁按任务 id 键（clarify 已定：id 全局唯一、操作者责任）；第 28 节 `scheduled_tasks.task_id` 主键直接对接
- **Alternatives considered**: 保留内嵌 record 加 id——类名与课件不符；同时保留两个类——重复概念

### D2: `TaskScheduler` 构造器注入（Spring 自动装配 bean），不自己 new

- **Decision**: `AgentScheduler(ProfileRegistry, AgentService, SessionManager, TaskScheduler)`；Spring 注入自动装配的 `ThreadPoolTaskScheduler`（boot 上下文默认有该 bean）
- **Rationale**: 课件 harness 要"ArgumentCaptor 抓注册参数"——TaskScheduler 必须可观察，注入才能 mock/verify；不持有线程池生命周期（Spring 管理）
- **Alternatives considered**: 草图在构造器内 `new ThreadPoolTaskScheduler(pool=4)`——测试抓不到 schedule() 参数；且线程池生命周期外泄给组件

### D3: 时区断言用行为验证（nextExecution），不用 equals

- **Decision**: 注册测试捕获 `Trigger`，断言 `instanceof CronTrigger` + `getExpression()==cron` + `nextExecution(new SimpleTriggerContext(基准,基准,基准))` 等于按配置时区计算的期望时刻
- **Rationale**: CronTrigger 无 zone getter、equals 不比 zone（字节码实证）；`nextExecution` 是"时区生效"的真实行为——cron "0 0 9 * * *" + Asia/Shanghai，基准 2026-08-12T00:00:00Z，期望 2026-08-12T01:00:00Z（若误用服务器时区 UTC 则得 09:00Z，断言失败）
- **Alternatives considered**: 反射读私有字段——丑且脆；equals 断言——实证不比较 zone，抓不住坑四

### D4: 非法 cron 注册失败不阻断启动（逐条隔离）

- **Decision**: `registerAll` 对每条规则单独 try/catch：`CronTrigger` 构造抛异常（非法表达式）→ log.error 记该条、继续注册其他条
- **Rationale**: TechnicalSolution §8.2 既有先例——"校验失败的 Agent 不阻断启动但记录错误日志"；一条坏配置不该让整个调度器起不来
- **Alternatives considered**: 失败即崩（启动即显错）——与 §8.2 先例冲突，且一条坏规则拖垮所有 Agent

### D5: frontmatter `schedules` 解析规则

- **Decision**: `AgentLoader.deriveProfile` 解析 `schedules`（`{id, cron, zone, message}` 列表）→ `List<ScheduleConfig>`；`id` 缺失或空 → log.warn 跳过该条（全局唯一是操作者责任，缺 id 无法拿锁键）；`zone` 缺失 → 存 null，运行时回退系统时区；`message` 缺失 → 存 null（交给 AgentService 前由调用方语义决定，注册本身不拦）
- **Rationale**: clarify 定案 + 坑一"配置即 Agent"（改时间不重编译）；非法配置不阻断启动（与 D4 同源）
- **Alternatives considered**: id 缺失报错阻断——过严，与 §8.2 先例不符

### D6: 删除草图的运行时增删接口与手拼 sessionId

- **Decision**: 删除 `registerProfile`/`unregisterProfile`/任务句柄表/`@PreDestroy` 自建线程池清理；`runOnce` 内 `sessionManager.getOrCreate("scheduler","scheduler",profile.getName())` 拿 Session，`session_id` 拼接只在 SessionManager 内部（H4 不变量④）
- **Rationale**: 运行时增删任务 = 扩展阶段（TechnicalSolution §8.5 原文）；草图手拼 `"scheduler+scheduler+profileName"` 绕过 SessionManager 违反 H4④ 与 18 节约定，且格式与 SessionManager 内部拼接可能不一致（分隔符漂移）
- **Alternatives considered**: 保留草图形状只加 id——会话身份违规与运行时增删超范围都不符合课件

## 不做的事（边界确认）

- 不落 `scheduled_tasks`/`task_executions` 表、无 `ScheduledTaskStore`、无管理端点——第 28 节
- 不加分布式锁/选主/租约——扩展阶段
- 不加失败重试/告警——扩展阶段
- 不改 boot 模块（application.yaml 不动；TaskScheduler 走自动装配默认值，pool=1 对核心阶段语义无碍——重叠场景靠 tryLock 跳过，串行派发不改变行为）
