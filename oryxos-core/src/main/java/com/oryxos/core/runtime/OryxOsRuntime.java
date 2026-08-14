package com.oryxos.core.runtime;

import java.nio.file.Path;

/**
 * 工作区根的统一定义口(第 27 节)。
 *
 * <p>默认 ".oryxos";整机测试用系统属性 {@code oryxos.root} 指向临时工作区(默认仍 ".oryxos" 不变)。
 * 每次调用实时读属性、不在类加载时缓存——测试类在起 Spring 上下文前先 {@code System.setProperty} 即可生效,与类的加载顺序无关。
 */
public final class OryxOsRuntime {

  /** 系统属性名:覆盖工作区根,默认 ".oryxos"。 */
  public static final String ROOT_PROPERTY = "oryxos.root";

  private OryxOsRuntime() {}

  /** 工作区根目录(默认 .oryxos,可用 -Doryxos.root 覆盖)。 */
  public static Path workspaceRoot() {
    String override = System.getProperty(ROOT_PROPERTY);
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return Path.of(".oryxos");
  }

  /** 工作区根下的相对路径:resolve("agents", "foo") → {@code <root>/agents/foo};绝对路径原样返回。 */
  public static Path resolve(String first, String... more) {
    Path path = Path.of(first, more);
    return path.isAbsolute() ? path : workspaceRoot().resolve(path);
  }
}
