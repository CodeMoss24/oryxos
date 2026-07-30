package com.oryxos.web.controller;

import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统状态 Controller(health / info)。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "name", "OryxOS",
                "version", "1.0.0-SNAPSHOT",
                "java", System.getProperty("java.version"),
                "time", java.time.Instant.now().toString()));
    }
}
