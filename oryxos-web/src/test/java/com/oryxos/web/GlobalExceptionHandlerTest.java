package com.oryxos.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 统一异常出口回归(课件验收 harness):每类异常映射到约定状态码;响应体都是统一 ApiResponse 信封; 500 兜底响应里不含内部异常的 message(不泄漏)。
 *
 * <p>用测试专用探针 Controller 逐类抛出异常,@WebMvcTest 自动装载真实 GlobalExceptionHandler。
 */
// springdoc 2.x 与本模块的 @WebMvcTest 默认扫描有摩擦(Controller/Advice 不入选),显式 @Import 规避
@WebMvcTest
@Import({ExceptionProbeController.class, GlobalExceptionHandler.class})
@DisplayName("GlobalExceptionHandlerTest — 每类异常映射到约定状态码,500 不泄漏")
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("SessionNotFoundException 映射 404,响应为统一信封")
  void sessionNotFoundMappedTo404() throws Exception {
    mockMvc
        .perform(get("/probe/session"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("session not found: s-1"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("ResourceNotFoundException 映射 404(Agent 等资源不存在)")
  void resourceNotFoundMappedTo404() throws Exception {
    mockMvc
        .perform(get("/probe/resource"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("agent not found: x"));
  }

  @Test
  @DisplayName("ProviderUnavailableException 映射 503(Provider 故障)")
  void providerUnavailableMappedTo503() throws Exception {
    mockMvc
        .perform(get("/probe/provider"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value(503))
        .andExpect(jsonPath("$.message").value("provider down: deepseek"));
  }

  @Test
  @DisplayName("IllegalStateException 迁改为 503(既有映射自 404 迁改,经用户确认)")
  void illegalStateMappedTo503() throws Exception {
    mockMvc
        .perform(get("/probe/illegalState"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value(503));
  }

  @Test
  @DisplayName("InvalidRequestException 映射 400(消息空/超 32KB)")
  void invalidRequestMappedTo400() throws Exception {
    mockMvc
        .perform(get("/probe/invalidRequest"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("消息为空或超过 32KB"));
  }

  @Test
  @DisplayName("IllegalArgumentException 映射 400(既有映射保留不动)")
  void illegalArgumentMappedTo400() throws Exception {
    mockMvc
        .perform(get("/probe/illegalArgument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("AgentTimeoutException 映射 504(调用超 60 秒)")
  void agentTimeoutMappedTo504() throws Exception {
    mockMvc
        .perform(get("/probe/timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value(504))
        .andExpect(jsonPath("$.message").value("agent call timed out after 60s"));
  }

  @Test
  @DisplayName("内部异常细节_绝不能出现在500响应里——统一话术且不泄漏(课件关键回归)")
  void internalErrorDetailsNeverLeakInto500Response() throws Exception {
    mockMvc
        .perform(get("/probe/internal"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("内部错误"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(not(containsString("jdbc:sqlite"))));
  }

  @Test
  @DisplayName("所有错误响应共用统一信封:code/message/timestamp 齐备")
  void errorResponsesShareUnifiedEnvelope() throws Exception {
    mockMvc
        .perform(get("/probe/internal"))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.timestamp").exists());
  }
}
