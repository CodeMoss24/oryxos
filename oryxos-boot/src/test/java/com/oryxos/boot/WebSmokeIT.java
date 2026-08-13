package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Web 冒烟(@SpringBootTest 起真实上下文,不依赖模型):/health、/info、/profiles、/tools 真实链路可达—— 验证 Bean 装配和扫描范围没炸。18
 * 节那个 "Found 0 repositories" 的坑如果在 web 链路复发,这里第一时间红。
 *
 * <p>补强(analyze C1):不存在的 Agent 名 invoke → 404,同样不依赖模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@DisplayName("WebSmokeIT — 真实上下文冒烟:四端点可达 + invoke 404,不依赖模型")
class WebSmokeIT {

  @Autowired private TestRestTemplate rest;

  @Test
  @DisplayName("health 返回 200 与统一信封")
  void healthReturns200WithEnvelope() {
    ResponseEntity<Map> resp = rest.getForEntity("/api/v1/health", Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).containsKeys("code", "message", "data", "timestamp");
    assertThat(resp.getBody().get("code")).isEqualTo(200);
  }

  @Test
  @DisplayName("info 返回 200 且带 providers 连通状态字段")
  void infoReturns200WithProvidersField() {
    ResponseEntity<Map> resp = rest.getForEntity("/api/v1/info", Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(data).containsKey("providers"); // 探测失败/未配置也要安全返回,端点恒 200
  }

  @Test
  @DisplayName("profiles 返回 200 且 data 为数组")
  void profilesReturns200WithArrayData() {
    ResponseEntity<Map> resp = rest.getForEntity("/api/v1/profiles", Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody().get("data")).isInstanceOf(java.util.List.class);
  }

  @Test
  @DisplayName("tools 返回 200 且 data 为数组")
  void toolsReturns200WithArrayData() {
    ResponseEntity<Map> resp = rest.getForEntity("/api/v1/tools", Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody().get("data")).isInstanceOf(java.util.List.class);
  }

  @Test
  @DisplayName("不存在的 Agent invoke 返回 404(统一异常出口)")
  void invokeUnknownAgentReturns404() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> body = new HttpEntity<>("{\"content\":\"hi\"}", headers);

    ResponseEntity<Map> resp =
        rest.postForEntity("/api/v1/agents/no-such-agent/invoke", body, Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().get("code")).isEqualTo(404);
  }

  @Test
  @DisplayName("管理台 /admin 与子路径均回落 index.html(SPA 不 404)")
  void adminRootAndSubpathFallBackToIndexHtml() {
    ResponseEntity<String> root = rest.getForEntity("/admin/", String.class);
    assertThat(root.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(root.getBody()).contains("OryxOS 管理台");

    ResponseEntity<String> subpath = rest.getForEntity("/admin/sessions", String.class);
    assertThat(subpath.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(subpath.getBody()).contains("OryxOS 管理台");
  }
}
