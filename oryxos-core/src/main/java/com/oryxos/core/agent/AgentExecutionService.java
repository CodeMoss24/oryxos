package com.oryxos.core.agent;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 执行编排:异步触发 + 执行历史。
 *
 * <p>异步触发({@link #triggerAsync})先落一条"运行中"记录、立即返回 id(HTTP 请求不再干等整轮 ReAct,杜绝浏览器 Failed to fetch),真正的
 * ReAct 在虚拟线程后台跑,结束回填状态——符合宪法(VII:virtual thread 撑高并发)。成功失败都留痕(审计 day one)。
 */
@Component
public class AgentExecutionService {

  private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

  private final AgentExecutionStore store;
  private final ExecutorService executor;

  public AgentExecutionService(AgentExecutionStore store) {
    this.store = store;
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * 异步触发一次 Agent 执行:落"运行中"记录 → 立即返回 id → {@code work} 在虚拟线程后台执行 → 结束回填。 {@code work}
   * 内部即完整的一轮编排({@code AgentService.process},审计在其内部)。
   */
  public long triggerAsync(String agentName, String source, String sessionId, Runnable work) {
    long id = store.start(agentName, source, Instant.now());
    executor.execute(
        () -> {
          boolean ok = false;
          String error = null;
          try {
            work.run();
            ok = true;
          } catch (RuntimeException e) {
            error = e.getMessage();
            log.error("Agent " + sanitize(agentName) + " 后台执行失败", e);
          } finally {
            safeFinish(id, sessionId, ok, error);
          }
        });
    return id;
  }

  public List<AgentExecution> history(String agentName, int limit) {
    return store.listByAgent(agentName, limit);
  }

  private void safeFinish(long id, String sessionId, boolean success, String error) {
    try {
      store.finish(id, sessionId, success, error, Instant.now());
    } catch (RuntimeException e) {
      log.warn("Agent 执行记录回填失败(id={}):{}", id, sanitize(e.getMessage()));
    }
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
  }
}
