package com.oryxos.web.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Web 层配置:CORS 核心阶段全开(TS §7.4)+ /admin SPA 前端路由回落。
 *
 * <p>回落策略:请求的静态文件存在则直出,否则回落到 index.html——/api/v1/** 由 Controller 精确接管, 不受影响;不用 forward
 * view-controller 方案(那会吞掉所有 /admin/** 包括静态资源,造成循环转发)。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/v1/**").allowedOriginPatterns("*").allowedMethods("*");
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // ResourceHttpRequestHandler 对空路径(即 /admin/ 本身)不咨询 resolver 链,根路径必须精确接管:
    // /admin 重定向到 /admin/,/admin/ 转发到 index.html
    registry.addViewController("/admin").setViewName("redirect:/admin/");
    registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    // 根路径:欢迎页机制找不到 static 根下的 index.html(管理台在 /admin/ 下),
    // 不接管会落到 NoResourceFoundException 被 GlobalExceptionHandler 报成 500
    registry.addViewController("/").setViewName("redirect:/admin/");
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/admin/**")
        .addResourceLocations("classpath:/static/admin/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws IOException {
                // 目录请求(空路径/尾斜杠)与未命中一律回落 index.html;文件存在且可读则直出。
                // 注意不能用 isFile():fat JAR 里嵌套 jar 的资源对它永远返回 false,会误伤静态文件。
                if (!resourcePath.isEmpty() && !resourcePath.endsWith("/")) {
                  Resource requested = location.createRelative(resourcePath);
                  if (requested.exists() && requested.isReadable()) {
                    return requested;
                  }
                }
                return new ClassPathResource("/static/admin/index.html");
              }
            });
  }
}
