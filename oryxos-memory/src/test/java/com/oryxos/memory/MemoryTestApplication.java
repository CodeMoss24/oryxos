package com.oryxos.memory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 测试切片配置:实体/仓库落在 oryxos-storage 的固定扫描包内(boot 的 @EntityScan/@EnableJpaRepositories
 * 同口径),这里显式指向以便 @DataJpaTest 找到它们。
 */
@SpringBootApplication
@EntityScan("com.oryxos.storage.entity")
@EnableJpaRepositories("com.oryxos.storage.repository")
public class MemoryTestApplication {}
