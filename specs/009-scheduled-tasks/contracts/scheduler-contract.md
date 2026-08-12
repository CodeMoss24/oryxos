# Contract: AgentScheduler 行为契约

**Date**: 2026-08-12 | **Feature**: 009-scheduled-tasks

组件：`com.oryxos.core.scheduler.AgentScheduler`（@Component，Spring 管理生命周期）

## 依赖（构造器注入）

| 依赖 | 类型 | 来源 |
|------|------|------|
| ProfileRegistry | 接口类 | 16 节交付，`list()` 扫全部 Profile |
| AgentService | 类 | 17 节交付，`process(Session, String)` 统一入口 |
| SessionManager | 接口 | 18 节交付，`getOrCreate(channel, user, profileName)` 幂等取会话 |
| TaskScheduler | Spring 接口 | boot 上下文自动装配的 `ThreadPoolTaskScheduler`（bean 名 "taskScheduler"） |

## 行为契约

### 注册（启动时）

1. `@PostConstruct registerAll()`：遍历 `profileRegistry.list()` 每个 Profile 的 `getSchedules()`，逐条 `taskScheduler.schedule(() -> runOnce(profile, sc), new CronTrigger(sc.getCron(), sc.zoneId()))`
2. **cron + 时区一起传**——`CronTrigger(String, ZoneId)`，不用服务器系统时区替用户做主（坑四）
3. **逐条隔离**：单条规则构造 `CronTrigger` 抛异常（非法 cron）→ log.error 记该条，继续注册其余条，不阻断启动（§8.2 先例）
4. 不用静态 `@Scheduled` 注解——注册内容完全来自 Profile 配置（坑一）

### 触发一次（runOnce(Profile, ScheduleConfig)）

```
1. 锁 = taskLocks.computeIfAbsent(sc.getId(), id -> new ReentrantLock())
2. if (!lock.tryLock()) → log.info 跳过本次触发（不排队、不堆积），return   [坑二]
3. try:
     session = sessionManager.getOrCreate("scheduler", "scheduler", profile.getName())   [会话身份约定]
     agentService.process(session, sc.getMessage())                                      [走与 CLI/Web 完全相同的入口]
   catch (Exception e) → log.error 记任务 id + 异常，不外抛                         [坑三：调度器不崩]
   finally → lock.unlock()                                                           [坑三：不留死锁]
```

### 并发与失败语义

- **锁键 = 任务 id**（全局唯一，操作者责任，clarify 定案）；同 id 两任务共用一把锁属配置错误
- 进程内 `ReentrantLock`，**不是**分布式锁（核心阶段单实例）
- 失败照常走 `agentService.process` 内部审计（llm_calls/tool_invocations），不为钟推单独开逻辑
- 无运行时增删任务接口（扩展阶段）；无任务句柄管理（schedule 一次性注册，不跟踪 ScheduledFuture）

## 测试可见性（harness 需求）

- `runOnce(Profile, ScheduleConfig)`：包级可见（harness 直接调用测全部分支，不真等时间）
- `lockFor(String taskId)`：包级可见，返回该任务锁（harness 模拟"上一次还占着锁"）

## 会话身份（跨节契约）

`("scheduler", "scheduler", profileName)` 三元组——18 节 `session_id` = channel+user+profile 联合生成公式在 SessionManager 内应用；不在此处手拼 session_id。
