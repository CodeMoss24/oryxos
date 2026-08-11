package com.oryxos.web.controller;

import com.oryxos.core.memory.MemoryService;
import com.oryxos.web.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Memory 查询 Controller。 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

  private final MemoryService memoryService;

  public MemoryApiController(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  @GetMapping
  public ApiResponse<Map<String, String>> get() {
    return ApiResponse.ok(Map.of("memory", memoryService.buildContext(null)));
  }
}
