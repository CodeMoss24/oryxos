package com.oryxos.web.dto;

import java.time.Instant;

/** 统一响应信封。成功与错误共用一个信封。成功 code=0(对齐参考版管理台契约)。 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(0, "ok", data, Instant.now());
  }

  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, message, null, Instant.now());
  }
}
