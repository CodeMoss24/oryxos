package com.oryxos.tool.sandbox;

import com.oryxos.core.exception.SandboxViolationException;
import com.oryxos.core.runtime.OryxOsRuntime;
import com.oryxos.core.sandbox.SandboxWhitelistPort;
import com.oryxos.core.sandbox.SandboxWhitelistStore;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 核心阶段唯一实现。文件操作限制工作目录、Shell 命令白名单、HTTP 域名白名单,在应用层做校验,不使用 Java SecurityManager(JDK 17 起已废弃、JDK 21
 * 已不可用)。
 *
 * <p>应用层白名单是"劝阻级"防线,防的是模型犯傻误操作,防不住蓄意绕过。三个校验方法都是 private——对外只有 enforce 一个入口,接口不被这一档 实现带偏。
 *
 * <p>路径校验:目标 normalize().toAbsolutePath() 后必须 startsWith 某个允许根;允许根在构造时同样转绝对(相对配置按当前工作目录解析), 基准一致才能防
 * `../` 路径穿越。
 *
 * <p>同时实现 {@link SandboxWhitelistPort}:管理台可经 Web 端点运行时查询 / 增删白名单。存储用并发集合({@link
 * CopyOnWriteArrayList} / {@link
 * ConcurrentHashMap#newKeySet()})——校验读路径无锁(热路径),管理写路径极少发生、拷贝开销可接受。双构造器:纯内存 props 版(单测 / 无库场景)与
 * store 持久化版(Spring 装配,重启保留);add/remove 先改内存再写库(幂等,仅 changed 时落库),写后下一次工具调用即按新白名单校验。 每次改动落 INFO
 * 日志留痕(动态改白名单 = 远程调整安全护栏,审计 day one)。
 */
@Component
public class WhitelistSandbox implements Sandbox, SandboxWhitelistPort {

  private static final Logger LOG = LoggerFactory.getLogger(WhitelistSandbox.class);

  // 具体类型 CopyOnWriteArrayList(而非 List 接口):需要 addIfAbsent 的原子"不存在才加"语义
  private final CopyOnWriteArrayList<Path> allowedRoots = new CopyOnWriteArrayList<>();
  private final Set<String> allowedCommands = ConcurrentHashMap.newKeySet();
  private final CopyOnWriteArrayList<String> allowedDomainPatterns = new CopyOnWriteArrayList<>();

  // 持久化后端:非空则 add/remove 写穿落库、构造时从库恢复;为 null 时纯内存(单测 / 无库场景)
  private final SandboxWhitelistStore store;

  /**
   * 纯内存构造:三块白名单来自配置 + 恒加 workspace root。null(配置键缺省)归一为空 = deny-all,绝不 NPE 也绝不放行。 测试直接 new 本构造, 不经
   * Spring 容器。
   */
  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.store = null;
    seed(fileProps, shellProps, httpProps);
  }

  /**
   * 持久化构造(Spring 装配):先恢复已落库的三类白名单,再播种配置默认值 + workspace root(经 {@link #add} 幂等写穿,库为唯一事实源)。 之后
   * add/remove 写穿到库、重启保留。显式 {@code @Autowired} 标注本构造器,与纯内存版无歧义。
   */
  @Autowired
  public WhitelistSandbox(
      SandboxWhitelistStore store,
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.store = store;
    for (SandboxWhitelistStore.Entry entry : store.loadAll()) {
      applyToMemory(entry.category(), entry.value()); // 库恢复:只进内存,不再写穿
    }
    seed(fileProps, shellProps, httpProps);
  }

  /** 播种配置默认白名单 + 恒加 workspace root(add 幂等,store 为 null 时仅改内存)。 */
  private void seed(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    nullToEmpty(fileProps.allowedPaths()).forEach(p -> add(Category.FILE, p));
    nullToEmpty(shellProps.allowedCommands()).forEach(c -> add(Category.SHELL, c));
    nullToEmpty(httpProps.allowedDomains()).forEach(d -> add(Category.HTTP, d));
    // workspace 恒可读写:管理台加的白名单路径之外,Agent 产出与工作区文件不因误删根白名单而失锁
    add(Category.FILE, OryxOsRuntime.workspaceRoot().toString());
  }

  private static List<String> nullToEmpty(List<String> list) {
    return list == null ? List.of() : list;
  }

  /** 仅更新内存(不写库):构造 / 恢复时用。FILE 归一为绝对路径。 */
  private void applyToMemory(Category category, String value) {
    if (category == Category.FILE) {
      allowedRoots.addIfAbsent(normalizeRoot(value));
    } else if (category == Category.SHELL) {
      allowedCommands.add(value);
    } else {
      allowedDomainPatterns.addIfAbsent(value);
    }
  }

  private static Path normalizeRoot(String rawPath) {
    return Path.of(rawPath).toAbsolutePath().normalize();
  }

  @Override
  public void enforce(SandboxAction action) {
    switch (action.type()) {
      case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
      case SHELL_COMMAND -> checkShellCommand(action.target());
      case HTTP_REQUEST -> checkHttpUrl(action.target());
    }
  }

  private void checkFilePath(String rawPath) {
    Path target = Path.of(rawPath).normalize().toAbsolutePath();
    boolean allowed = allowedRoots.stream().anyMatch(target::startsWith);
    if (!allowed) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  private void checkShellCommand(String command) {
    String firstToken = command.trim().split("\\s+")[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  private void checkHttpUrl(String url) {
    String host = URI.create(url).getHost();
    boolean allowed =
        allowedDomainPatterns.stream().anyMatch(pattern -> matchesDomain(host, pattern));
    if (!allowed) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  private boolean matchesDomain(String host, String pattern) {
    String h = host.toLowerCase(Locale.ROOT);
    String p = pattern.toLowerCase(Locale.ROOT);
    if (p.startsWith("*.")) {
      // 带点号边界:*.example.com → .example.com 结尾才命中,形似域名 evil-example.com 不得绕过
      return h.endsWith(p.substring(1));
    }
    return h.equals(p);
  }

  // ---- SandboxWhitelistPort:运行时管理(查询 / 增加 / 删除)----

  @Override
  public List<String> list(Category category) {
    if (category == Category.FILE) {
      return allowedRoots.stream().map(Path::toString).toList();
    }
    if (category == Category.SHELL) {
      return List.copyOf(allowedCommands);
    }
    return List.copyOf(allowedDomainPatterns);
  }

  @Override
  public boolean add(Category category, String value) {
    String entry = requireNonBlank(value);
    boolean changed;
    String canonical; // 入内存的规范形,也是落库 / 展示 / 删除对齐的值(FILE 为归一后的绝对路径)
    if (category == Category.FILE) {
      Path root = normalizeRoot(entry);
      canonical = root.toString();
      changed = allowedRoots.addIfAbsent(root);
    } else if (category == Category.SHELL) {
      canonical = entry;
      changed = allowedCommands.add(entry);
    } else {
      canonical = entry;
      changed = allowedDomainPatterns.addIfAbsent(entry);
    }
    // 写穿:只有内存确有变更才落库(幂等,避免重复写;启动播种重复调用不会重复插入)
    if (changed && store != null) {
      store.add(category, canonical);
    }
    LOG.info("Sandbox 白名单增加 {} -> {} (changed={})", category, sanitize(entry), changed);
    return changed;
  }

  @Override
  public boolean remove(Category category, String value) {
    String entry = requireNonBlank(value);
    boolean changed;
    String canonical;
    if (category == Category.FILE) {
      Path root = normalizeRoot(entry);
      Path removed = removeFileRoot(root);
      canonical = removed == null ? root.toString() : removed.toString();
      changed = removed != null;
    } else if (category == Category.SHELL) {
      canonical = entry;
      changed = allowedCommands.remove(entry);
    } else {
      canonical = entry;
      changed = allowedDomainPatterns.remove(entry);
    }
    if (changed && store != null) {
      store.remove(category, canonical);
    }
    LOG.info("Sandbox 白名单删除 {} -> {} (changed={})", category, sanitize(entry), changed);
    return changed;
  }

  /** 删除文件白名单根:先试归一形(管理台展示的就是归一形),失败再试词法形(用户手输相对路径)。 */
  private Path removeFileRoot(Path normalized) {
    if (allowedRoots.remove(normalized)) {
      return normalized;
    }
    return null;
  }

  private static String requireNonBlank(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("白名单条目不能为空");
    }
    return value.strip();
  }

  /** 去掉 CR/LF,防止条目内容伪造日志行(CWE-117)。 */
  private static String sanitize(String value) {
    return value.replace('\r', '_').replace('\n', '_');
  }
}
