# Quickstart Validation: Plugin Agent Directory

**Date**: 2026-08-16 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

本节验收以 **harness 自动化**为主（`mvn clean verify` 全绿即机制成立），人工项为辅。下面是可直接执行的验证场景。

## 前置
- JDK 21 + Maven（仓库根 `mvn -v` 可跑）。
- `.oryxos/agents/` 存在（`oryxos init` 已跑过）。
- 示例 Agent 目录 `daily-reconcile/` 已由 spec-kit 产出（四部分俱全）。

## 场景 1：harness 全绿（机制成立）
```bash
mvn clean verify
```
**预期**：全绿，含 P3C/SpotBugs/FindSecBugs/PMD。六测类（AgentLoaderTest、DeriveProfileTest、AgentScanRegisterTest、ProfileRegistryRuntimeTest、AgentSchedulerRegisterTest、ProgressiveDisclosureTest）非空且关键回归测试对号。

只跑本节模块测试：
```bash
mvn -pl oryxos-core test -Dtest='AgentLoaderTest,DeriveProfileTest,AgentScanRegisterTest,ProfileRegistryRuntimeTest,AgentSchedulerRegisterTest,ProgressiveDisclosureTest'
```
**预期**：6 类全绿。

## 场景 2：一个目录上线一个 Agent（人工/半自动）
```bash
# 启动
oryxos serve   # 或 ./scripts/start.sh
# 列 Agent —— daily-reconcile 出现，全程零 Java
curl -s http://localhost:8080/api/v1/profiles | grep daily-reconcile
oryxos profile list
```
**预期**：`daily-reconcile` 在列表里。

## 场景 3：定时来自 Agent（人工，真模型链路）
- 把 `daily-reconcile/AGENT.md` 的 `schedules.cron` 临时设为"2 分钟后"的时刻，设 `OPS_WEBHOOK_URL` 指向可收的 webhook。
- 重启/等触发。
**预期**：到点自动触发 → webhook 收到推送 → `tool_invocations`/`task_executions` 有账。手动 `POST /api/v1/agents/daily-reconcile/invoke` 补跑一次，验证人推与钟推同链路。

## 场景 4：渐进式披露（harness 钉死 + 人工抽查）
- harness `ProgressiveDisclosureTest`：构造多文件 Agent 目录，`loadSystemPrompt` 结果含正文、不含子指令/参考/脚本内容。
- 人工：触发一个带 `skills/` 的 Agent，查 `tool_invocations` 应出现 `read_file("skills/...")` 记录（按需加载生效）。

## 场景 5：正文即时生效（人工）
- 改 `daily-reconcile/AGENT.md` 正文一句，不重启，下一次触发用新说明。（17 节 ContextLoader 无缓存已钉死，本节不重测，仅人工确认。）

## 场景 6：两条来源同规矩（harness 钉死）
- `ProfileRegistryRuntimeTest`：运行时 `register()` 后立即 `get()` 可见；缺 provider 的非法配置，运行时注册与启动扫描抛**同一异常类型 + 同一消息**。

## 场景 7：运行时注册句柄（harness 钉死）
- `AgentSchedulerRegisterTest`：`registerProfile(profile)` 后 `scheduledTasks` 句柄表有该 schedule id 的 `ScheduledFuture`；cron/时区来自 `Profile.schedules`。

## 依赖方向门禁（每节必跑）
```bash
grep -rn 'import com.oryxos.provider' oryxos-core/src/main && echo "VIOLATION" || echo "OK"
```
**预期**：`OK`（core 不反向依赖 provider）。

## 剩余人工项（harness 已判卷，这几项等你人工过）
- 真模型定时链路真触发、webhook 真收到、审计真有账（场景 3）。
- 资源按需加载真跑通（场景 4 人工抽查 `tool_invocations`）。
- 正文改了即时生效（场景 5）。
