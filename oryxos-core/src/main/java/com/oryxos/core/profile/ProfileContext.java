package com.oryxos.core.profile;

/**
 * 当前请求的 Profile 上下文(ThreadLocal)。 解决"工具执行时怎么知道当前是哪个 Agent"——OryxTool.execute 签名不带 Profile,
 * NotifyTools 取 notify_channels、按 Profile 过滤工具子集这类需求都从 ProfileContext 读。
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
