package com.oryxos.core.agent;

/**
 * 工具执行上下文:记录当前工具正替哪个 Agent 执行(第 30 节 per-agent 记忆)。
 *
 * <p>同步阻塞模型(原则七)下,一次 ReAct 循环整体跑在一条虚拟线程上——{@code ToolExecutor} 执行工具前置入 {@code
 * profile.name()}、执行后清除,{@code save_memory}/{@code recall_memory} 据此落到本 Agent 的 MEMORY.md。
 * 读路径(buildContext/readAll)不经 ToolExecutor,由 MemoryServiceImpl 在委托 store 前后临时置入再复原。
 *
 * <p>仅内存线程局部变量,不跨线程传递、不参与序列化;无 Agent 上下文时取到 null(回退全局路径)。
 */
public final class ToolExecutionContext {

  private static final ThreadLocal<String> AGENT_NAME = new ThreadLocal<>();

  private ToolExecutionContext() {}

  /** 置入当前线程正在服务的 Agent 名。 */
  public static void set(String agentName) {
    AGENT_NAME.set(agentName);
  }

  /** 取当前线程的 Agent 名;无上下文时返回 null。 */
  public static String get() {
    return AGENT_NAME.get();
  }

  /** 清除当前线程的 Agent 上下文(ThreadLocal 防泄漏,配合 finally 使用)。 */
  public static void clear() {
    AGENT_NAME.remove();
  }
}
