# Tool 契约:注册清单与行为

## 1. 统一抽象(OryxTool,oryxos-core,已存在不动)

```java
String getName();                 // LLM 点名
String getDescription();          // 给 LLM 看
String getInputSchema();          // JSON Schema 字符串;缺失 → Provider 翻译 Function Calling 卡死
ToolResult execute(String inputJson); // record(success, content, errorMessage, retryable)
```

## 2. 内置 + 扩展工具注册清单(本节后共 14 个)

### 存量 6 个(重构为 FunctionCallback 管道,行为保真)

| 工具名 | 方法 | 参数 schema(自动生成) | 动作类型 |
|---|---|---|---|
| read_file | readFile(path) | {path: string} | FILE_READ |
| write_file | writeFile(path, content) | {path, content} | FILE_WRITE |
| list_dir | listDir(path) | {path} | FILE_READ |
| shell | shell(command) | {command} | SHELL_COMMAND(超时 30s) |
| http_get | httpGet(url) | {url} | HTTP_REQUEST |
| http_post | httpPost(url, body) | {url, body} | HTTP_REQUEST |

### 新增 5 个扩展工具

| 工具名 | 方法 | 参数 schema | 动作类型 | 行为约定 |
|---|---|---|---|---|
| edit_file | editFile(path, oldText, newText) | {path, oldText, newText} | FILE_READ+FILE_WRITE | 旧文本**唯一匹配**才替换;找不到/多次匹配都失败且文件一字不动 |
| grep | grep(path, pattern) | {path, pattern} | FILE_READ | 递归按正则搜内容,返回 `文件:行号:内容`;上限 200 条截断注明;二进制/非 UTF-8 文件跳过 |
| glob | glob(pattern) | {pattern} | FILE_READ | 通配(如 `**/*.yaml`)找路径;上限 200 条截断注明;匹配根=工作目录 |
| ask_user | askUser(question) | {question} | 无(人机交互) | 经 UserInteraction.ask;拿不到回答抛异常(不静默卡住);无人值守渠道明确报错 |
| web_search | webSearch(query) | {query} | HTTP_REQUEST(provider 发请求前 enforce) | 结果渲染为文本给模型;引擎经 SearchProvider 可替换 |

### 其余(注册态,非本节改造)

- notify(NotifyTools,19 节 OryxTool Bean,本节确认注册链路)
- save_memory / recall_memory(22 节 Memory 交付,不在本节)

## 3. 越界语义(与存量一致)

任何工具执行第一步过 `Sandbox.enforce(action)`;校验失败 → `ToolResult.failure(错误信息, retryable=false)`,真实 IO 不发出。错误信息含 `SandboxViolationException` 文案。

## 4. MCP 工具(方式二,经 McpToolAdapter)

- name/description/inputSchema ← tools/list 返回原样(JsonSchema 序列化为 JSON 字符串)
- execute(inputJson) → `callTool(new CallToolRequest(name, argsMap))` 原样转发
- 结果:TextContent 块拼接为 content;`isError()` → failure(retryable=true);异常 → failure("MCP 调用失败", true)

## 5. 方式三(@Tool Plugin Tool 的 M4 等价形态)

业务方以 `FunctionCallback` 形式声明 `@Bean`(如 `FunctionCallback.builder().description(...).function("my_tool", ...)` 或 `.method(...)`),启动自动扫描包装注册——与内置工具同一条管道。
