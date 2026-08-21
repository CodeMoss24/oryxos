package com.oryxos.web.dto;

import java.util.List;

/**
 * 创建 Agent 请求体:{name, description},目录结构由脚手架生成; skillBindings 可选——创建成功后逐个建立固定软连接绑定(管理台新建页勾选的 Skill);
 * schedule 可选——创建时即写入 AGENT.md 的 schedules(管理台新建页填的 cron),留空 = 不配定时。
 */
public record CreateAgentRequest(
    String name, String description, List<String> skillBindings, ScheduleDraft schedule) {

  public record ScheduleDraft(String cron, String zone, String message) {}
}
