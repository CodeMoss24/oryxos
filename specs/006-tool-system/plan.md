# Implementation Plan: Tool 体系

**Branch**: `020-lesson20-tool` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-tool-system/spec.md`

## Summary

第 20 节 Tool 体系:在既有 `OryxTool`/`ToolResult`/`ToolRegistry`(oryxos-core)与 `Sandbox` 前向接口(oryxos-tool)之上,补齐三件事——①内置工具重构为 Spring AI M4 `FunctionCallback` 管道(schema 从方法签名自动生成,不再手写)+ 新增 5 个业界级扩展工具(edit_file/grep/glob/ask_user/web_search);②`AnnotatedToolAdapter` 统一包装管道 + `ToolAutoRegistrar` 扩展扫描;③MCP 方式二真实接线(`io.modelcontextprotocol.sdk:mcp:0.7.0`,McpClientService 解析配置→连接→注册,失联 WARN 隔离;McpToolAdapter 原样转发)。验收由 6 个 harness 测试类承载,`mvn clean verify` 全绿即通过;人工项(真模型/真 MCP/真网络)另行清单。

## Technical Context

**Language/Version**: JDK 21 + Spring Boot 3.3.5(spring-boot-starter-parent,默认开 `-parameters`)

**Primary Dependencies**: Spring AI 1.0.0-M4(锁定,BOM;`org.springframework.ai.model.function.FunctionCallback` 管道,无 `@Tool` 注解——已核实,见 research 决策 1);MCP Java SDK `io.modelcontextprotocol.sdk:mcp:0.7.0`(本地仓库已核实全 API,新增到 oryxos-tool);SnakeYAML(经 oryxos-core 传递);Jackson(spring-boot-starter-web 自带)

**Storage**: 无新表;mcp_servers.yaml 文件配置(SnakeYAML 解析);运行时 ToolRegistry 内存注册表

**Testing**: JUnit 5 + Mockito + OkHttp MockWebServer(已依赖);6 个 harness 测试类,单测默认跑;集成冒烟打 `@Tag("integration")` CI 跳过;完成定义 = `mvn clean verify` 全绿

**Target Platform**: Linux(WSL2)/K8s 单体;无新部署形态

**Project Type**: Java Maven 多模块(第 20 节落位 oryxos-tool 为主,oryxos-core 一个文件级小改)

**Performance Goals**: 无新性能指标;grep/glob 结果上限 200 条防上下文撑爆;shell 超时沿用 30s

**Constraints**: P3C/SpotBugs/FindSecBugs/PMD 静态门禁全绿;不新增 plan 未列依赖;不动前序节公共接口;工具名/动作类型/schema 键名逐字保真

**Scale/Scope**: 14 个工具(6 重构 + 5 新增 + notify 注册确认 + 2 个 22 节延后);MCP server 数核心阶段预期个位数

## Constitution Check

*GATE: 逐条对照宪法与 CLAUDE.md constitution,以下已通过*

| 原则 | 本计划执行情况 |
|---|---|
| I. JDK21+SB3.x 单体 | 无新模块、无新部署形态;新依赖仅 oryxos-tool 内 MCP SDK |
| II. 五大能力优先,治理层延后 | Tool Policy/按需加载/自暴露 MCP server/容器沙箱全部明确不做(spec 边界) |
| III. 自实现 ReAct Loop | ReActLoop 零改动;FunctionCallback 只做 schema 生成与调用载体,不引入 Spring AI 自动执行 |
| IV. Spring AI 只用一半(最易错) | ✅ 只用 FunctionCallback 的 schema 生成 + 协议转换;**无** Spring AI 自动 tool 执行路径(Provider 侧翻译仍走 ToolSchemaAdapter,执行全权 ReActLoop+ToolExecutor) |
| V. Plugin Tool 三档 | 方式二(MCP)本节接线;方式三以 M4 等价形态(FunctionCallback Bean)支持;方式一依赖 29 节 Skill |
| VI. SQLite+MEMORY.md | 无新表;无 ddl-auto 依赖 |
| VII. 审计 day one 落库 | 复用 17 节 ToolExecutor 既有 tool_invocations 路径,零新增逻辑 |
| VIII. 接口先行 | UserInteraction / SearchProvider 先抽象后实现;Sandbox 复用既有接口 |
| IX. 可演示 | 14 工具 tool list 可见 + harness 全绿 + quickstart 人工项清单 |
| 软门禁 | 本计划列明:①`AnnotatedToolAdapter` 名称沿用课件(包装对象因 M4 现实为 FunctionCallback,已在 clarify 与 research 记录);②无已定字面量改动;③无课件与技术方案冲突(已走用户确认);④存量内置工具重构经用户确认(Option A 的 M4 实现);⑤MCP SDK 0.7.0 本地核实通过;⑥唯一新增依赖 mcp 0.7.0 已在计划列明 |

## Project Structure

### Documentation (this feature)

```text
specs/006-tool-system/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出(7 个决策)
├── data-model.md        # Phase 1 输出(注册表/配置格式/抽象)
├── quickstart.md        # Phase 1 输出(自动化+人工验收)
├── contracts/
│   └── tools.md         # 14 工具契约 + MCP/方式三契约
└── tasks.md             # /speckit-tasks 输出(后续)
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/tool/   # 不动(OryxTool/ToolResult/ToolRegistry 已交付)
oryxos-tool/src/main/java/com/oryxos/tool/
├── ToolAutoRegistrar.java              # 改:增加 FunctionCallback Bean 扫描注册
├── adapter/AnnotatedToolAdapter.java   # 新:FunctionCallback → OryxTool 包装
├── config/ToolConfiguration.java       # 新:14 行 FunctionCallback @Bean 装配(内置+扩展工具)
├── builtin/
│   ├── FileTools.java                  # 改:6 方法(3 存量+edit_file/grep/glob)
│   ├── ShellTools.java                 # 改:1 方法
│   ├── HttpTools.java                  # 改:2 方法
│   └── WebSearchTools.java             # 新:webSearch
├── interaction/
│   ├── InteractionTools.java           # 新:askUser
│   ├── UserInteraction.java            # 新:接口
│   ├── ConsoleUserInteraction.java     # 新:终端实现
│   └── UnsupportedUserInteraction.java # 新:无人值守实现
├── search/
│   ├── SearchProvider.java             # 新:接口 + SearchResult record
│   └── DuckDuckGoSearchProvider.java   # 新:免 key 实现(base URL/HttpClient 可注入)
├── mcp/
│   ├── McpServerConfig.java            # 新:配置 record(name/transport/command/args/env/url)
│   ├── McpClientService.java           # 改:解析 yaml→连接→listTools→注册;失联 WARN 隔离;protected connect() 测试缝
│   └── McpToolAdapter.java             # 改:真实 callTool 转发 + 结果包装
└── notify/                             # 不动(19 节交付)

oryxos-tool/src/test/java/com/oryxos/tool/
├── ToolTestFixture.java                # 新:AnnotationConfigApplicationContext 装配(真实 WhitelistSandbox + 临时目录)
├── OryxToolContractTest.java           # 新:harness
├── ToolRegistryTest.java               # 新:harness
├── builtin/FileToolsTest.java          # 新:harness
├── builtin/ShellToolsTest.java         # 新:harness
├── builtin/HttpToolsTest.java          # 新:harness
├── mcp/McpToolAdapterTest.java         # 新:harness(mock McpSyncClient)
├── mcp/McpClientServiceTest.java       # 新:harness(mock + 失联子类)
├── interaction/InteractionToolsTest.java  # 新(扩展工具配套)
├── search/DuckDuckGoSearchProviderTest.java # 新(MockWebServer 假端点)
└── (回归) notify/NotifyToolsTest / WebhookNotifyAdapterTest 等既有

oryxos-tool/pom.xml                     # 改:新增 io.modelcontextprotocol.sdk:mcp:0.7.0
.oryxos/mcp_servers.yaml                # 改:补 servers 示例骨架(注释形态,含 stdio/sse 两例)
```

**Structure Decision**: 全部落位 oryxos-tool(课件模块落位表的"其余(Registry/内置 Tool/MCP)→oryxos-tool";存量现实 `ToolRegistry` 在 oryxos-core 因 ReActLoop/ToolExecutor 依赖,保持不动——已在 clarify 记录);`AnnotatedToolAdapter` 放 `adapter` 子包(避免与 ToolAutoRegistrar 平铺混乱);工具实现类(FileTools 等)与装配(ToolConfiguration)分离,装配一处可见全部工具注册——对齐课件"在装配处多 registerAnnotated 两行"。

## Complexity Tracking

无宪法违规,不需要豁免表。唯一偏离字面的点(`@Tool` 注解 → M4 FunctionCallback 管道)已在 clarify(用户确认)与 research 决策 1 完整记录,不构成违规——本质能力(schema 自动生成、自动扫描、统一适配器包装)全部达成。
