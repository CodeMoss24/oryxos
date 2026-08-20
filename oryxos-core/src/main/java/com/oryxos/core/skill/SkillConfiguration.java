package com.oryxos.core.skill;

import com.oryxos.core.runtime.OryxOsRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Skill 栈装配(镜像参考版 OryxOsRuntime 的 wiring,不依赖装配中心):启动全量扫 {@code .oryxos/skills/} 建全局索引,SkillService
 * 播种内置 Skill(幂等——用户改过不覆盖),迁移服务处理旧 frontmatter skills 字段。
 *
 * <p>skill 各实现类保持纯 POJO 零 Spring 依赖(与 16/17 节交付的类同风格),bean 装配集中在本文件。
 */
@Configuration
public class SkillConfiguration {

  @Bean
  SkillStore skillStore() {
    return new SkillStore(OryxOsRuntime.workspaceRoot());
  }

  @Bean
  SkillLoader skillLoader() {
    return new SkillLoader(OryxOsRuntime.workspaceRoot().resolve("skills"));
  }

  /** 启动全量扫 .oryxos/skills/ 建全局 Skill 索引(CRUD 与它共用同一份注册表)。 */
  @Bean
  SkillRegistry skillRegistry(SkillLoader skillLoader) {
    return skillLoader.loadAll();
  }

  @Bean
  AgentSkillBindingService agentSkillBindingService(SkillLoader skillLoader) {
    return new AgentSkillBindingService(OryxOsRuntime.workspaceRoot(), skillLoader);
  }

  @Bean
  SkillCatalog skillCatalog(SkillRegistry skillRegistry) {
    return new InstalledSkillCatalog(skillRegistry);
  }

  /** 全局 Skill 库 CRUD;启动播种内置 Skill(report-format,幂等——用户改过不覆盖)。 */
  @Bean
  SkillService skillService(
      SkillStore skillStore,
      SkillRegistry skillRegistry,
      SkillLoader skillLoader,
      AgentSkillBindingService skillBindings) {
    SkillService service = new SkillService(skillStore, skillRegistry, skillLoader, skillBindings);
    service.seedBuiltins();
    return service;
  }

  /** 显式启动依赖结果:旧 frontmatter skills 迁移在 profile 扫描前完成(reconcile 出 binding issues)。 */
  @Bean
  AgentSkillStartupReport agentSkillStartupReport(
      SkillService ignoredSeededSkills, AgentSkillBindingService skillBindings) {
    return new AgentSkillMigrationService(OryxOsRuntime.workspaceRoot(), skillBindings)
        .migrateAll();
  }
}
