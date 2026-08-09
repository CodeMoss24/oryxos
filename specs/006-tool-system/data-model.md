# Data Model: Tool 体系

本节**无新增 SQLite 表**(五张表维持不变,`tool_invocations` 审计链路由 17 节 ToolExecutor 既有实现承载)。数据模型是运行时内存注册表 + 配置文件格式。

## 1. 工具注册表(ToolRegistry,内存)

- 键:工具名(String,LLM 点名用);值:OryxTool 实例
- 生命周期:Spring 上下文刷新 → ToolAutoRegistrar 扫描 → 注册;运行期只读(无增删)
- 过滤:`subset(Collection<String> names)` → 按 Profile.tools 声明的名字取子集,**精确匹配**(多过滤/少过滤都是错)

## 2. 工具契约三件套(每个注册工具必填)

| 属性 | 来源 |
|---|---|
| name | FunctionCallback.getName() / OryxTool.getName() |
| description | 装配处 description() |
| inputSchema(JSON Schema 字符串) | MethodInvokingFunctionCallback 从方法签名自动生成 / FunctionCallback.inputTypeSchema() / MCP tools/list 返回的 JsonSchema 序列化 |

## 3. mcp_servers.yaml 配置格式(.oryxos/mcp_servers.yaml)

```yaml
servers:
  - name: <server 名, 日志/去重用>
    transport: stdio | sse
    command: <stdio: 可执行文件路径, 如 python3>
    args: [<stdio: 启动参数, 如 "-m", "mcp_server">]   # 可选
    env: { <stdio: 环境变量, 如 "KEY": "value"> }      # 可选
    url: <sse: MCP server HTTP 端点, 如 https://.../sse>  # transport=sse 时必填
```

- 解析:SnakeYAML(经 oryxos-core 传递依赖);缺 name / transport 非法 / stdio 缺 command / sse 缺 url 的项 → WARN 跳过,不阻断其余
- 顶层可以是 `servers:` 列表,也可以是直接列表(兼容两种写法,解析时归一化)

## 4. 对外抽象(接口先行,本节立)

| 抽象 | 方法 | 实现(核心阶段) |
|---|---|---|
| UserInteraction | `String ask(String question)` 拿不到回答抛异常 | ConsoleUserInteraction(终端读一行)/ UnsupportedUserInteraction(明确报错),按 `oryxos.cli.interactive` 条件装配 |
| SearchProvider | `record SearchResult(title, url, snippet)`;`List<SearchResult> search(String query)` | DuckDuckGoSearchProvider(免 key Instant Answer API,base URL 可注入) |

## 5. 状态迁移(无)

工具执行无状态;失败经 ToolResult(成功/内容/错误/可重试)表达,由 ReAct 循环决定是否重试。
