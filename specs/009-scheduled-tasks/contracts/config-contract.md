# Contract: frontmatter `schedules` 配置契约

**Date**: 2026-08-12 | **Feature**: 009-scheduled-tasks

定义来源：`.oryxos/agents/<name>/AGENT.md` frontmatter（YAML，`---` 包围）。解析方：`AgentLoader.deriveProfile` → `Profile.schedules: List<ScheduleConfig>`。

## 键定义

| 键 | 类型 | 必填 | 语义 | 缺省/非法处理 |
|----|------|------|------|---------------|
| `id` | string | 是 | 任务标识，全局唯一（跨 Agent），防重叠锁键、28 节 task_id 同源 | 缺失/空 → log.warn 跳过该条，其余条照常注册 |
| `cron` | string | 是 | cron 表达式（Spring CronTrigger 语法） | 非法 → 注册时抛异常，逐条隔离 log.error，不阻断启动 |
| `zone` | string | 否 | IANA 时区名 | 缺失 → null，运行时回退 `ZoneId.systemDefault()` |
| `message` | string | 否 | 到点拼给 Agent 的消息 | 缺失 → null，触发时原样传 |

## 示例

```yaml
---
identity:
  agent_name: 天气助手
  prompt: 你是每日天气播报助手
schedules:
  - id: daily-weather
    cron: "0 9 * * *"
    zone: Asia/Shanghai
    message: 到点了，帮我查一下今天的天气并整理成播报
---
```

## 边界

- 非 `schedules` 键的非法条目：不解析、不报错（同既有 frontmatter 行为）
- 多个 Agent 同 id：操作者配置错误，本节不校验（28 节落库主键冲突显式报错）
- 运行时改 frontmatter：重启生效（定义随进程启动注册，改 cron 需重启——扩展阶段补运行时增删）
