# Phase 0 研究结论：CLI 命令行入口与 Session 会话层

## 现状盘点（代码差距分析）

第 16/17 节开发时已顺手搭出第 18 节大部分骨架，本节是"补齐 + 钉死契约"，不是从零开发：

| 现状 | 说明 | 本节处置 |
|------|------|---------|
| `OryxOsApplication`（boot） | 轻/重命令分流（LIGHT_COMMANDS）+ `@EnableJpaRepositories(basePackages="com.oryxos.storage.repository")` + `@EntityScan(basePackages="com.oryxos.storage.entity")` + CommandLineRunner 派发 | FR7/FR8 已满足，保留 |
| `OryxOsCli`（cli） | 注册 9 个命令类 = 12 子命令（Profile 内嵌 list/create/show/delete） | 已满足 FR1 |
| `CliChannel`（channel-cli） | 交互循环（/quit、空行、trim）已实现 | **改造**：硬编码 sessionId `"cli+console+<profile>"`、每次新建 Session——违反幂等/持久化契约 |
| `Session`/`Message`（core） | POJO + record，字段齐全 | 保留 |
| `SessionEntity`/`SessionRepository`（storage） | JPA 实体字段齐全（含 archived_at） | 保留 |
| `SessionPersistencePort`（core）+ `SessionPersistenceAdapter`（provider） | 17 节交付：AgentService.process 末尾 save(session)，手写 JSON 序列化 | 保留接口；序列化逻辑抽公共编解码器复用（行为不变） |
| `schema.sql`（storage） | 只有 llm_calls / tool_invocations | **补 sessions 建表 DDL** |
| `SessionCommand`（cli） | stub：打印"未启动 Spring"提示 | **补真实现**：轻命令 JDBC 直连读 sessions 表 |
| `ChatCommand`（cli） | `--profile` 无默认值（课件骨架 `defaultValue="default"`）；`@Autowired ApplicationContext` 在 Picocli 反射创建的命令类上不生效（null → NPE） | **改造**：Picocli 命令类由 Spring 工厂创建 |
| `SessionManager` | **不存在** | **新建**：接口 core + 实现 storage |
| `SessionManagerTest` / `SessionRepositoryTest` | 不存在 | **新建**（harness 主体） |

## 研究结论

### D1. SessionManager 接口落位（决策）

**Decision**: `SessionManager` 接口放 `oryxos-core`（`com.oryxos.core.session`），签名只用 core 类型（`Session`、String），实现 `JpaSessionManager` 放 `oryxos-storage`。id 拼接（channel+user+profile）只发生在 `JpaSessionManager` 内部。

**Rationale**: 与课件模块落位表一致（"`SessionManager` 接口→oryxos-core，`Session` 实体+Repository→oryxos-storage"）；接口依赖方向 core ← storage 与既有 `SessionPersistencePort` 模式一致；所有入口（CLI/Web/定时）只提供三元组，id 拼接唯一出口。

**Alternatives considered**: 接口+实现都放 core（会强制 core 依赖 JPA/SQLite，破坏模块职责）；实现放 provider（与 `SessionPersistenceAdapter` 同模块，但 entity/repository 在 storage，依赖链绕）。

### D2. 会话 JSON 编解码集中（决策）

**Decision**: storage 模块内建 `SessionCodec`（Session↔SessionEntity，含 messages_json 序列化/反序列化）。`JpaSessionManager` 与 `SessionPersistenceAdapter` 共用，消除两处手写 JSON 不一致风险。

**Rationale**: 现有 `SessionPersistenceAdapter.serializeMessages` 手写 JSON（escape 逻辑），`JpaSessionManager.get` 需要反序列化；两套手写实现必出 escape 不一致 bug。抽公共编解码器是消除重复的最小动作，不改任何公共接口签名。

**Alternatives considered**: 不动 adapter、JpaSessionManager 自己写（两套 JSON 逻辑，风险）；引入 Jackson（新增第三方依赖，超出需求）。

### D3. ChatCommand 依赖注入方式（决策）

**Decision**: ChatCommand 标 `@Component`，boot 的 `PicocliConfig` 改为用 `SpringCommandFactory`（picocli-spring-boot-starter 自带）创建子命令；同时**排除 `PicocliAutoConfiguration`**，防止它注册的 `picocliCommandLineRunner` 与 `OryxOsApplication` 自己的 runner 双执行命令。

**Rationale**: `@Autowired` 字段在 Picocli 反射创建的命令类上不注入（null）——现有代码必 NPE；SpringCommandFactory 是标准整合方式。排查发现 `picocli-spring-boot-starter` 的自动配置会注册第二个 CommandLineRunner，`oryxos chat` 会执行两遍——必须在启动类排除，否则人工验收必然翻车。

**Alternatives considered**: ChatCommand 手动从静态 context 取 bean（不优雅、测试难）；不动注入、ChatCommand 里 `new ClassPathXmlApplicationContext` 之类（重复启动上下文，错）。

### D4. chat 无 --profile 默认值（决策）

**Decision**: 对齐课件骨架 `defaultValue = "default"`；`--message` 单条模式保留。`currentUser()` 取 `System.getProperty("user.name")`（本机用户名），兜底 `"console"`。

**Rationale**: 课件 chat 骨架 `@Option(names = "--profile", defaultValue = "default")` 是唯一权威；本机 CLI 无认证，本机用户名是合理用户标识。

### D5. session list 轻命令实现（决策）

**Decision**: `SessionCommand`（session list）保持轻命令（不启 Spring），用 JDBC 直连 `.oryxos/oryxos.db` 读 sessions 表，列出 session_id / profile / channel / user / last_active_at。`oryxos-cli` 增加 `sqlite-jdbc` 依赖（已在 BOM 锁定，storage 在用，非新第三方）。

**Rationale**: 课件"轻命令直接文件操作"精神——SQLite 就是文件，JDBC 直连不启 Spring；12 命令清单（CLAUDE.md 九）要求 session list"列出会话历史"，stub 不算完成。依赖在既有 BOM 内，不违反软门禁 6。

**Alternatives considered**: 保持 stub（验收能过但功能残缺，Demo 时难看）；归重命令启 Spring（违背轻命令设计，启动慢）。

### D6. serve/chat 与 Tomcat（现状确认）

**Decision**: 不动。`spring-boot-starter-web` 在 classpath，重命令起 Spring 时 Tomcat 随启动（chat 会占 8080）。核心阶段接受此现状，不引入 web type 切换。

**Rationale**: 功能正确（chat 能对话、serve 常驻）；切换 `WebApplicationType.NONE` 超出本节交付物范围，且 26 节 Web Service 会重新审视。

### D7. 测试策略（决策）

- `SessionManagerTest`（storage 模块测试）：`@DataJpaTest` + `StorageTestApplication` + `AutoConfigureTestDatabase(NONE)` + `schema.sql`（照 `LlmCallRepositoryTest` 模式），测 `JpaSessionManager`：幂等 / 隔离 / id 拼接唯一性 / get 不存在返回空。
- `SessionRepositoryTest`（storage 模块测试）：sessions 表能存能读；messages_json 长文本回读完整；模拟重启（同库新 context 重查）。
- 关键回归测试（课件原样落地，中文名译英文 + `@DisplayName` 保留原文）：`sameTripleAlwaysReturnsSameSession`——`getOrCreate("cli","wang","default")` 两次同 id；`getOrCreate("web","wang","default")` 不同 id。
- 单测默认全跑（storage 测试已按此模式工作）；无网络依赖，不打 integration 标签。
- `mvn clean verify` 全绿为完成定义（含 P3C/SpotBugs/FindSecBugs/PMD）。

## 待 tasks 落地的依赖核实

- `picocli.spring.SpringCommandFactory` 类路径——picocli-spring-boot-starter 在 boot 模块，implement 时先 `mvn dependency:tree` 核实存在（软门禁 5）。
- `sqlite-jdbc` 在 oryxos-cli 增加依赖后 `DriverManager.getConnection("jdbc:sqlite:.oryxos/oryxos.db")` 可用。
