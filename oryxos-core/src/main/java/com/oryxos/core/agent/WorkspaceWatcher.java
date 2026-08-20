package com.oryxos.core.agent;

import com.oryxos.core.runtime.OryxOsRuntime;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 第二条录入路径的执行者(第 30 节):让 Agent 目录实时生效——丢进去即上线、免重启。
 *
 * <p>启动先全量扫一遍 {@code .oryxos/agents/},之后用 JDK {@link WatchService} 实时监听目录级事件。 新增/修改 → 存在性判断 (存在 →
 * {@link AgentLifecycleService#register(Path)},与 API 上传同一段代码;不存在 → 注销)。 单个坏目录注册失败只记告警, 不中断监听循环。
 *
 * <p>线程模型:一个守护线程(名字 {@code workspace-watcher},setDaemon)——跟 AgentScheduler 的调度线程同类, 是基础设施守护线程,
 * 不把异步编程模型引进请求链路(H4 ⑤;宪法七注释明示此项合法)。
 *
 * <p>边界(课件 5.2.3 注明):WatchService 不递归监听子目录——agents/ 一层就够,目录内文件的改动不会触发事件; 所以文件编辑必须显式走 {@code
 * AgentLifecycleService.update} 重注册,不能指望监听兜底。
 */
@Component
public class WorkspaceWatcher {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceWatcher.class);

  /** 写目录是多文件操作,AGENT.md 可能晚于目录出现——注册失败短暂防抖后重试,首次失败靠重试兜底。 */
  private static final int REGISTER_RETRY_MS = 200;

  private static final int MAX_REGISTER_ATTEMPTS = 5;

  private final AgentLifecycleService lifecycle;
  private final Path agentsDir;

  private volatile WatchService watchService;
  private volatile Thread watcherThread;

  /** 双构造器时 Spring 按 @Autowired 选主构造器(否则回退找无参构造、装配挂);工作区路径走 OryxOsRuntime。 */
  @Autowired
  public WorkspaceWatcher(AgentLifecycleService lifecycle) {
    this(lifecycle, OryxOsRuntime.resolve("agents"));
  }

  public WorkspaceWatcher(AgentLifecycleService lifecycle, Path agentsDir) {
    this.lifecycle = lifecycle;
    this.agentsDir = agentsDir;
  }

  /** 启动:注册监听 → 全量扫存量 → 起守护线程进事件循环。测试可调 stop() 关停。 */
  public void start() {
    try {
      watchService = FileSystems.getDefault().newWatchService();
      Files.createDirectories(agentsDir);
      agentsDir.register(
          watchService,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_DELETE,
          StandardWatchEventKinds.ENTRY_MODIFY);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start workspace watcher", e);
    }
    scanAll();
    watcherThread = new Thread(this::watchLoop, "workspace-watcher");
    watcherThread.setDaemon(true);
    watcherThread.start();
  }

  /** 启动全量扫:把存量 Agent 目录都拾起来,与实时事件走同一段 register(agentDir)。 */
  public void scanAll() {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(agentsDir)) {
      for (Path child : stream) {
        if (Files.isDirectory(child)) {
          registerSafely(child);
        }
      }
    } catch (IOException e) {
      log.warn("workspace scan failed: {}", e.getMessage());
    }
  }

  private void watchLoop() {
    while (true) {
      WatchKey key;
      try {
        key = watchService.take();
      } catch (java.nio.file.ClosedWatchServiceException e) {
        log.info("workspace watcher stopped");
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      try {
        for (WatchEvent<?> event : key.pollEvents()) {
          Path dir = (Path) key.watchable();
          Path child = dir.resolve((Path) event.context());
          if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            // DELETE 事件时目录已不在磁盘,直接交注销分支判断
            onAgentDirEvent(child);
          } else if (Files.isDirectory(child)) {
            // CREATE/MODIFY 只关心目录级事件;agents/ 下的散文件不是 Agent
            onAgentDirEvent(child);
          }
        }
      } finally {
        key.reset();
      }
    }
  }

  /** 目录级事件的统一处理:存在 = 新增/修改 → register(agentDir);不存在 = 删除 → 注销。 包级可见,供测试直接驱动事件处理逻辑。 */
  void onAgentDirEvent(Path agentDir) {
    if (Files.exists(agentDir)) {
      registerSafely(agentDir);
    } else {
      unregisterSafely(agentDir);
    }
  }

  private void registerSafely(Path agentDir) {
    for (int attempt = 1; attempt <= MAX_REGISTER_ATTEMPTS; attempt++) {
      try {
        lifecycle.register(agentDir);
        return;
      } catch (Exception e) {
        if (attempt == MAX_REGISTER_ATTEMPTS) {
          // 单个坏目录不拖垮监听
          log.warn(
              "failed to register agent dir {} ({} attempts): {}",
              agentDir,
              attempt,
              e.getMessage());
        } else {
          try {
            Thread.sleep(REGISTER_RETRY_MS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    }
  }

  private void unregisterSafely(Path agentDir) {
    try {
      // API 删除只是"删目录 + 归档"的规整版,底层注销同一段代码;目录已不存在,归档是空操作
      lifecycle.delete(agentDir.getFileName().toString());
    } catch (Exception e) {
      log.warn("failed to unregister agent dir {}: {}", agentDir, e.getMessage());
    }
  }

  /** 停监听(测试关停用):关闭 WatchService,守护线程经 ClosedWatchServiceException 退出。 */
  public void stop() {
    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException ignored) {
        // 关停路径:close 失败不影响测试收尾
      }
    }
  }
}
