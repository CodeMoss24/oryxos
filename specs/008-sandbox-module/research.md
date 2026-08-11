# Research: Sandbox 沙箱模块

**Date**: 2026-08-11 | **Feature**: 008-sandbox-module | **Branch**: 024-lesson24-sandbox

## 决策记录

### D1: 依赖核实——无新增第三方依赖

- **Decision**: 实现依赖全部在既有 pom 内：`@ConfigurationProperties` 来自 oryxos-tool 已有的 `spring-boot-starter`（间接带 spring-boot-autoconfigure）；测试用 MockWebServer `com.squareup.okhttp3:mockwebserver:4.12.0`（test scope）与 `spring-boot-starter-test`（JUnit 5 + Mockito），均已在 oryxos-tool/pom.xml 声明。
- **Rationale**: 课件 §3.2 的三 props record 与测试 harness 依赖都在本地依赖中核实存在，无软门禁触发。
- **Alternatives considered**: 无（未引入任何新依赖）。

### D2: 配置绑定注册 glue——`@EnableConfigurationProperties` 放 ToolConfiguration

- **Decision**: 三个 `@ConfigurationProperties` record 通过 oryxos-tool 的 `ToolConfiguration` 上加 `@EnableConfigurationProperties({FileSandboxProperties.class, ShellSandboxProperties.class, HttpSandboxProperties.class})` 注册为 Bean。
- **Rationale**: 课件的 record 没有 `@Component`，Spring 不会自动绑定——运行时 `WhitelistSandbox`（@Component）构造器注入三个 props 需要它们先成为 Bean。放 ToolConfiguration（已是 @Configuration、扫描 com.oryxos 覆盖）可把绑定 glue 留在 oryxos-tool 内，boot 模块零改动；课件交付物没点名 boot 改造，保持 diff 最小。
- **Alternatives considered**: (a) `@ConfigurationPropertiesScan` 加在 `OryxOsApplication`（boot 模块）——可行但要动 boot 且扩大扫描面；(b) 给 record 加 `@Component`——偏离课件字面代码。选 @EnableConfigurationProperties 最贴合课件形态。

### D3: `matchesDomain` 点号边界修复（既有漏洞）

- **Decision**: 通配符匹配改为 `host.endsWith(pattern.substring(1))`（`*.example.com` 保留点号 → `.example.com` 结尾才命中）。
- **Rationale**: 既有实现用 `substring(2)` 丢掉点号 → `"evil-example.com".endsWith("example.com")` 为真，形似域名可绕过。课件 §四 harness 与 spec FR-003 明确守这个回归点（api.example.com 放行、evil-example.com 拒绝）。
- **Alternatives considered**: 正则 `^[^.]+\.example\.com$`——课件给了逐字代码，用等价的 endsWith 点号边界实现保骨架同构。

### D4: 相对白名单根转绝对（用户澄清确认）

- **Decision**: `WhitelistSandbox` 构造时对每个 allowed root 做 `Path.of(...).normalize().toAbsolutePath()`（相对路径按当前工作目录解析）；目标路径 `Path.of(raw).normalize().toAbsolutePath()`；两者基准一致后 `startsWith`。
- **Rationale**: 课件只把目标转绝对、根保持原样——`allowed-paths: .oryxos`（相对）将永远匹配不上绝对目标，运行时读 `.oryxos/agents/...` 全拦。用户已确认此方案（spec Clarifications 2026-08-11）。
- **Alternatives considered**: 配置强制绝对路径（便携性差，换机器要改）；按 `oryxos.workspace` 解析（与 workspace 相对值耦合，绕回同问题）。

### D5: 既有骨架拉回规范形态

- **Decision**: `SandboxAction`（record）与 `ActionType`（enum）从 `Sandbox` 接口嵌套提为顶层独立文件；`WhitelistSandbox` 构造器从 `(String csv × 3)` 改为 `(FileSandboxProperties, ShellSandboxProperties, HttpSandboxProperties)`；配置键从 `oryxos.sandbox.file.allowed-paths`（@Value CSV）改为顶层 `file.allowed_paths` / `shell.allowed_commands` / `http.allowed_domains`。
- **Rationale**: 课件 §3.1/§3.2 交付物逐字给顶层类型与 props record 形态；TechnicalSolution §6.7 权威文档同样写 `file.allowed_paths` 三键——既有 `oryxos.sandbox.*` 是对权威文档的偏离。用户已确认按课件交付物拉回。
- **Alternatives considered**: 保留嵌套形态（与课件交付物不符）；保留 oryxos.sandbox.* 键（偏离 TechnicalSolution 权威文档）。

### D6: "IO 没有发生"接线回归——副作用断言，不新增测试接缝

- **Decision**: 接线回归不往四个 Tool 构造器塞 mock 执行器，改用可观察副作用证明危险动作未发生：FileTools 白名单外写文件后 `Files.notExists` 断言；ShellTools 白名单外命令后目标文件仍在断言；HttpTools 白名单拒绝 + 目标指向 MockWebServer，`server.getRequestCount() == 0`；NotifyTools 真实拒绝沙箱 + mock adapter `verify(adapter, never())`。
- **Rationale**: 课件建议"mock 底层执行器 + verify never"，但给 Tool 加可注入执行器会改前序节公共构造器、超出课件"改造点"范围。副作用断言与课件"只断言抛异常不够、得证明危险动作真的没跑"的断言语义逐条一致（spec Assumptions 已记录）。
- **Alternatives considered**: 构造器注入 HttpClient/ProcessBuilder（改公共 API，否决）；`@VisibleForTesting` setter（同样动公共面，否决）。

## 技术要点核实

- **@ConfigurationProperties 可用性**: `org.springframework.boot.context.properties.ConfigurationProperties` 注解类位于 spring-boot 核心 jar，oryxos-tool 的 `spring-boot-starter` 依赖已覆盖；relaxed binding 使 `allowedPaths` ↔ `allowed_paths` 互认。
- **Spring 扫包**: boot 模块 `OryxOsApplication` 用 `@SpringBootApplication(scanBasePackages = "com.oryxos")`，sandbox 包内的 `@Component` 与 ToolConfiguration 均可被扫到。
- **既有测试装配**: `ToolTestFixture` 用 AnnotationConfigApplicationContext 手装配 `WhitelistSandbox`（三字符串构造器）——改造后需同步改为 props 形态（构造三个 props 实例传入），`HttpToolsTest.executeAgainstMock` 同样处理。
- **开关语法**: 实现用 Java 21 增强 switch 的箭头分支（`case FILE_READ, FILE_WRITE -> ...`）已在既有代码使用并通过构建门禁；新增代码避开 `default ->` 等 P3C/ASM 敏感形态。
