package com.oryxos.core.scheduler;

import java.time.ZoneId;

/**
 * 一条定时规则的完整配置声明,来自 AGENT.md frontmatter 的 schedules 段。
 *
 * <p>id 是任务标识,全局唯一(跨 Agent 不重名,操作者责任)——防重叠锁的键, 也是扩展阶段 scheduled_tasks.task_id 主键的同源标识,重名属配置错误。
 *
 * <p>zone 显式声明触发时区,缺省回退服务器系统时区(cron 表达式默认按系统时区, 别让服务器时区替用户做主)。
 */
public record ScheduleConfig(String id, String cron, String zone, String message) {

  /** 触发时区:配置了 zone 用配置的,否则回退系统时区。 */
  public ZoneId zoneId() {
    return zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone);
  }
}
