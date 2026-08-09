---

description: "Task list for CLI 命令行入口与 Session 会话层"
---

# Tasks: CLI 命令行入口与 Session 会话层

**Input**: Design documents from `/specs/004-cli-session-layer/`

**Prerequisites**: plan.md、spec.md、research.md（G1~G8 差距分析）、data-model.md、contracts/

**Tests**: 课件"验收 harness"点名 `SessionManagerTest`、`SessionRepositoryTest`，测试任务先行或伴随实现（harness 先行）。

**Organization**: 按用户故事分组，会话层为所有故事的共同地基，放 Foundational。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 所属用户故事（US1/US2/US3）
- 描述含确切文件路径

---

## Phase 1: Setup（共享基础设施）

**目的**: 全故事共用的建表脚本

- [x] T001 在 `oryxos-storage/src/main/resources/schema.sql` 追加 sessions 建表 DDL（字段照 data-model.md：session_id TEXT PRIMARY KEY、profile_name、channel、user_id、messages_json TEXT、status 默认 'active'、created_at、last_active_at、archived_at），与既有 llm_calls/tool_invocations 同风格（CREATE TABLE IF NOT EXISTS）

---

## Phase 2: Foundational（阻塞性前置——会话层地基，US1 依赖）

**目的**: SessionManager 接口 + 实现 + 编解码 + harness 两个测试类，全入口共用的会话地基

- [x] T002 [P] 先写 harness：新建 `oryxos-storage/src/test/java/com/oryxos/storage/SessionRepositoryTest.java`（照 `LlmCallRepositoryTest` 模式：@DataJpaTest + @ContextConfiguration(StorageTestApplication.class) + @AutoConfigureTestDatabase(NONE) + schema.sql），覆盖：① 手工建表脚本建出的 sessions 表能存能读（save → findById 断言全字段）② messages_json 含引号/换行/反斜杠的长消息回读完整 ③ 模拟"重启"（同库新 context 重新 @DataJpaTest 注入 repository 重查）历史还在
- [x] T003 [P] 先写 harness：新建 `oryxos-storage/src/test/java/com/oryxos/storage/SessionManagerTest.java`（同 @DataJpaTest 模式，注入 JpaSessionManager），覆盖：① **课件关键回归测试原样落地**——`sameTripleAlwaysReturnsSameSession`（@DisplayName 保留课件中文名"同一三元组_历次getOrCreate都是同一个Session"）：`getOrCreate("cli","wang","default")` 两次 → `assertEquals(first.getSessionId(), second.getSessionId())`；`getOrCreate("web","wang","default")` → `assertNotEquals(first.getSessionId(), other.getSessionId())` ② 三元组 user 不同 → 不同会话 ③ 三元组 profile 不同 → 不同会话 ④ getOrCreate 带回已有历史（先 save 后 getOrCreate，messages 仍在）⑤ get 未命中返回空 Optional
- [x] T004 新建 `oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java` 接口（契约见 contracts/session-manager.md）：`Session getOrCreate(String channel, String user, String profileName)`、`Optional<Session> get(String sessionId)`、`void save(Session session)`；javadoc 注明"id 拼接只在此处实现内部"；不依赖 storage 任何类型
- [x] T005 新建 `oryxos-storage/src/main/java/com/oryxos/storage/session/SessionCodec.java`：Session ↔ SessionEntity 双向转换 + messages_json 序列化/反序列化（JSON 数组 `[{"role":"...","content":"..."}]`，escape `\ " \n \r \t`，反序列化容忍空/坏 JSON 返回空列表）；从 `SessionPersistenceAdapter` 迁移既有序列化逻辑（行为不变）
- [x] T006 新建 `oryxos-storage/src/main/java/com/oryxos/storage/session/JpaSessionManager.java`（@Component，实现 SessionManager）：id 拼接 `channel+":"+user+":"+profileName` 唯一在此处；getOrCreate = 先 findById 命中返回（含历史）否则新建 status=active 并 save；get = findById 经 SessionCodec 转换；save = upsert（findById 存在则更新 messages_json/status/时间戳，否则新增）
- [x] T007 改造 `oryxos-provider/src/main/java/com/oryxos/provider/SessionPersistenceAdapter.java`：内部序列化改用 SessionCodec（注入 SessionCodec，删掉手写 serializeMessages/escapeJson），对外行为与接口签名完全不变；跑 `mvn -pl oryxos-provider test` 回归

---

## Phase 3: US1 终端交互式对话（P1）

**目的**: `oryxos chat` 走会话层，幂等/持久化契约生效；依赖注入修复

**独立测试标准**: SessionManagerTest/SessionRepositoryTest 全绿；人工：chat 多轮对话 + /quit + 重启历史还在（quickstart.md 清单 1/7/9）

- [x] T008 改造 `oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java`：注入 SessionManager，删硬编码 `"cli+console+"+profileName` 与 `new Session(...)`，改 `sessionManager.getOrCreate("cli", currentUser(), profileName)`（currentUser = System.getProperty("user.name") 兜底 "console"）；交互循环（/quit、空行、trim、singleMessage 单条模式）保持既有行为；测试：`mvn -pl oryxos-channel-cli test`
- [x] T009 改造 `oryxos-cli/src/main/java/com/oryxos/cli/ChatCommand.java`：标 @Component（让 Spring 工厂创建、@Autowired 生效）；`--profile` 加 `defaultValue = "default"`（对齐课件骨架）；保留 --message 单条模式；run() 里从注入的 ApplicationContext 取 CliChannel bean 的逻辑改为直接注入 CliChannel（构造注入或字段注入）
- [x] T010 改造 `oryxos-boot/src/main/java/com/oryxos/boot/PicocliConfig.java`：`new CommandLine(command)` 改用 SpringCommandFactory（picocli-spring-boot-starter 的 `picocli.spring.SpringCommandFactory`，实现前先 `mvn -pl oryxos-boot dependency:tree` 核实该类存在，核实不到 → 停下报告）
- [x] T011 改造 `oryxos-boot/src/main/java/com/oryxos/boot/OryxOsApplication.java`：`@SpringBootApplication` 排除 `PicocliAutoConfiguration`（`org.springframework.boot.autoconfigure.AutoConfiguration.imports` 或 `exclude = {org.springframework.boot.autoconfigure.picocli.PicocliAutoConfiguration.class}` 按实际类名），防止其 CommandLineRunner 与自身双执行命令

---

## Phase 4: US2 轻命令秒回（P2）

**目的**: session list 输出真实数据；轻命令不启 Spring 契约保持

**独立测试标准**: 人工：`session list` 列真实会话；`profile list`/`init` 秒回（quickstart.md 清单 3/5）

- [x] T012 [P] 改造 `oryxos-cli/src/main/java/com/oryxos/cli/SessionCommand.java`（session list）：JDBC 直连 `.oryxos/oryxos.db`（`DriverManager.getConnection("jdbc:sqlite:.oryxos/oryxos.db")`），SELECT session_id/profile_name/channel/user_id/last_active_at FROM sessions，逐行打印；库不存在/表不存在时打印友好提示（"no sessions yet"）不抛堆栈
- [x] T013 [P] 改造 `oryxos-cli/pom.xml`：加 `org.xerial:sqlite-jdbc` 依赖（版本沿用父 BOM 锁定，勿新写版本号；实现前核实 BOM 中该依赖存在）

---

## Phase 5: US3 指定 Agent 对话与服务启动（P3）

**目的**: `chat --profile`、`serve`、`gateway` 三模式共享存储契约立住

**独立测试标准**: 人工：`chat --profile weather` 正常对话；`serve` Tomcat 常驻、日志 "Found N JPA repository interfaces" N>0（quickstart.md 清单 2/4/6/8）

- [x] T014 [P] 核对 12 命令完整性：`oryxos-cli` 下 OryxOsCli 注册 9 个命令类（init/status/chat/serve/gateway/profile list|create|show|delete/provider list/tool list/session list），逐个 `--help` 可用；缺的补 @Command（预期不缺，核对即可）
- [x] T015 [P] 核对 `oryxos-boot` 启动配置：`@EnableJpaRepositories(basePackages="com.oryxos.storage.repository")` + `@EntityScan(basePackages="com.oryxos.storage.entity")` 已在，确认未在改造中丢失（T011 改注解时连带核对）

---

## Phase 6: Polish（收尾与跨切面）

**目的**: 全量门禁与跨节契约回归

- [x] T016 跑 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）；红了当场修，不攒
- [x] T017 前序节回归：`mvn -pl oryxos-core,oryxos-provider,oryxos-storage,oryxos-boot test` 全绿（跨节契约：AgentService/SessionPersistencePort 未被破坏）
- [x] T018 H4 六条全局不变量自查（节级收尾时做，写在验收报告里）：①涉外 IO 过 Sandbox（本节无新增涉外 IO，session list 只读本地 SQLite）②llm_calls/tool_invocations 审计路径未被本节破坏 ③grep 无明文 key ④session_id 只在 SessionManager 内拼接（grep 确认无其他 `+profile` 拼 id 处）⑤无 Reactor/CompletableFuture/自建线程池 ⑥无 Spring AI 自动工具执行路径

---

## 依赖关系

```
T001（sessions DDL）
  └─> T002（SessionRepositoryTest，harness 先行）
        └─> T005（SessionCodec）─> T006（JpaSessionManager）─> T007（Adapter 复用）
  └─> T003（SessionManagerTest，harness 先行）
        └─> T004（SessionManager 接口）
  ├─> T008（CliChannel）─> T009（ChatCommand）─> T010（PicocliConfig）─> T011（排除自动配置）
  ├─> T013（cli pom sqlite-jdbc）─> T012（SessionCommand）
  └─> T014/T015（核对）→ T016/T017/T018（收尾）
```

用户故事完成顺序：US1（Phase 3）→ US2（Phase 4）→ US3（Phase 5）→ Polish（Phase 6）。会话层（Phase 2）是三者的共同前置。

## 并行执行示例

- T002/T003（两个 harness 测试类，不同文件）可并行，但都依赖 T001。
- T004/T005 可并行（core 接口与 storage 编解码互不依赖）。
- T012/T013 可并行（命令实现与 pom 依赖）。
- T014/T015 可并行（核对类）。

## 实施策略

- MVP = US1：T001→T002/T003→T004/T005/T006→T007→T008/T009/T010/T011，会话地基 + chat 交互闭环先通。
- US2/US3 是轻量补齐与核对，量小、独立。
- 每个任务完成即跑所在模块测试，红了当场修，不攒到最后。
