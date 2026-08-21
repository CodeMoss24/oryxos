package com.oryxos.web.controller;

import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.agent.AgentStore;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskView;
import com.oryxos.core.scheduler.TaskExecutionView;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.ScheduleAddRequest;
import com.oryxos.web.dto.ScheduleEnableRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务管理端点(第 28 节 + 30 节补管理闭环)。
 *
 * <p>六端点:列表 / 执行历史 / 立即执行 / 启用停用 / 添加 / 删除。定义源是 Agent 的 AGENT.md schedules(创建 Agent 填 cron
 * 或本页"添加"都写进 AGENT.md),删除同理从 AGENT.md 移除条目——不是独立实体。 Agent
 * 已归档的幽灵任务(库记录残留)删除时直接清库。AgentLifecycleService 在 core,web 可注入。
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleApiController {

  private final AgentScheduler scheduler;
  private final AgentLifecycleService lifecycle;

  public ScheduleApiController(AgentScheduler scheduler, AgentLifecycleService lifecycle) {
    this.scheduler = scheduler;
    this.lifecycle = lifecycle;
  }

  /** 列出所有定时任务及运行状态。 */
  @GetMapping
  public ApiResponse<List<ScheduledTaskView>> list() {
    return ApiResponse.ok(scheduler.listAll());
  }

  /** 某任务的执行历史(按 startedAt 倒序)。 */
  @GetMapping("/{id}/executions")
  public ApiResponse<List<TaskExecutionView>> executions(@PathVariable String id) {
    return ApiResponse.ok(scheduler.executions(id));
  }

  /** 立即执行一次(手动触发,不等 cron、无视启用状态)。 */
  @PostMapping("/{id}/run")
  public ApiResponse<Map<String, Object>> run(@PathVariable String id) {
    scheduler.runNow(id);
    return ApiResponse.ok(Map.of("status", "triggered", "taskId", id));
  }

  /** 启用/停用任务。 */
  @PutMapping("/{id}")
  public ApiResponse<Map<String, Object>> toggle(
      @PathVariable String id, @RequestBody ScheduleEnableRequest body) {
    scheduler.setEnabled(id, body.enabled());
    return ApiResponse.ok(Map.of("taskId", id, "enabled", body.enabled()));
  }

  /** 添加定时任务:写入目标 Agent 的 AGENT.md schedules(cron 非法 / Agent 不存在 / 同 Agent 已有任务 → 400),写盘即生效。 */
  @PostMapping
  public ApiResponse<Map<String, Object>> add(@RequestBody ScheduleAddRequest body) {
    if (body == null || body.agent() == null || body.agent().isBlank()) {
      throw new IllegalArgumentException("agent 不能为空");
    }
    String name = body.agent().trim();
    AgentStore.ScheduleDraft draft =
        new AgentStore.ScheduleDraft(body.cron(), body.zone(), body.message());
    lifecycle.addSchedule(name, draft);
    return ApiResponse.ok(Map.of("taskId", name + "-schedule", "agent", name));
  }

  /**
   * 删除定时任务:从所属 Agent 的 AGENT.md 移除 schedules 条目 → 注销句柄 → 清 scheduled_tasks + 执行历史。 Agent 已归档(幽灵
   * 任务)直接清库。任务不存在 → 400。
   */
  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> remove(@PathVariable String id) {
    lifecycle.removeSchedule(id);
    return ApiResponse.ok(Map.of("taskId", id, "deleted", true));
  }
}
