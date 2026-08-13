# Implementation Plan: Web Service 与第一版管理平台

**Branch**: `010-lesson26-web-service` | **Date**: 2026-08-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-web-service-admin/spec.md`

## Summary

把前 25 节交付的底座能力(LLM、ReAct、Memory、Tool、Sandbox、定时)包装成 REST API 对外门面:六个 Controller 提供 11 个端点(10 个核心端点 + 1 个会话列表只读扩展),统一 `ApiResponse` 信封与 `GlobalExceptionHandler` 单异常出口,`serve` 常驻只凭一个 provider key 可启动;顺带交付第一版只读管理平台(Vue 3 + Vite,托管在 `/admin`,五页调五个 GET 端点),并把管理台设计 token 沉淀为项目内 skill `oryxos-admin-ui`。

技术路线:HTTP 层 Spring MVC + virtual thread(同步阻塞代码,无响应式);Controller 只做校验/包装/错误处理,发消息与 invoke 走与 CLI 同一个 `AgentService.process`;切片测试 + 真实上下文冒烟 IT 承载验收。

## Technical Context

**Language/Version**: JDK 21、Spring Boot 3.3.5(锁定 BOM,已核实 `mvn dependency:tree`)

**Primary Dependencies**: spring-boot-starter-web(已有)、springdoc-openapi-starter-webmvc-ui 2.6.0(已有,已核实)、spring-ai 1.0.0-M4(已有;`OpenAiAutoConfiguration` 排除项已在 application.yaml,全限定名已核实;spring-ai-alibaba 不在依赖树,DashScope 排除无需新增)

**Storage**: SQLite(复用前序 `sessions` 表,本节无新表)

**Testing**: JUnit 5 + Mockito + `@WebMvcTest` 切片(SessionApiControllerTest、GlobalExceptionHandlerTest)+ `@SpringBootTest` 冒烟(WebSmokeIT,`@Tag("integration")`,放 oryxos-boot 与 ProviderSmokeIT 同置)

**Target Platform**: Linux 服务器(单二进制部署)

**Project Type**: Web Service + 静态管理平台

**Performance Goals**: 200 并发 invoke 稳定不退化(virtual thread 承载);`/info` Provider 探活带 2s 级超时,不拖垮端点

**Constraints**: 单条消息 ≤32KB;历史返回 ≤100 条;invoke ≤60s;无认证/SSE/WebSocket/RBAC/限流;H4 六条不变量(涉外 IO 首行 Sandbox.enforce 不适用于本模块新端点;无 Reactor/CompletableFuture/自建线程池;session_id 只在 SessionManager 内拼接;无 Spring AI 自动工具执行)

**Scale/Scope**: 11 个 REST 端点 + 1 个 SPA(5 页)+ 1 个项目内 skill;无新数据表

## Constitution Check

*GATE: 通过。逐条核对:*

| 宪法条款 | 本节落实 | 结论 |
|---|---|---|
| I 单体多模块 | 不新增模块,前端源码在 oryxos-web 内 | ✅ |
| II 核心能力边界 | 认证/SSE/WebSocket/RBAC/限流/Agent 目录上传等全部不做 | ✅ |
| III 自实现 ReAct | 不触碰循环,只包装 | ✅ |
| IV Spring AI 边界 | 仅排除 eager 装配(已有);不引入自动 tool 执行 | ✅ |
| V/VII 审计 | 审计走既有 AgentService 链路,web 不旁路 | ✅ |
| VI SQLite | 无新表,无 ddl-auto 演进 | ✅ |
| VIII 接口先行 | 探活/全文读取均为既有接口增量方法,无实现细节泄漏 | ✅ |
| 核心阶段 Web API 边界 | 10 端点 + 1 只读扩展(用户已确认,文档措辞修正入收尾清单) | ✅(经确认) |
| 运行环境约束 | 密钥走 `${DEEPSEEK_API_KEY}` 环境变量占位(已有) | ✅ |

**复杂性说明**: 无违反;两个前序公共接口(ProviderPort、MemoryService、SessionManager、LongTermMemoryStore)的**增量扩展**(新增方法,不改既有方法)已由用户在 clarify 阶段逐项确认。

## Project Structure

### Documentation (this feature)

```text
specs/010-web-service-admin/
├── plan.md              # 本文件
├── research.md          # Phase 0 研究结论
├── data-model.md        # Phase 1 数据视图
├── quickstart.md        # Phase 1 验收指南
├── contracts/           # Phase 1 接口契约
│   └── api-contract.md  # 11 个端点契约
└── tasks.md             # Phase 2(/speckit-tasks)
```

### Source Code (repository root)

```text
oryxos-web/
├── src/main/java/com/oryxos/web/
│   ├── controller/          # 六个 Controller(扩展既有五个 + 会话列表/接线改造)
│   │   ├── SessionApiController.java    # 改:SessionManager 接线、32KB 校验、新异常
│   │   ├── AgentApiController.java      # 改:Agent 存在性校验、60s 超时
│   │   ├── ProfileApiController.java    # 接线既有
│   │   ├── MemoryApiController.java     # 改:readAll() 全文
│   │   ├── ToolApiController.java       # 接线既有
│   │   └── SystemApiController.java     # 改:/info 带 Provider 连通状态
│   ├── exception/           # 新:五个领域异常(SessionNotFound/ResourceNotFound/
│   │   └── ...              #     ProviderUnavailable/AgentTimeout/InvalidRequest)
│   ├── config/              # 新:WebMvcConfigurer(CORS 全开 + /admin SPA 回落)
│   ├── dto/                 # 新:MessageRequest/MessageResponse(课件命名)
│   ├── GlobalExceptionHandler.java      # 扩展异常映射
│   └── ErrorCode.java       # 改:INTERNAL_ERROR 话术 → "内部错误"
├── src/main/frontend/       # 新:Vue 3 + Vite 管理台源码(oryxos-admin-ui skill 生成)
│   ├── package.json / package-lock.json / vite.config.js(base '/admin/')
│   └── src/                 # 五页 + 左导航
└── src/main/resources/static/admin/     # 构建产物(npm run build,提交仓库)

oryxos-core/                 # 增量:接口新增方法
├── ProviderPort.java        # + connectivity 探活方法
├── MemoryService.java       # + readAll()
└── SessionManager.java      # + listAll()

oryxos-provider/  ProviderService.java  # 实现探活
oryxos-memory/    LongTermMemoryStore.java + 三后端   # 实现 readAll()
oryxos-storage/   JpaSessionManager.java              # 实现 listAll()

oryxos-boot/
└── src/test/java/com/oryxos/boot/WebSmokeIT.java     # 真实上下文冒烟(同 ProviderSmokeIT 位置)

.claude/skills/oryxos-admin-ui/SKILL.md   # 新:管理台设计 token 固化
```

**Structure Decision**: 前端源码置于 `oryxos-web/src/main/frontend/`(课程规定);构建串联用 frontend-maven-plugin 绑进 `generate-resources`(对齐 #167 拍板,见 research R7),产物落 `src/main/resources/static/admin/` 且不入库,`mvn package` 一条命令出全量 fat JAR;WebSmokeIT 放 oryxos-boot(oryxos-web 只依赖 core,真实上下文需要 storage/tool/memory/provider 完整装配,boot 才真正触发 JPA repository 扫描——课程明确点名的价值)。

## Complexity Tracking

无宪法违反,不需要辩护。
