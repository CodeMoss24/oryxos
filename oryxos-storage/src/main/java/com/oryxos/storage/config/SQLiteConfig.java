package com.oryxos.storage.config;

import org.springframework.context.annotation.Configuration;

/** SQLite + JPA 配置占位。 数据源和 Hibernate 配置在 application.yaml 里,这里保留类供未来扩展(如手动建表脚本注册)。 */
@Configuration
public class SQLiteConfig {}
