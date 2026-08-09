# 实施计划：CLI 命令行入口与 Session 会话层

**分支**: `018-lesson18-cli` | **日期**: 2026-08-09 | **规格**: [spec.md](./spec.md)

**输入**: `/specs/004-cli-session-layer/spec.md` 功能规格说明书

## 摘要

交付 OryxOS 的命令行入口与全入口共用的会话持久化地基。CLI 是薄薄的入口层：12 个 Picocli 子命令（骨架已在），重命令（chat/serve/gateway）起 Spring 上下文、轻命令（init/profile 等）直接文件操作秒回（分流已在 boot 主类实现）。本节的实缺口：`SessionManager`（接口 core + 实现 storage，id 拼接唯一出口、getOrCreate 幂等）、sessions 手工建表脚本、`CliChannel` 去掉硬编码 sessionId 改用 SessionManager、ChatCommand 依赖注入修复（SpringCommandFactory + 排除 PicocliAutoConfiguration 防双执行）、session list 真实现（JDBC 直连）、`SessionManagerTest`/`SessionRepositoryTest` 两个 harness 测试类。

## 技术上下文

**语言/版本**: JDK 21（避开 P3C/ASM 解析不了的 Java 18+ 语法形态，如增强 switch 的 `default ->` 写法）

**主要依赖**: Spring Boot 3.x、Picocli（cli 已有）、sqlite-jdbc（BOM 已锁定，storage 在用，本节加入 cli 依赖）、picocli-spring-boot-starter（boot 已有，用于 SpringCommandFactory；需排除其自动配置）

**存储**: SQLite 手工建表脚本（`schema.sql` 补 sessions DDL，`spring.sql.init.mode=always` 由测试加载；生产首次建表 `ddl-auto=update` 兜底）

**测试**: JUnit 5 + AssertJ + `@DataJpaTest`（照 `LlmCallRepositoryTest` 模式：`StorageTestApplication` + `AutoConfigureTestDatabase(NONE)` + `schema.sql`）；`mvn clean verify` 全绿即完成（含 P3C/SpotBugs/FindSecBugs/PMD）

**目标平台**: Linux x86-64（Ubuntu 22.04+ 等），WSL2 开发环境

**项目类型**: Maven 多模块 Spring Boot 单体

**性能目标**: 轻命令不启 Spring 秒回（已有）；会话读写毫秒级

**约束**: 凭证走环境变量占位；SQLite 手工建表脚本；session_id 拼接只在 SessionManager 内部；不修改前序节公共接口

**规模/范围**: 新建 4 个主类（SessionManager 接口、JpaSessionManager、SessionCodec）+ 改造 3 个（CliChannel、ChatCommand、PicocliConfig）+ 1 个启动类排除项 + schema.sql 补 DDL + cli pom 加依赖 + 2 个测试类

## 宪法合规检查

*门禁：Phase 0 研究前通过，Phase 1 设计后重检。*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. JDK 21 + Spring Boot 3.x 单体 | ✅ PASS | 全部改动在现有 9 模块内，不拆新模块 |
| II. 五大核心能力优先 | ✅ PASS | CLI 是入口层，会话层是地基；治理层不涉及 |
| III. 自实现 ReAct Loop | ✅ PASS | 不触碰 ReActLoop，仅增加触发源接入 |
| IV. Spring AI 使用边界 | ✅ PASS | 本节不涉及 Spring AI |
| V. Plugin Tool 三档接入 | ✅ PASS | 不涉及 |
| VI. SQLite + MEMORY.md | ✅ PASS | sessions 表用手工建表脚本，生产首次建表 ddl-auto=update 兜底（技术方案允许） |
| VII. 审计 Day One 落库 | ✅ PASS | 不破坏既有 llm_calls/tool_invocations 写入；会话层不写审计 |
| VIII. 接口先行 | ✅ PASS | SessionManager 接口放 core 不携带实现细节；实现放 storage |
| IX. 可演示 Demo | ✅ PASS | chat 交互式对话 + session list 可查 + 重启历史不丢，撑起 Demo 一对话版 |

**门禁结果**: 全部 PASS，无违规项。

## 项目结构

### 文档（本节 feature）

```text
specs/004-cli-session-layer/
├── plan.md              # 本文件
├── research.md          # Phase 0 差距分析与决策
├── data-model.md        # sessions 表 DDL + SessionCodec 设计
├── quickstart.md        # 自动化门禁 + 人工验收清单
├── contracts/
│   ├── session-manager.md   # SessionManager 三方法契约
│   └── cli-commands.md      # 12 子命令 + chat 交互契约
└── tasks.md             # /speckit-tasks 命令输出
```

### 源代码（仓库根目录）

```text
oryxos-core/src/main/java/com/oryxos/core/session/
└── SessionManager.java      # [新建] 接口：getOrCreate/get/save，id 拼接唯一出口

oryxos-storage/src/main/java/com/oryxos/storage/
├── session/
│   ├── JpaSessionManager.java   # [新建] SessionManager 实现：幂等 getOrCreate、get、save(upsert)
│   └── SessionCodec.java        # [新建] Session↔SessionEntity 转换 + messages_json 序列化/反序列化
├── config/                      # [不改]
└── resources/schema.sql         # [改造] 补 sessions 建表 DDL

oryxos-provider/src/main/java/com/oryxos/provider/
└── SessionPersistenceAdapter.java  # [改造] 序列化逻辑改用 SessionCodec（行为不变，接口不动）

oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/
└── CliChannel.java             # [改造] 去硬编码 sessionId，改 getOrCreate("cli", currentUser(), profile)

oryxos-cli/src/main/java/com/oryxos/cli/
├── ChatCommand.java            # [改造] 标 @Component；--profile 默认 "default"
├── SessionCommand.java         # [改造] JDBC 直连列 sessions 表（真实数据）
└── pom.xml                     # [改造] 加 sqlite-jdbc 依赖

oryxos-boot/src/main/java/com/oryxos/boot/
├── OryxOsApplication.java      # [改造] 排除 PicocliAutoConfiguration（防命令双执行）
└── PicocliConfig.java          # [改造] SpringCommandFactory 创建命令实例

oryxos-storage/src/test/java/com/oryxos/storage/
├── SessionManagerTest.java     # [新建] 幂等/隔离/id 唯一/get 未命中（含课件关键回归测试）
└── SessionRepositoryTest.java  # [新建] 手工建表能存能读/messages_json 完整/模拟重启
```

**结构决策**: 改动落 5 个模块（core/storage/provider/channel-cli/cli/boot），全部是既有骨架的补齐与修正。`SessionManager` 接口在 core、实现+编解码在 storage（依赖倒置，与 `SessionPersistencePort` 同模式）。不新建模块，不修改前序节公共接口签名（`SessionPersistenceAdapter` 仅内部换用公共编解码器）。

## 复杂度追踪

> 无宪法违规项需要说明。

## Phase 0: 研究结论

见 [research.md](./research.md)（含 G1~G8 差距分析、D1~D7 决策：SessionManager 落位、SessionCodec 集中、SpringCommandFactory 注入、--profile 默认值、session list JDBC、Tomcat 现状接受、测试策略）

## Phase 1: 设计产出

见 [data-model.md](./data-model.md)、[quickstart.md](./quickstart.md)、[contracts/](./contracts/)
