# Research: Notify 通知模块

**Created**: 2026-08-09

## Decision 1: HTTP 客户端选择

**Decision**: 使用 JDK 内置 `java.net.http.HttpClient`（已在 WebhookNotifyAdapter 中使用）。

**Rationale**: JDK 21 内置，无需额外依赖。连接超时 10s、请求超时 30s，适合 webhook 推送场景。核心阶段不引入额外的 HTTP 客户端库。

**Alternatives considered**:
- Spring RestClient: 课程原稿推荐，但需要 Spring Boot Web 依赖。当前 oryxos-tool 已有 `spring-boot-starter-web` 依赖，RestClient 也可用。但 JDK HttpClient 零额外依赖，且已在代码中落地，保持一致。
- Apache HttpClient / OkHttp: 成熟但增加依赖体积，核心阶段不必要。

## Decision 2: 测试 HTTP Mock 策略

**Decision**: 使用 OkHttp MockWebServer（`com.squareup.okhttp3:mockwebserver3`）模拟 HTTP 服务端。

**Rationale**: MockWebServer 是测试 `java.net.http.HttpClient` 的标准实践——它在本地随机端口起一个真实的 HTTP 服务器，可以编程控制返回状态码和 body，验证发送的请求内容。JUnit 5 + MockWebServer 的组合是业界标准做法。

**Alternatives considered**:
- 直接 mock HttpClient: 不可行——HttpClient 的 send 方法是 final 的，Mockito 无法 mock。
- Spring MockRestServiceServer: 只适用于 RestTemplate/RestClient，不适用于 JDK HttpClient。
- 自定义 HttpServer（com.sun.net.httpserver.HttpServer）: JDK 内置但 API 繁琐，MockWebServer 更简洁。

**⚠️ 软门禁**: MockWebServer 是本项目新增的第三方测试依赖，需要添加到 `oryxos-tool/pom.xml` 的 `<dependencies>` 中（scope=test）。

## Decision 3: ProfileContext.resolveNotifyChannel 策略

**Decision**: NotifyTools 直接遍历 `Profile.getNotifyChannels()` 列表，channel 参数非空时按 `channelType` 精确匹配，channel 为空时使用第一个配置的渠道。

**Rationale**: 课件中的 `profileContext.resolveNotifyChannel(channel)` 简洁优雅，但现有 NotifyTools 代码用内联遍历实现相同逻辑，避免在 ProfileContext（一个简单的 ThreadLocal holder）上增加业务方法。保持 ProfileContext 纯粹（只做 get/set/clear），业务逻辑留在工具内部。

**Alternatives considered**:
- 在 ProfileContext 上加 resolveNotifyChannel 方法: 语义上更内聚，但让 ThreadLocal holder 染上业务逻辑。核心阶段保持 ProfileContext 简洁。
- 在 Profile 类上加 resolveNotifyChannel: Profile 是数据对象，不适合放业务方法。

## Decision 4: NotifyTarget 位置

**Decision**: `NotifyTarget` 作为 `NotifyChannelAdapter` 接口的内嵌 record（当前实现），而非独立文件。

**Rationale**: NotifyTarget 与 NotifyChannelAdapter 语义紧密绑定（send 方法的参数类型），内嵌表达"这是接口契约的一部分"。课程原稿将其作为独立 record 文件，效果等价。

**Alternatives considered**:
- 独立 `NotifyTarget.java` 文件: 课程原稿写法，同等有效。
