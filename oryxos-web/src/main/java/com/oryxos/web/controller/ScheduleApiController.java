package com.oryxos.web.controller;

import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskView;
import com.oryxos.core.scheduler.TaskExecutionView;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.ScheduleEnableRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务管理端点(第 28 节)。
 *
 * <p>四端点:列表 / 执行历史 / 立即执行 / 启用停用。AgentScheduler 在 core,web 可注入。
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleApiController {

  private final AgentScheduler scheduler;

  public ScheduleApiController(AgentScheduler scheduler) {
    this.scheduler = scheduler;
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
}
