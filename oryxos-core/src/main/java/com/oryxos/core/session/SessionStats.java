package com.oryxos.core.session;

/** 会话统计(供管理台概览统计卡)。 */
public record SessionStats(int active, int archived) {

  public int total() {
    return active + archived;
  }
}
