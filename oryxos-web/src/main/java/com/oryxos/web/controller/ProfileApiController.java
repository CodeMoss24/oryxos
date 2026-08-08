package com.oryxos.web.controller;

import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.web.dto.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profile 查询 Controller。 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {

  private final ProfileRegistry profileRegistry;

  public ProfileApiController(ProfileRegistry profileRegistry) {
    this.profileRegistry = profileRegistry;
  }

  @GetMapping
  public ApiResponse<List<Map<String, String>>> list() {
    List<Map<String, String>> result =
        profileRegistry.list().stream()
            .map(
                p ->
                    Map.of(
                        "name", p.getName() == null ? "" : p.getName(),
                        "description", p.getDescription() == null ? "" : p.getDescription()))
            .toList();
    return ApiResponse.ok(result);
  }
}
