package com.oryxos.core.profile;

/**
 * 当前请求的 Profile 上下文(ThreadLocal)。 解决"工具执行时怎么知道当前是哪个 Agent"——OryxTool.execute 签名不带 Profile, 工具执行里需要按
 * Agent 区分行为的场景(如 per-agent 记忆)从 ProfileContext 读。31 节起 notify/tools 都走全局注册表, 不再从这里取渠道或工具白名单。
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

  private ProfileContext() {}

  public static void set(Profile profile) {
    CURRENT.set(profile);
  }

  public static Profile get() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
