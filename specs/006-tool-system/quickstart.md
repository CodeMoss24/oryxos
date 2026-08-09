# Quickstart: Tool 体系验收引导

## 前置

- 分支 `020-lesson20-tool`;构建门禁 `mvn clean verify`(含 P3C/SpotBugs/FindSecBugs/PMD)。
- 无外部服务依赖:全部测试单测级(MCP 全 mock、web_search 用 MockWebServer、白名单用真实 WhitelistSandbox + 受控配置)。

## 自动化验收(harness,必跑)

```bash
mvn -pl oryxos-tool -am test          # 本节测试(契约/注册/内置工具/扩展/MCP)
mvn clean verify                      # 全量门禁(最终)
```

| 测试类 | 验收点 |
|---|---|
| OryxToolContractTest | 遍历真实装配的 ToolRegistry,每个工具 name/description/inputSchema 非空 |
| ToolRegistryTest | 三类来源注册为 OryxTool;subset 精确匹配(多/少都错) |
| FileToolsTest / ShellToolsTest / HttpToolsTest | 正常能跑通 + 越界会被拦(真实白名单) |
| McpToolAdapterTest | execute 转发参数原样、结果包装、失败可重试 |
| McpClientServiceTest | listTools 被包装注册;失联只 WARN、其余照常注册、启动不炸 |
| (回归)ToolExecutorTest / NotifyToolsTest / WebhookNotifyAdapterTest 等前序测试 | 跨节契约不破 |

预期:`mvn clean verify` 全绿。

## 功能冒烟(CLI,轻命令不依赖 Spring 上下文)

```bash
oryxos tool list
# 预期:14 个工具可见(6 存量 + 5 扩展 + notify;save_memory/recall_memory 待 22 节)
```

## 人工项(harness 判不了的,等真环境)

1. **方式一真跑一次**:`.oryxos/skills/` 写 SKILL.md + 配置真实 MCP server(如 filesystem-mcp),Agent 读意图、调 MCP 工具完成任务——依赖真模型 + 真 server。
2. **方式三真跑一次**:写一个 FunctionCallback @Bean 示例工具,`tool list` 可见、Agent 能调通。
3. **web_search 真跑**:application.yaml 配 `oryxos.sandbox.http.allowed-domains: api.duckduckgo.com`,Agent 搜一次(依赖真网络)。
4. **ask_user 交互**:`oryxos chat` 会话里让 Agent 提问(装配 `oryxos.cli.interactive=true` 的进程),终端可回答;`oryxos serve` 进程里调用则返回"不支持交互"。
5. **审计目检**:真链路跑一次工具调用,`tool_invocations` 表出现 success=true/false 两条记录(17 节既有链路)。
6. **MCP 失联隔离**:mcp_servers.yaml 配一个坏 server(命令不存在),启动日志出现 WARN、OryxOS 照常起、其余工具可用。

## 设计对照(实现时逐条核对)

- 工具方法第一行过 `Sandbox.enforce`(24 节 Sandbox 本体已在,接线即生效)。
- 每次调用成败落 `tool_invocations`(17 节 ToolExecutor 既有路径,零改动)。
- Profile `tools` 字段过滤一行没改(ReActLoop/PromptBuilder 既有接线)。
- 无 Spring AI 自动 tool 执行路径(ReActLoop+ToolExecutor 全权调度;FunctionCallback 只做 schema/调用载体,Provider 侧翻译仍走 16 节 ToolSchemaAdapter)。
