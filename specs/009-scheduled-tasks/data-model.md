# Data Model: 定时任务模块（第三种触发源）

**Date**: 2026-08-12 | **Feature**: 009-scheduled-tasks

## 持久化

**无新增 SQLite 表。** 本节不落 `scheduled_tasks`/`task_executions`（第 28 节补齐，spec 边界已声明）。定时触发的审计记录走既有 `llm_calls`/`tool_invocations` 表（`agentService.process` 内部既有路径），表结构零改动。

## 运行时值对象（无持久化）

### ScheduleConfig（定时规则配置）

一次定时触发的完整声明。落在 `com.oryxos.core.scheduler` 包，`Profile.schedules` 字段的条目类型（替代草图的 `Profile.Schedule`）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | 是 | 任务标识，全局唯一（跨 Agent 不重名，操作者责任）；防重叠锁的键、第 28 节 `scheduled_tasks.task_id` 主键的同源标识 |
| cron | String | 是 | cron 表达式（Spring CronTrigger 语法） |
| zone | String | 否 | IANA 时区名（如 Asia/Shanghai）；null/空 → `zoneId()` 回退 `ZoneId.systemDefault()` |
| message | String | 否 | 到点拼给 Agent 的消息内容；null 时注册照常、触发时原样传（语义由调用方定义） |

访问器 `zoneId()`：`zone` 非空 → `ZoneId.of(zone)`；否则 `ZoneId.systemDefault()`。

## 配置契约（AGENT.md frontmatter）

```yaml
schedules:
  - id: daily-weather      # 必填，全局唯一
    cron: "0 9 * * *"      # 必填
    zone: Asia/Shanghai    # 可选，缺省 = 服务器系统时区
    message: 现在几点了，帮我看看今天的天气  # 可选，到点说的话
  - id: hourly-digest
    cron: "0 * * * *"
```

解析规则（`AgentLoader.deriveProfile`）：

| 情形 | 处理 |
|------|------|
| `schedules` 缺失 | 不设置（保持空列表），Agent 无定时任务 |
| 条目缺 `id` / `id` 为空 | log.warn 跳过该条（无锁键可用），其余条照常注册 |
| 条目缺 `zone` | 存 null，运行时回退系统时区 |
| `cron` 非法 | 注册时（构造 CronTrigger）抛异常 → 逐条隔离 log.error，其余任务照常（§8.2 先例） |
| 两个 Agent 声明相同 id | 配置错误（操作者责任），本节不强制校验；第 28 节落库时主键冲突显式报错 |

## 会话身份（运行期约定，非持久化新表）

定时触发复用既有 `sessions` 表：channel = `"scheduler"`、user = `"scheduler"`、profile = Agent 名——三元组经 `SessionManager.getOrCreate` 幂等取会话，`session_id` 拼接只发生在 SessionManager 内部；同一 Agent 历次定时触发同一 Session，历史靠 `max_history_turns` 截断兜底。
