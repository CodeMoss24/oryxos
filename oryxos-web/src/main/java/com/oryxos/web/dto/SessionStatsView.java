package com.oryxos.web.dto;

/** GET /api/v1/sessions/stats 视图:活跃、归档、总会话计数。 */
public record SessionStatsView(int active, int archived, int total) {

  public static SessionStatsView from(int active, int archived) {
    return new SessionStatsView(active, archived, active + archived);
  }
}
