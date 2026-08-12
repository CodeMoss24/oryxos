# Quickstart: 定时任务模块验收/回归指南

**Date**: 2026-08-12 | **Feature**: 009-scheduled-tasks

## 自动化验收（harness 判卷）

```bash
# 全量门禁（含 P3C/SpotBugs/PMD 静态检查）
mvn clean verify

# 只跑本节测试
mvn -pl oryxos-core test -Dtest=AgentSchedulerTest,AgentLoaderTest
```

**预期**：全绿。`AgentSchedulerTest` 四坑对号：

| 测试 | 守住的坑 |
|------|---------|
| 注册时 CronTrigger 带 cron + 时区（ArgumentCaptor + nextExecution 行为断言） | 坑四 |
| 锁被占时本次触发跳过（verify never process） | 坑二 |
| 异常不外抛 + 锁 finally 释放（二进宫 times(2)） | 坑三 |
| 会话三元组固定 scheduler/scheduler/profileName、两次触发同一 Session | 会话身份约定 |

## 人工验证（课件"五、做完怎么验"剩余项）

前置：环境已配置 LLM Provider（`oryxos init` + 环境变量）。

```bash
# 1. 给某 Agent 配每分钟定时（.oryxos/agents/<name>/AGENT.md frontmatter）
#    schedules:
#      - id: demo-every-minute
#        cron: "* * * * *"
#        zone: Asia/Shanghai
#        message: 到点了，简单说一句"定时任务触发成功"即可

# 2. 启动（serve/gateway/chat 任一常驻）
oryxos serve
```

**逐项**：

- **真实到点触发一次**：等到下个分钟点，看 Agent 自动发起对话；`sqlite3 .oryxos/oryxos.db "select count(*) from llm_calls"` 有账、`sessions` 表出现 `scheduler+scheduler+<agent>` 会话
- **改 cron 不重编译**：把 `cron` 改成 `*/5 * * * *`，重启，按新节奏触发
- **端到端预演**：完整走一遍"到点自动触发 → ReAct 循环 → 审计留痕"，为 31 节两个定时 Demo 踩实地基
- **重叠跳过 / 失败隔离 / 锁释放 / 时区 / 会话身份**：harness 已覆盖，`mvn test` 绿即打勾，无需人工

## 已知边界（本节不做）

- `scheduled_tasks`/`task_executions` 表、管理端点——第 28 节
- 分布式协调、失败重试告警、运行时增删任务——扩展阶段
