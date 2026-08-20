package com.oryxos.web;

import com.oryxos.core.exception.ProfileValidationException;
import com.oryxos.core.exception.SandboxViolationException;
import com.oryxos.core.skill.SkillReferencedException;
import com.oryxos.web.controller.dto.SkillReferenceConflictView;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常处理(异常出口只有一个,各 Controller 不自己拼错误响应)。
 *
 * <p>映射口径(课件草图,经用户确认):400 参数错误、404 资源不存在、503 Provider 故障、504 调用超时、 500 兜底统一话术。兜底不把 e.getMessage()
 * 原样吐给外部——内部异常细节进日志,对外只说"内部错误"。 既有 IllegalArgumentException→400 映射保留不动,IllegalStateException 自 404
 * 迁改为 503。
 *
 * <p>对齐参考版管理台补齐的映射:ProfileValidationException→400、NoResourceFoundException→404、
 * SandboxViolationException→4031(沙箱违规 不再 500,HTTP 状态 403)。SkillReferencedException→409 随批 4 skill
 * 栈一起接入。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({
    InvalidRequestException.class,
    IllegalArgumentException.class,
    ProfileValidationException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorCode.BAD_REQUEST.code, e.getMessage()));
  }

  @ExceptionHandler({
    SessionNotFoundException.class,
    ResourceNotFoundException.class,
    NoResourceFoundException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ErrorCode.NOT_FOUND.code, e.getMessage()));
  }

  @ExceptionHandler(SandboxViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleSandboxViolation(SandboxViolationException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(ErrorCode.SANDBOX_VIOLATION.code, e.getMessage()));
  }

  /**
   * 409 — Skill 仍被 Agent 引用,删除被拦(批 4 skill 栈;SkillReferencedException 继承 IllegalStateException,此
   * handler 更具体,优先于 503 映射)。
   */
  @ExceptionHandler(SkillReferencedException.class)
  public ResponseEntity<ApiResponse<SkillReferenceConflictView>> handleSkillReferenced(
      SkillReferencedException ex) {
    SkillReferenceConflictView data =
        SkillReferenceConflictView.from(ex.skillName(), ex.references());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ApiResponse<>(
                HttpStatus.CONFLICT.value(), ex.getMessage(), data, java.time.Instant.now()));
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
