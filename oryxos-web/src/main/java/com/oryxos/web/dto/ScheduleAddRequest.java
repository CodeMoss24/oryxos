package com.oryxos.web.dto;

/** 添加定时任务请求(管理台):把 schedule 写进目标 Agent 的 AGENT.md schedules(定义源仍是 AGENT.md)。 */
public record ScheduleAddRequest(String agent, String cron, String zone, String message) {}
