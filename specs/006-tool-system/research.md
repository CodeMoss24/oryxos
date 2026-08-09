# Research: Tool 体系(MCP 方式二接线 + 内置工具重构)

## 决策 1:Spring AI 1.0.0-M4 的 FunctionCallback 管道(替代 @Tool 注解)

- **Decision**: 内置工具与方式三统一走 `FunctionCallback.builder().method(name, paramTypes...).targetObject(bean).build()` 管道;`AnnotatedToolAdapter` 把 `FunctionCallback` 包装成 `OryxTool`;`ToolAutoRegistrar` 改为扫描容器中全部 `FunctionCallback` Bean(保留既有 `OryxTool` Bean 扫描)。
- **Rationale**: 项目锁定 spring-ai 1.0.0-M4(BOM),经 javap 逐类核实:M4 **无** `org.springframework.ai.tool.annotation.Tool`、无 `ToolCallback`/`ToolDefinition`(`@Tool` 是 M5+ 引入);M4 的工具抽象是 `org.springframework.ai.model.function.FunctionCallback`(getName/getDescription/getInputTypeSchema/call(String)),16 节 `ToolSchemaAdapter` 已在使用同一类型。M4 的 `FunctionCallback$Builder.method()` 返回 `MethodInvokingSpec`(name/targetObject/targetClass/build),内部 `MethodInvokingFunctionCallback.generateJsonSchema(Map<String,Class<?>>)` **从方法签名自动生成 JSON Schema**——这正是课件 `@Tool` 注解"自动生成 schema"的 M4 等价物(用户已确认选此路)。
- **Alternatives considered**:
  - 升级 spring-ai 至 1.0.0-M6(有真 `@Tool`):Maven Central 可达可拉,但 M4→M6 API 断裂(FunctionCallback→ToolCallback、工具消息类型改名),16/17 节 Provider/ReAct 层全要动,违反"前序节公共接口不动"门禁,回归面远超本节 → 拒绝。
  - 自造 `@Tool` 注解:违反"不建文档外抽象层",无第三方能力可吃 → 拒绝。
  - 放弃重构(Option B):用户已否决。
- **关键细节**: 编译产物含 `MethodParameters`(spring-boot-starter-parent 3.3.5 默认 `-parameters`)→ schema 的 property 名是真实参数名(url/path/content/command…),不是 arg0。已实测确认。
- **命名**: 类名沿用课件交付物 `AnnotatedToolAdapter`(它包装的是 FunctionCallback,M4 下没有真正"注解"可包装,但概念等价、名字保真)。

## 决策 2:MCP Java SDK 0.7.0(io.modelcontextprotocol.sdk:mcp)接入方式

- **Decision**: oryxos-tool 新增依赖 `io.modelcontextprotocol.sdk:mcp:0.7.0`(本地仓库已核实存在;spring-ai-core M4 不依赖 mcp,无版本冲突)。`McpClientService` 解析 `.oryxos/mcp_servers.yaml` → 逐 server `McpClient.sync(transport)` → `initialize()` → `listTools()` → 每个 `McpSchema.Tool` 包成 `McpToolAdapter` 注册;任一环节失败 catch 记 WARN 跳过该 server,不中断其余。
- **Rationale**: javap 全量核实 0.7.0 API:`McpClient` 是静态工厂接口(`sync(ClientMcpTransport)` → `SyncSpec` → `build()` → `McpSyncClient` 类,`listTools`/`callTool` **非 final**,Mockito 可 mock);传输层 `io.modelcontextprotocol.client.transport.StdioClientTransport(ServerParameters.builder(command).args(...).env(...))` 与 `HttpClientSseClientTransport(String url)`;`McpSyncClient` 构造器仅存 delegate、`build()` 不自动握手 → **必须先显式调 `initialize()`** 再 `listTools()`,失败走同一 catch → WARN 跳过。
- **关键细节**:
  - `listTools()` → `ListToolsResult.tools()` → `List<McpSchema.Tool>`(record:name/description/inputSchema)。
  - `inputSchema()` 返回 `McpSchema.JsonSchema`——**包私有 record**(type/properties/required/additionalProperties 四个 public accessor),无法直接引用类型名,用 `var` 调用 accessor 组装 `LinkedHashMap` 再 Jackson 序列化成字符串给 `OryxTool.getInputSchema()`。
  - `callTool(new CallToolRequest(name, Map<String,Object> args))` → `CallToolResult(content, isError)`;文本块 `TextContent.text()` 拼接为结果内容;`isError()==true` → `ToolResult.failure(..., retryable=true)`;调用抛异常 → `failure("MCP 调用失败", true)`(对齐课件可重试语义)。
  - 输入 JSON → `Map<String,Object>`:Jackson `ObjectMapper.readValue(inputJson, Map.class)`。
- **Alternatives considered**: 用 spring-ai-mcp 1.0.0-M6 的 McpToolUtils(M6 才有,且与 M4 不兼容)→ 拒绝。

## 决策 3:web_search 与 DuckDuckGo Instant Answer

- **Decision**: `SearchProvider` 接口(record `SearchResult(String title, String url, String snippet)` + `List<SearchResult> search(String query)`);`DuckDuckGoSearchProvider` 打 `GET https://api.duckduckgo.com/?q=<query>&format=json&no_html=1`,解析 `RelatedTopics[]`(每项 `Text`/`FirstURL`;嵌套 `Topics[]` 展平)与 `AbstractText`/`AbstractURL`,渲染为模型可读文本。base URL 与 `HttpClient` 可注入(测试用 MockWebServer 假端点,不碰真网)。
- **Rationale**: 免 key、业界常用;接口先行与 19 节 `NotifyChannelAdapter`、本节 `Sandbox` 同套路;`web_search` 工具第一件事过 `Sandbox.enforce(HTTP_REQUEST, query 无关——是最终请求的 URL 域名)`——注意:工具只把 query 给 provider,域名校验发生在 **provider 发请求前**(provider 内部先 enforce 再请求,与 http_get 同一 WhitelistSandbox 的 `http.allowed-domains`)。
- **关键细节**: 默认 `http.allowed-domains` 为空 → 真实使用需在 application.yaml 配 `oryxos.sandbox.http.allowed-domains: api.duckduckgo.com`(不改 WhitelistSandbox 默认值,避免动既有装配;quickstart 里写明)。
- **Alternatives considered**: 手写 DuckDuckGo HTML 抓取(脆)、接入需要 key 的引擎(超边界)→ 均拒绝。

## 决策 4:grep 二进制检测与编辑语义

- **Decision**: grep 用**严格 UTF-8 解码**(`CharsetDecoder` REPORT 模式),解码失败的文件跳过不中断(不依赖 `Files.probeContentType`,其依赖系统 magic 文件不可靠);结果上限 200 条,截断时注明;格式 `文件:行号:内容`。edit_file 唯一匹配语义:count<1 报错、count>1 报错、=1 替换;报错时文件不落盘(先全读进内存,匹配唯一才写回)。
- **Rationale**: 课件 6.2 明示;严格解码零外部依赖、行为确定。
- **Alternatives considered**: probeContentType → 平台相关,拒绝。

## 决策 5:ask_user 的 UserInteraction 装配策略

- **Decision**: `UserInteraction` 接口 + `ConsoleUserInteraction`(BufferedReader 读一行)/`UnsupportedUserInteraction`(抛异常,带"不支持交互"信息)。装配:`ToolConfiguration` 里按 `@ConditionalOnProperty(name="oryxos.cli.interactive", havingValue="true")` 注册 Console、否则 Unsupported——CLI `chat` 启动装配处设该属性(交互进程),`serve`/`gateway` 默认 Unsupported(无人值守)。两个实现都不加 `@Component`(避免 bean 歧义)。
- **Rationale**: 课件"装配时注入的实现决定"是唯一指定;条件装配满足"CLI 可问、定时/Web 明确报错";核心阶段无多渠道并发交互需求(ReAct 同步执行,同一 stdin 一问一答,课件已论证)。
- **Alternatives considered**: 全局唯一 bean 按渠道选择(核心阶段无渠道注册表,过度设计)→ 拒绝。

## 决策 6:McpClientService 可测性

- **Decision**: `McpClientService` 把"建立单个连接"抽为 `protected McpSyncClient connect(McpServerConfig cfg)`(包可见可重写),测试子类 override 返回 mock / 抛异常,分别覆盖"正常注册"与"失联 WARN 隔离"。
- **Rationale**: 不新增公共概念(protected 钩子是标准测试缝),课件 harness 的"mock McpClient"由此落地。
- **Alternatives considered**: 注入函数式工厂接口(新公共类型)→ 拒绝。

## 决策 7:存量工具行为契约(重构红线)

- **Decision**: 重构后工具名/schema/执行行为逐字保真,唯一有意调整:schemas 由手写字符串变为方法签名自动生成(语义等价,property 名一致);"越界"语义维持存量——`execute` 捕获 `SandboxViolationException` 返回 `ToolResult.failure(..., false)`(课件模板的 `assertThrows` 与存量不符,以存量为准,测试断言 failure 结果;课件原文进 `@DisplayName`)。
- **Rationale**: "前序节交付的公共接口不动";17 节 ToolExecutor 已按 `ToolResult` 消费失败。
