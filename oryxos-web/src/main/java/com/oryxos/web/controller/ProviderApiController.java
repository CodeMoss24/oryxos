package com.oryxos.web.controller;

import com.oryxos.core.provider.ProviderRegistry;
import com.oryxos.core.provider.ProviderRegistry.ProviderDef;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.ProviderSaveRequest;
import com.oryxos.web.dto.ProviderUpdateRequest;
import com.oryxos.web.dto.ProviderView;
import com.oryxos.web.exception.ResourceNotFoundException;
import com.oryxos.web.provider.ProviderModelsService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider 动态注册表 CRUD:LLM 接入点运行时增删改,管理台管、运行时按名动态建 ChatModel(指纹缓存,改配置免重启生效)。
 *
 * <p>薄转发给 {@link ProviderRegistry}。错误码沿用既有:定义非法 → 400;不存在 → 404;统一 {@code ApiResponse} 信封。 名为
 * {@code mock} 的 provider 免 base-url(走内置假模型),其余必须有 base-url。
 */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderApiController {

  private static final String MOCK = "mock";

  private final ProviderRegistry registry;
  private final ProviderModelsService modelsService;

  public ProviderApiController(ProviderRegistry registry, ProviderModelsService modelsService) {
    this.registry = registry;
    this.modelsService = modelsService;
  }

  @PostMapping
  public ApiResponse<ProviderView> create(@RequestBody ProviderSaveRequest req) {
    String name = req == null ? null : req.name();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("provider 名为空"); // → 400
    }
    if (registry.exists(name)) {
      throw new IllegalArgumentException("provider 已存在: " + name); // → 400
    }
    validate(name, req.baseUrl());
    ProviderDef saved =
        registry.save(new ProviderDef(name, req.apiKey(), req.baseUrl(), req.description()));
    return ApiResponse.ok(ProviderView.from(saved));
  }

  @GetMapping
  public ApiResponse<List<ProviderView>> list() {
    return ApiResponse.ok(registry.list().stream().map(ProviderView::from).toList());
  }

  @GetMapping("/{name}")
  public ApiResponse<ProviderView> get(@PathVariable String name) {
    return ApiResponse.ok(
        registry
            .find(name)
            .map(ProviderView::from)
            .orElseThrow(() -> new ResourceNotFoundException("provider 不存在: " + name)));
  }

  @PutMapping("/{name}")
  public ApiResponse<ProviderView> update(
      @PathVariable String name, @RequestBody ProviderUpdateRequest req) {
    ProviderDef existing =
        registry
            .find(name)
            .orElseThrow(() -> new ResourceNotFoundException("provider 不存在: " + name)); // → 404
    validate(name, req.baseUrl());
    // 前端编辑表单回填的是掩码值;提交掩码 = 未修改,保留原 key——否则打码值会覆盖真实 key
    String apiKey =
        ProviderView.mask(existing.apiKey()).equals(req.apiKey())
            ? existing.apiKey()
            : req.apiKey();
    ProviderDef saved =
        registry.save(new ProviderDef(name, apiKey, req.baseUrl(), req.description()));
    return ApiResponse.ok(ProviderView.from(saved));
  }

  /**
   * 列出某 provider 下的模型 id(服务端代理 OpenAI 兼容的 {@code /models} 端点,避免浏览器直连暴露 api-key)。 provider
   * 不存在→404;端点不可达/缺 base-url→503(统一 ApiResponse 信封)。
   */
  @GetMapping("/{name}/models")
  public ApiResponse<List<String>> models(@PathVariable String name) {
    return ApiResponse.ok(modelsService.listModels(name));
  }

  @DeleteMapping("/{name}")
  public ApiResponse<Void> delete(@PathVariable String name) {
    if (!registry.exists(name)) {
      throw new ResourceNotFoundException("provider 不存在: " + name); // → 404
    }
    registry.delete(name);
    return ApiResponse.ok(null);
  }

  /** 非 mock 的 provider 必须有 base-url(否则运行时建不出 OpenAI 兼容 ChatModel)。 */
  private static void validate(String name, String baseUrl) {
    if (MOCK.equals(name)) {
      return;
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("provider " + name + " 缺少 base-url");
    }
  }
}
