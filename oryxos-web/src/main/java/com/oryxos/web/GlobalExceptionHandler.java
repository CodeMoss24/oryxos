package com.oryxos.web;

import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.exception.AgentTimeoutException;
import com.oryxos.web.exception.InvalidRequestException;
import com.oryxos.web.exception.ProviderUnavailableException;
import com.oryxos.web.exception.ResourceNotFoundException;
import com.oryxos.web.exception.SessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理(异常出口只有一个,各 Controller 不自己拼错误响应)。
 *
 * <p>映射口径(课件草图,经用户确认):400 参数错误、404 资源不存在、503 Provider 故障、504 调用超时、 500 兜底统一话术。兜底不把 e.getMessage()
 * 原样吐给外部——内部异常细节进日志,对外只说"内部错误"。 既有 IllegalArgumentException→400 映射保留不动,IllegalStateException 自 404
 * 迁改为 503。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({InvalidRequestException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.BAD_REQUEST.code, e.getMessage()));
  }

  @ExceptionHandler({SessionNotFoundException.class, ResourceNotFoundException.class})
  public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ErrorCode.NOT_FOUND.code, e.getMessage()));
  }

  @ExceptionHandler({IllegalStateException.class, ProviderUnavailableException.class})
  public ResponseEntity<ApiResponse<Void>> handleProviderDown(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(ErrorCode.SERVICE_UNAVAILABLE.code, e.getMessage()));
  }

  @ExceptionHandler(AgentTimeoutException.class)
  public ResponseEntity<ApiResponse<Void>> handleTimeout(AgentTimeoutException e) {
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .body(ApiResponse.error(ErrorCode.GATEWAY_TIMEOUT.code, e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleInternal(Exception e) {
    log.error("Unhandled exception", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, ErrorCode.INTERNAL_ERROR.message));
  }
}
