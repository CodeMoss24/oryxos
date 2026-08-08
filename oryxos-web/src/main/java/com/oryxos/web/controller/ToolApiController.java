package com.oryxos.web.controller;

import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.web.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tool 信息 Controller。 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {

  private final ToolRegistry toolRegistry;

  public ToolApiController(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  @GetMapping
  public ApiResponse<List<Map<String, String>>> list() {
    List<Map<String, String>> result =
        toolRegistry.list().stream()
            .map(
                t ->
                    Map.of(
                        "name", t.getName(),
                        "description", t.getDescription()))
            .toList();
    return ApiResponse.ok(result);
  }
}
