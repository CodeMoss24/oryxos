package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP 域名白名单配置:http.allowed_domains(精确匹配 + *. 通配符,带点号边界)。空 = 什么都不允许,而非不校验。 */
@ConfigurationProperties(prefix = "http")
public record HttpSandboxProperties(List<String> allowedDomains) {}
