package com.oryxos.core.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Agent 派生 Profile 的内存索引,按 name 提供快速查找。 Channel 接收消息时通过它拿到具体 Profile。 */
@Component
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new LinkedHashMap<>();

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
