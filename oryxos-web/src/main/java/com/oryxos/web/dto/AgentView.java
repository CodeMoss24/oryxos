package com.oryxos.web.dto;

import com.oryxos.core.profile.Profile;
import java.util.List;

/** Agent 列表/详情视图(web 层 DTO)。core 返回 Profile 实体,web 层负责裁剪成对外形态—— core 不依赖 web,方向不能反。 */
public record AgentView(
    String name,
    String description,
    ProviderInfo provider,
    List<String> tools,
    List<String> skills,
    List<ScheduleInfo> schedules) {

  public record ProviderInfo(String name, String model) {}

  public record ScheduleInfo(String id, String cron, String zone, String message) {}

  public static AgentView from(Profile profile) {
    Profile.Provider provider = profile.getProvider();
    return new AgentView(
        profile.getName(),
        profile.getDescription(),
        provider == null ? null : new ProviderInfo(provider.name(), provider.model()),
        profile.getTools(),
        profile.getSkills(),
        profile.getSchedules() == null
            ? List.of()
            : profile.getSchedules().stream()
                .map(s -> new ScheduleInfo(s.id(), s.cron(), s.zone(), s.message()))
                .toList());
  }
}
