package com.oryxos.web.controller;

import com.oryxos.core.react.ProviderPort;
import com.oryxos.web.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统状态 Controller(health / info)。
 *
 * <p>info 多带一份各 Provider 的实时连通状态(经 ProviderPort 探活,探测失败/未配置安全返回"断开", 不拖垮端点本身)。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

  private final ProviderPort providerPort;

  public SystemApiController(ProviderPort providerPort) {
    this.providerPort = providerPort;
  }

  @GetMapping("/health")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.ok(Map.of("status", "UP"));
  }

  @GetMapping("/info")
  public ApiResponse<Map<String, Object>> info() {
    Map<String, Boolean> providers = providerPort.connectivity();
    return ApiResponse.ok(
        Map.of(
            "name",
            "OryxOS",
            "version",
            "1.0.0-SNAPSHOT",
            "java",
            System.getProperty("java.version"),
            "time",
            java.time.Instant.now().toString(),
            "providers",
            providers));
  }
}
