package com.oryxos.web;

import com.oryxos.web.exception.AgentTimeoutException;
import com.oryxos.web.exception.InvalidRequestException;
import com.oryxos.web.exception.ProviderUnavailableException;
import com.oryxos.web.exception.ResourceNotFoundException;
import com.oryxos.web.exception.SessionNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 测试专用探针(GlobalExceptionHandlerTest 切片内装载):每类异常对应一个路径,只用于验证统一异常出口。 */
@RestController
public class ExceptionProbeController {

  @GetMapping("/probe/{type}")
  void probe(@PathVariable String type) {
    switch (type) {
      case "session":
        throw new SessionNotFoundException("session not found: s-1");
      case "resource":
        throw new ResourceNotFoundException("agent not found: x");
      case "provider":
        throw new ProviderUnavailableException("provider down: deepseek");
      case "illegalState":
        throw new IllegalStateException("provider unavailable");
      case "illegalArgument":
        throw new IllegalArgumentException("bad argument");
      case "invalidRequest":
        throw new InvalidRequestException("消息为空或超过 32KB");
      case "timeout":
        throw new AgentTimeoutException("agent call timed out after 60s");
      default:
        // 未映射异常走 500 兜底:message 里的内部细节一个字都不许出现在响应体
        throw new RuntimeException("jdbc:sqlite:/data/oryxos.db connect failed");
    }
  }
}
