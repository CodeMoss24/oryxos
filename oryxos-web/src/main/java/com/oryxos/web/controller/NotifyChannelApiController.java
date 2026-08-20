package com.oryxos.web.controller;

import com.oryxos.core.notify.NotifyChannelDef;
import com.oryxos.core.notify.NotifyChannelRegistry;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.CreateNotifyChannelRequest;
import com.oryxos.web.dto.NotifyChannelView;
import com.oryxos.web.dto.UpdateNotifyChannelRequest;
import com.oryxos.web.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知渠道注册表 CRUD:全局命名的 notify 出口,管理台增删改查、Agent 按名引用。
 *
 * <p>薄转发给 {@link NotifyChannelRegistry}(依赖倒置——web 只认这个 core 契约,具体实现在 storage)。错误码沿用既有口径: 名字冲突 /
 * 定义非法 → 400({@code IllegalArgumentException});不存在 → 404({@code ResourceNotFoundException}); 统一
 * {@code ApiResponse} 信封。type 必须是已装配的渠道实现之一(webhook/feishu/wecom/dingtalk)。
 */
@RestController
@RequestMapping("/api/v1/notify-channels")
public class NotifyChannelApiController {

  private static final Set<String> SUPPORTED_TYPES =
      Set.of("webhook", "feishu", "wecom", "dingtalk");

  private final NotifyChannelRegistry registry;

  public NotifyChannelApiController(NotifyChannelRegistry registry) {
    this.registry = registry;
  }

  @PostMapping
  public ApiResponse<NotifyChannelView> create(@RequestBody CreateNotifyChannelRequest req) {
    String name = req == null ? null : req.name();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("渠道名为空"); // → 400
    }
    if (registry.exists(name)) {
      throw new IllegalArgumentException("通知渠道已存在: " + name); // → 400
    }
    validate(req.type(), req.url());
    NotifyChannelDef saved =
        registry.save(new NotifyChannelDef(name, req.type(), req.url(), req.description()));
    return ApiResponse.ok(NotifyChannelView.from(saved));
  }

  @GetMapping
  public ApiResponse<List<NotifyChannelView>> list() {
    return ApiResponse.ok(registry.list().stream().map(NotifyChannelView::from).toList());
  }

  @GetMapping("/{name}")
  public ApiResponse<NotifyChannelView> get(@PathVariable String name) {
    return ApiResponse.ok(
        registry
            .find(name)
            .map(NotifyChannelView::from)
            .orElseThrow(() -> new ResourceNotFoundException("通知渠道不存在: " + name)));
  }

  @PutMapping("/{name}")
  public ApiResponse<NotifyChannelView> update(
      @PathVariable String name, @RequestBody UpdateNotifyChannelRequest req) {
    if (!registry.exists(name)) {
      throw new ResourceNotFoundException("通知渠道不存在: " + name); // → 404
    }
    validate(req.type(), req.url());
    NotifyChannelDef saved =
        registry.save(new NotifyChannelDef(name, req.type(), req.url(), req.description()));
    return ApiResponse.ok(NotifyChannelView.from(saved));
  }

  @DeleteMapping("/{name}")
  public ApiResponse<Void> delete(@PathVariable String name) {
    if (!registry.exists(name)) {
      throw new ResourceNotFoundException("通知渠道不存在: " + name); // → 404
    }
    registry.delete(name);
    return ApiResponse.ok(null);
  }

  private static void validate(String type, String url) {
    if (type == null || !SUPPORTED_TYPES.contains(type)) {
      throw new IllegalArgumentException("不支持的渠道类型: " + type + "(支持: " + SUPPORTED_TYPES + ")");
    }
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("渠道 url 为空");
    }
  }
}
