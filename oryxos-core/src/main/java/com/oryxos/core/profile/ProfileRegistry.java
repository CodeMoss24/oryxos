package com.oryxos.core.profile;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Agent 派生 Profile 的内存索引,按 name 提供快速查找。 Channel 接收消息时通过它拿到具体 Profile。
 *
 * <p>第 29 节改可变并发结构:启动扫描与运行时新增(为下节 API 管理铺路)都走 {@link #register(Profile)}, 用 ConcurrentHashMap
 * 保证运行时注册后立即可见。register/remove/exists/find/list 签名不变,调用方零改动。
 */
@Component
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

  public void register(Profile profile) {
    profiles.put(profile.getName(), profile);
  }

  public void remove(String name) {
    profiles.remove(name);
  }

  public boolean exists(String name) {
    return profiles.containsKey(name);
  }

  public Optional<Profile> find(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  public Collection<Profile> list() {
    return profiles.values();
  }
}
