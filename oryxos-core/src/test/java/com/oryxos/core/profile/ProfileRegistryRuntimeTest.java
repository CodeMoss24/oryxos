package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 第 29 节 harness:ProfileRegistry 运行时注册。 改可变并发 Map 后,register() 后立即 find()/exists() 可见、remove()
 * 后不可见。 "两条来源同规矩"——运行时注册缺 provider 的非法配置与启动扫描抛同一异常类型 + 同一消息(同一段 deriveProfile 校验)。
 */
@DisplayName("ProfileRegistry — 运行时注册立即可见")
class ProfileRegistryRuntimeTest {

  @TempDir Path agentsDir;

  @Test
  @DisplayName("register() 后立即 find() 可见")
  void registerImmediatelyVisible() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile p = new Profile();
    p.setName("runtime-agent");

    registry.register(p);

    assertThat(registry.exists("runtime-agent")).isTrue();
    assertThat(registry.find("runtime-agent")).containsSame(p);
  }

  @Test
  @DisplayName("remove() 后 find() 不可见")
  void removeMakesInvisible() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile p = new Profile();
    p.setName("gone-agent");
    registry.register(p);

    registry.remove("gone-agent");

    assertThat(registry.exists("gone-agent")).isFalse();
    assertThat(registry.find("gone-agent")).isEmpty();
  }

  @Test
  @DisplayName("重复注册同名覆盖既有条目")
  void reRegisterOverwrites() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile first = new Profile();
    first.setName("dup");
    first.setDescription("first");
    Profile second = new Profile();
    second.setName("dup");
    second.setDescription("second");
    registry.register(first);

    registry.register(second);

    assertThat(registry.find("dup")).get().extracting(Profile::getDescription).isEqualTo("second");
  }

  @Test
  @DisplayName("list() 返回全部已注册 Profile")
  void listReturnsAll() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile a = new Profile();
    a.setName("a");
    Profile b = new Profile();
    b.setName("b");
    registry.register(a);
    registry.register(b);

    assertThat(registry.list()).hasSize(2);
  }

  /**
   * US3 守点:两条来源同规矩。 启动扫描(scanAndRegister→deriveProfile)与运行时注册(手动 deriveProfile→register)对同一缺
   * provider 的非法配置,抛同一异常类型(IllegalArgumentException)+ 同一消息——因为都走同一段 AgentLoader.deriveProfile 校验。
   */
  @Test
  @DisplayName("缺 provider 的非法配置:运行时注册与启动扫描抛同一异常类型+同一消息")
  void runtimeRegisterInvalidProfile_throwsSameAsScan() throws Exception {
    AgentLoader loader = new AgentLoader(agentsDir);
    Path dir = agentsDir.resolve("broken");
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve("AGENT.md"),
        """
                ---
                description: "no provider"
                ---
                # body
                """);
    var parsed = loader.parseAgentMd(dir.resolve("AGENT.md"));

    // 启动扫描路径:scanAndRegister 内部对坏 Agent 调 deriveProfile,失败被 try/catch 吞(不阻断)。
    // 直接调 deriveProfile 拿到的是它抛的异常(同一段校验代码)。
    assertThatThrownBy(() -> loader.deriveProfile("broken", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent 'broken': missing required field 'provider.name'");

    // 运行时注册路径:业务方/下节 API 调同一段 deriveProfile + register;同一异常。
    assertThatThrownBy(() -> loader.deriveProfile("broken", parsed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent 'broken': missing required field 'provider.name'");
  }
}
