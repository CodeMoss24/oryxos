---
name: oryxos-init
description: >-
  初始化 OryxOS（或同类 JDK 21 + Spring Boot 3.x 企业级单体）的工程地基：Maven 多模块骨架、
  结构化日志、Actuator + Prometheus 监控、Spring MVC + 虚拟线程、springdoc OpenAPI、
  统一响应体与全局异常/错误码、Google 格式 + 阿里编码规约（Spotless + 阿里 P3C + Checkstyle）、
  代码安全检查（SpotBugs + Find Security Bugs + PMD + OWASP Dependency-Check），以及 CI 与 pre-commit。
  当用户要「初始化项目 / 搭工程骨架 / 起脚手架 / 加日志监控 / 加开发规范 / 加代码安全检查」时使用。
---
# OryxOS 项目初始化 Skill

把"工程地基"一次性、标准化地装好——业务逻辑（五大核心能力）不在本 skill 范围内。

## 什么时候用

- 新建 OryxOS 仓库、或给空仓库起工程骨架时
- 要给项目补齐日志 / 监控 / API 规范 / 开发规范 / 安全检查时
- 任何 JDK 21 + Spring Boot 3.x 的企业级单体，想一次到位地装好工程地基时

## 不做什么（边界）

- 不实现五大核心能力（Provider / ReAct / Memory / Tool / Web）——那是业务模块，走 Spec-Kit 的 user story 拆解
- 不硬编码任何密钥 / token / API key——一律用环境变量占位（`${ENV_VAR}`）
- 不替换已存在的业务代码；只新增基础设施与配置

## 前置约定（来自 OryxOS constitution）

实施前确认这些硬约束，写进配置：
- JDK 21、Spring Boot 3.x、Maven 多模块、单二进制（fat JAR）部署
- HTTP 层用 Spring MVC + Java 21 虚拟线程（不引入响应式）
- 持久化 SQLite + Spring Data JPA；长期记忆走 MEMORY.md（本 skill 只配数据源，不建业务表）
- 审计表 `tool_invocations` / `llm_calls` 的建表脚本预留位（day one 落库）
- 代码必须过 Google 格式 + 阿里编码规约 + 安全扫描，才能合并

---

## 初始化步骤（按顺序执行，每步完成后 `git commit`）

### 0. 确认参数
向用户确认：`groupId`（com.oryxos）、根 `artifactId`（oryxos-parent）、模块清单（默认 9 模块）、端口（默认 8080）、JDK（21）。

### 1. Maven 多模块骨架（已有）
确认 9 个模块 `pom.xml` 齐全：
`oryxos-core`、`oryxos-provider`、`oryxos-memory`、`oryxos-tool`、`oryxos-web`、
`oryxos-storage`、`oryxos-boot`、`oryxos-cli`、`oryxos-channel-cli`。
`oryxos-boot` 为启动模块（含 `main`），打 fat JAR。

### 2. 基础依赖与版本管理（已有，补充缺失）
父 `pom.xml` 已配置 Spring Boot BOM、Spring AI Alibaba BOM、SQLite JDBC、Picocli、SnakeYAML、springdoc。
**补充以下依赖到父 pom：**

```xml
<!-- logstash-logback-encoder（结构化 JSON 日志）-->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>${logstash-encoder.version}</version>
</dependency>

<!-- Actuator + Prometheus -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- springdoc-openapi（已有）-->
```

**补充以下属性到父 pom `<properties>`：**
```xml
<logstash-encoder.version>8.0</logstash-encoder.version>
<spotless.version>2.43.0</spotless.version>
<p3c.version>2.2.0</p3c.version>
<pmd.version>3.26.0</pmd.version>
<spotbugs.version>4.8.6.6</spotbugs.version>
<findsecbugs.version>1.12.0</findsecbugs.version>
<dependency-check.version>11.1.0</dependency-check.version>
<checkstyle.version>10.21.2</checkstyle.version>
<checkstyle.plugin.version>3.6.0</checkstyle.plugin.version>
```

**补充插件到父 pom `<pluginManagement>`：**
- `spotless-maven-plugin`（Google 格式）
- `maven-pmd-plugin` + `p3c-pmd`（阿里编码规约）
- `spotbugs-maven-plugin` + `findsecbugs-plugin`（安全扫描）
- `maven-checkstyle-plugin`（兜底检查）
- `dependency-check-maven`（CVE 扫描）

### 3. 日志（结构化）— 新增

`oryxos-boot/src/main/resources/logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 开发环境：彩色 console -->
    <springProfile name="dev,default">
        <include resource="org/springframework/boot/logging/logback/base.xml"/>
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- 生产环境：JSON 输出 -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKey>traceId</includeMdcKey>
                <includeContext>false</includeContext>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>
</configuration>
```

### 4. 监控（Actuator + Micrometer + Prometheus）— 新增

在 `application.yaml` 追加：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    tags:
      application: oryxos
```

暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`。

### 5. HTTP Server（Spring MVC + 虚拟线程）— 已有

确认 `application.yaml` 中：
```yaml
server.port: 8080
spring.threads.virtual.enabled: true
```
虚拟线程已开启（JDK 21，单机扛高并发）。

### 6. API 规范（OpenAPI + 统一响应 / 错误码）— 已有 springdoc，补充统一响应

在 `oryxos-web` 建：

**`ApiResponse.java`** — 统一响应体：
```java
package com.oryxos.web.common;

public record ApiResponse<T>(int code, String message, T data, long timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, System.currentTimeMillis());
    }
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }
}
```

**`ErrorCode.java`** — 错误码枚举：
```java
package com.oryxos.web.common;

public enum ErrorCode {
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),
    SANDBOX_VIOLATION(4031, "沙箱安全限制"),
    TOOL_EXECUTION_ERROR(5001, "工具执行失败"),
    LLM_CALL_ERROR(5002, "LLM 调用失败");

    public final int code;
    public final String message;

    ErrorCode(int code, String message) { this.code = code; this.message = message; }
}
```

**`GlobalExceptionHandler.java`** — 全局异常处理：
```java
package com.oryxos.web.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST.code, e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternal(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code, e.getMessage()));
    }
}
```

### 7. 开发规范（Google 格式 + 阿里编码规约，两层互补）— 新增

职责分开，互不冲突：
- **格式层 — Google**：Spotless + google-java-format，管缩进、import 顺序、空白、换行——`apply` 一键自动修。
- **编码规约层 — 阿里巴巴 Java 开发手册**：通过 **P3C（p3c-pmd ruleset）** 落地，管命名、并发、异常处理、集合、OOP、日志、SQL 等"怎么写才对"的规约。
- **兜底 — Checkstyle**（`google_checks.xml`）+ 根目录 `.editorconfig`。

**格式：Spotless + google-java-format**

在父 `pom.xml` 的 `<plugins>` 补充（非 `<pluginManagement>`，直接生效）：

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <configuration>
        <java>
            <googleJavaFormat><style>GOOGLE</style></googleJavaFormat>
            <removeUnusedImports/>
            <importOrder/>
        </java>
    </configuration>
    <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

**编码规约：阿里 P3C（挂在 PMD 上）**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <configuration>
        <rulesets>
            <ruleset>rulesets/java/ali-pmd.xml</ruleset>
            <ruleset>rulesets/java/ali-concurrent.xml</ruleset>
            <ruleset>rulesets/java/ali-exception.xml</ruleset>
        </rulesets>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.alibaba.p3c</groupId>
            <artifactId>p3c-pmd</artifactId>
            <version>${p3c.version}</version>
        </dependency>
    </dependencies>
    <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

**分工原则：** Google 管「长什么样」（格式），阿里管「怎么写才对」（规约），两者职责不同、可并存。若个别风格规则冲突，以 google-java-format 为准（因为它能自动修，省争论）。开发者本地可装「阿里巴巴 Java 编码规约」IDEA 插件，写代码时即时提示。

### 8. 代码安全检查 — 新增

父 `pom.xml` 补四件套：

**SpotBugs + Find Security Bugs**（覆盖 OWASP Top 10：SQL 注入、XSS、路径穿越、弱加密、XXE、不安全反序列化等）：

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>${findsecbugs.version}</version>
            </plugin>
        </plugins>
    </configuration>
</plugin>
```

**PMD**（源码层规则，已有阿里 P3C ruleset 在上面，此处仅配置全局 threshold）：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <!-- 配置同第 7 步，合并到一处 -->
</plugin>
```

**OWASP Dependency-Check**（扫第三方依赖已知 CVE）：

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>${dependency-check.version}</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <formats><format>HTML</format><format>JSON</format></formats>
    </configuration>
    <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

### 9. CI + pre-commit — 新增

**pre-commit（本地）**：在项目根目录创建 `.git/hooks/pre-commit`（或通过 `maven-antrun-plugin` 自动安装）：

```bash
#!/bin/bash
echo "Running pre-commit checks..."
mvn spotless:check -q || { echo "Spotless 格式检查失败，请执行 mvn spotless:apply 修复"; exit 1; }
```

**CI（GitHub Actions）**：创建 `.github/workflows/build.yml`：

```yaml
name: OryxOS Build

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven
      - run: mvn verify -B
```

`mvn verify` 串起：spotless:check → checkstyle → spotbugs → pmd → dependency-check；任一不过则红，禁止合并。

### 10. 验证

- [ ] `mvn clean verify` 全绿
- [ ] `mvn -pl oryxos-boot spring-boot:run` 起得来
- [ ] 访问 `/actuator/health`（UP）、`/actuator/prometheus`（有指标）、`/swagger-ui.html`（能打开）
- [ ] 故意写一行不规范代码 → `spotless:check` 报错；故意引一个有 CVE 的旧依赖 → depcheck 报警

---

## 检查清单（Definition of Done）

- [ ] 9 个 Maven 模块骨架建好，`mvn clean package` 出 fat JAR（已有）
- [ ] 结构化日志（prod 为 JSON，含 traceId），无 `System.out`
- [ ] `/actuator/health` `/info` `/prometheus` 可访问
- [ ] 虚拟线程开启（`spring.threads.virtual.enabled=true`）（已有）
- [ ] springdoc：`/swagger-ui.html` 可打开，统一 `ApiResponse` + `GlobalExceptionHandler` 就位
- [ ] Spotless（Google 格式）+ 阿里 P3C（编码规约）+ Checkstyle + `.editorconfig` 全部生效
- [ ] SpotBugs + Find Security Bugs + PMD + OWASP Dependency-Check 接入 `mvn verify`
- [ ] pre-commit + CI 跑通，任一检查失败即阻断
- [ ] 敏感配置全用 `${ENV_VAR}` 占位，无明文密钥

---

## 与 constitution / Spec-Kit 的分工

- **本 skill**：把上面这套工程地基"装上"（一次性、可复用、跨模块）
- **constitution**：把硬约束"钉死"（JDK 21、Google 规范、必须过安全扫描、Spring AI 只用一半…），让 AI 每次都遵守
- **CI + pre-commit**：把检查"强制执行"（机器把关，不靠人自觉）
- **Spec-Kit user story**：地基起好后，再按五大核心能力逐个开发

> 版本号、插件坐标、`google_checks.xml` 路径等以实施时官方文档为准；本 skill 给的是流程与配置骨架，不锁死具体版本。