package com.oryxos.web.controller;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.InfoView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统状态 Controller(health / info)。
 *
 * <p>info 对齐参考版形状:application + 已加载 Profile 引用到的 Provider 名单(core-only 可见口径;核心阶段不做 live
 * 探活——连通性以"已配置"为准,真实探活留扩展阶段)。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

  private final ProfileRegistry profileRegistry;

  public SystemApiController(ProfileRegistry profileRegistry) {
    this.profileRegistry = profileRegistry;
  }

  @GetMapping("/health")
  public ApiResponse<Map<String, String>> health() {
    return ApiResponse.ok(Map.of("status", "ok"));
  }

  @GetMapping("/info")
  public ApiResponse<InfoView> info() {
    List<String> providers =
        profileRegistry.list().stream()
            .map(Profile::getProvider)
            .filter(p -> p != null && p.name() != null)
            .map(Profile.Provider::name)
            .distinct()
            .sorted()
            .toList();
    return ApiResponse.ok(new InfoView("oryxos", providers));
  }
}
