package com.oryxos.web.dto;

import com.oryxos.core.profile.Profile;
import java.util.List;

/**
 * Agent 列表/详情视图(web 层 DTO)。core 返回 Profile 实体,web 层负责裁剪成对外形态—— core 不依赖 web,方向不能反。 provider/model
 * 平铺为 String(对齐参考版管理台前端字段)。31 节起 tools 走全局列表,Agent 视图不再携带 per-Agent 工具声明。
 */
public record AgentView(
    String name,
    String description,
    String provider,
    String model,
    List<String> skills,
    List<ScheduleInfo> schedules) {

  public AgentView {
    skills = skills == null ? List.of() : List.copyOf(skills);
    schedules = schedules == null ? List.of() : List.copyOf(schedules);
  }

  public record ScheduleInfo(String id, String cron, String zone, String message) {}

  public static AgentView from(Profile profile) {
    return from(profile, List.of());
  }

  /** liveSkills 传绑定检查实况(没装配时为空的);schedules 字段为 null 时兜底空列表。 */
  public static AgentView from(Profile profile, List<String> liveSkills) {
    Profile.Provider provider = profile.getProvider();
    return new AgentView(
        profile.getName(),
        profile.getDescription(),
        provider == null ? null : provider.name(),
        provider == null ? null : provider.model(),
        liveSkills,
        profile.getSchedules() == null
            ? List.of()
            : profile.getSchedules().stream()
                .map(s -> new ScheduleInfo(s.id(), s.cron(), s.zone(), s.message()))
                .toList());
  }
}
