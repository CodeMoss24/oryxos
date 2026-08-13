# Phase 0 研究结论:Web Service 与第一版管理平台

所有 NEEDS CLARIFICATION 已解决(三个用户裁决 + 五个文档裁决 + 三个 plan 内决策)。

## 用户裁决项(已记入 spec Clarifications)

### R1 第 11 个端点:GET /api/v1/sessions(会话列表)
- **Decision**: 新增只读列表端点,管理台"会话列表"页数据源;宪法/TS §7.2/CLAUDE.md 的"10 个端点"措辞修正入本节收尾清单。
- **Rationale**: 课件管理台提示词明确要求该 GET;列表只读不违反"只查询不做创建"精神;用户确认。
- **机制**: `SessionManager` 增量新增 `listAll()`(18 节公共接口增量扩展,机制由该裁决自然蕴含),`JpaSessionManager` 按 last_active_at 倒序查询。

### R2 Provider 连通探活
- **Decision**: 核心层 `ProviderPort` 增量新增探活方法(16 节公共接口增量扩展),`ProviderService` 对每个 provider 的 base-url 做带超时轻量探测;`/info` 实时返回连通/断开。
- **Rationale**: 既有 ports & adapters 模式;真实探测才配得上"连通状态"四字;WebSmokeIT 不依赖真模型(探测失败安全返回"断开")。
- **探活实现**: `RestClient` 用短超时 requestFactory(connect/read 各 2s),请求 base-url 本身,任何 HTTP 响应(含 4xx)视为"连通"(地址可达),连接失败/超时视为"断开";多 provider 顺序探测(不并行——H4 禁 CompletableFuture/自建线程池,顺序探测简单可控,2 providers × ≤2s 最坏 4s 可接受);`ProviderService` 已有 `restClientBuilder`,在其上派生一个带超时设置的子 client,不动 chat 路径的 client。

### R3 Memory 全文读取
- **Decision**: `MemoryService` 与 `LongTermMemoryStore` 增量新增 `readAll()`;三档后端(Markdown/SQLite/Mem0)原样读取、均不截断;`buildContext` 注入视图与其 4000 字符截断策略保留不动。
- **Rationale**: 管理台是运维查看入口,应看完整数据;截断是 system prompt 注入策略(防 context window 超限),不是数据策略。
- **实现要点**: `MarkdownMemoryStore.readAll()` 直接返回文件原文(不走 extractSection/truncateIfNeeded);`SqliteMemoryStore` 返回全部 entries 拼装;`Mem0MemoryStore` 走既有远端读取路径(无本地截断)。

### R4 异常映射与信封(课件草图 vs 既有代码的两处冲突合并裁决)
- **Decision**: 按课件草图执行映射——`SessionNotFoundException`/`ResourceNotFoundException`→404、`IllegalStateException`/`ProviderUnavailableException`→503(既有 IllegalStateException→404 迁改)、`InvalidRequestException`/`IllegalArgumentException`→400(既有保留+扩展)、`AgentTimeoutException`→504、兜底→500;信封保持既有 `ApiResponse(code/message/data/timestamp)` 不动;500 统一话术定为"内部错误",`ErrorCode.INTERNAL_ERROR` 字面量由"服务器内部错误"改为"内部错误"。
- **Rationale**: 用户裁决(信封+话术对齐)。连带影响:既有 SessionApiController 两处 `throw new IllegalStateException("session not found")` 改为 `SessionNotFoundException`(本轮重写接线,自然消除)。
- **harness 测试适配(已在 spec 记录并经用户确认)**: 课件关键回归测试"抛 IllegalStateException 断言 500"与"IllegalStateException→503"矛盾——按已确认的草图映射,该用例改抛未映射的 `RuntimeException` 走 500 兜底,断言逻辑(500 + 统一话术"内部错误" + 响应体不含内部异常细节如 `jdbc:sqlite`)保持不变;断言字段由 `$.errorCode` 适配为既有信封的 `$.code`。

## 文档裁决项(课件自身给出裁决规则,不惊动用户)

### R5 管理台设计 token:custom.css 为准
- **Decision**: 颜色/字体值直接取 `website/.vitepress/theme/custom.css` 实际值(主色 `#FF6B2B`、hover `#FF8C42`、强调 `#E8450A`;边框/分隔 `#1a1a1a`;正文灰阶 `#eeeeee/#999/#555`;字体 Space Grotesk/Inter + JetBrains Mono)。课件提示词里列出的 `#f97316/#ea6a00/#c2550a/#111111/#f5f5f5/#a3a3a3/#666666` 与文件不符。
- **Rationale**: 课件自定规则"`website/.vitepress/theme/custom.css` 就是唯一风格来源""值直接抄首页 token,一个字别自创"——文件优先于提示词里的枚举;该结论写入 `oryxos-admin-ui` skill 供后续复用。

### R6 60 秒超时只作用于 invoke
- **Decision**: 60s 上限只包住 `POST /agents/{name}/invoke`;发消息端点不加超时。
- **Rationale**: 课件与 TS §7.4 均表述为"Agent 调用最长 60 秒",会话消息端点两处文档均未提超时。

## Plan 内决策(课件明示"plan 里二选一"或实现机制选型)

### R7 管理台构建串联:frontend-maven-plugin(对齐 #167 拍板,用户复审指定)
- **Decision**: 用 frontend-maven-plugin 把 `install-node-and-npm → npm install → npm run build` 三段绑进 `generate-resources`,一条 `mvn package` 出全量 fat JAR;产物落 `src/main/resources/static/admin/` 且**不入库**(.gitignore),由插件在打包时重建。
- **事实核证(用户复审后实盘复查)**: ①主线(main/origin/009-lesson25)的 `oryxos-web/pom.xml` **从未有过**该插件(仅初版提交,零 plugins 段)——"pom 已配"的记忆只对 `origin/pr/contributing-md@f809d2d` 成立;②该分支是一条分叉旧线(**不含 L24/L25 实现代码**),其插件配置(1.15.1、三段 execution、`${frontend.skip}`)与前端骨架(package.json/Vue 3.5/Vite 6)是 #167 拍板产物;③该配置引用了 `${frontend.node.version}`/`${frontend.skip}` 两个属性,但其根 pom **未定义**——移植时必须补全。
- **移植口径**: 从 f809d2d 原样移植插件段到 `oryxos-web/pom.xml`,在根 `pom.xml` properties 补 `frontend.node.version`(对齐本机可用版本)与 `frontend.skip`(默认 false);同步移植 .gitignore 三条目(`**/node_modules/`、`frontend/dist/`、`static/admin/`)与前端骨架工程文件;五页管理台按 oryxos-admin-ui skill 在骨架上生成。
- **风险**: `install-node-and-npm` 需从 nodejs.org 下载 node(本机 443 防火墙环境,CLAUDE.md 环境约束)——implement 阶段先实测,失败则评估 `nodeDownloadRoot` 镜像或让插件复用系统 node;npm registry 已实测连通(`npm ping` PONG)。

### R8 SPA 回落实现:`PathResourceResolver` 兜底
- **Decision**: `WebMvcConfigurer.addResourceHandlers` 注册 `/admin/**` → `classpath:/static/admin/`,资源链上加自定义 `PathResourceResolver`:请求文件存在且可读则返回,否则回落 `admin/index.html`。
- **Rationale**: Spring Boot 静态资源经 SimpleUrlHandlerMapping(优先级低于 RequestMapping)——`/api/v1/**` 被 Controller 精确接管,不受回落影响;不用 `forward:` view-controller 方案(那会吞掉所有 `/admin/**` 请求包括静态资源,造成循环转发)。

### R9 invoke 60s 超时机制:Spring TaskExecutor + Future.get(60s)
- **Decision**: `AgentApiController` 注入 Spring 的 `TaskExecutor`(applicationTaskExecutor,`spring.threads.virtual.enabled=true` 下即虚拟线程执行器),`submit(() -> agentService.process(...)).get(60, SECONDS)`;`TimeoutException` → `cancel(true)` + 抛 `AgentTimeoutException`。
- **Rationale**: H4 不变量⑤禁 Reactor/CompletableFuture/**自建**线程池——用 Spring 提供的执行器 + 经典 `Future.get(timeout)` 是合规解;虚拟线程上阻塞等待不让出平台线程,超时取消对 RestClient 等阻塞 IO 可通过中断传导。
- **中断语义**: 超时后取消信号尽力传导(虚拟线程可中断),已被取消的任务若继续写入会话/审计,与"超时即终止"的语义有轻微偏差——核心阶段接受,记入验收报告人工项。

### R10 WebSmokeIT 位置与形态
- **Decision**: 放 oryxos-boot(`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` + `@Tag("integration")`),与 ProviderSmokeIT 同置同风格;断言 `/health`、`/info`、`/profiles`、`/tools` 四端点 200 + 信封字段结构。
- **Rationale**: oryxos-web 只依赖 core,真实上下文需要完整模块装配;放 boot 才真正触发 JPA repository 扫描——课程点名的价值("18 节 Found 0 repositories 的坑如果在 web 模块复发,这里第一时间红")。`/info` 的 provider 探活在本机防火墙下失败会返回"断开",断言只查结构与 200,不断言连通值。

### R11 其余契约细节
- **创建会话请求体**: 沿用既有骨架字段 `profile_name`(必填)、`user_id`(默认 "anonymous")、`channel`(默认 "web");实现改走 `sessionManager.getOrCreate(channel, user, profileName)`——session_id 拼接只发生在 SessionManager 内(H4 ④),骨架里 Controller 自拼 id 的代码随本轮重写消除。
- **invoke 会话身份**: `sessionManager.getOrCreate("web", user_id, agentName)`(沿用调度器先例,同 Agent 同 user 复用会话);对调用方面仍是无状态。invoke 前校验 `profileRegistry` 中存在该 Agent,不存在抛 `ResourceNotFoundException`→404。
- **消息 32KB 校验**: `content == null || content.length() > 32 * 1024` → `InvalidRequestException` → 400(课件示意逐字)。
- **历史 100 条**: `GET /sessions/{id}` 返回 `session.getMessages()` 末尾 100 条,字段 role/content(沿用骨架形态)。
- **归档**: `DELETE /sessions/{id}` 置 `archived` 状态并 `sessionManager.save`(骨架只改内存态,本轮落库)。
- **404/503/504 的 message 来源**: 按课件草图,取异常 message(400 同);仅 500 用统一话术"内部错误"。
- **springdoc**: 依赖已在 BOM(2.6.0),`/swagger-ui.html` 已配;不新增配置。
- **CORS**: `WebMvcConfigurer.addCorsMappings("/api/v1/**").allowedOriginPatterns("*")` 全开(TS §7.4),与 SPA 回落同放一个 config 类。
- **application.yaml**: 既有配置已含 port 8080、virtual thread、OpenAiAutoConfiguration 排除、springdoc、datasource——本节无需改动(课件 yaml 草图中的 DashScope 排除项因 spring-ai-alibaba 不在依赖树而不需要,维持现状并注释说明)。
