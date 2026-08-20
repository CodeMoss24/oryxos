package com.oryxos.web.dto;

/** 异步触发响应:executionId 立即可查执行历史,status 恒为 RUNNING(后台执行中)。 */
public record TriggerResponse(long executionId, String status) {

  public static TriggerResponse running(long executionId) {
    return new TriggerResponse(executionId, "RUNNING");
  }
}
