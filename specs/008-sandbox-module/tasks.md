---

description: "Task list for Sandbox 沙箱模块 (第24节)"
---

# Tasks: Sandbox 沙箱模块

**Input**: Design documents from `/specs/008-sandbox-module/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 按课件"验收 harness"要求测试任务伴随实现任务落地（harness 先行：断言先定，先红后绿）。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

无 —— 不新建项目、不新增第三方依赖。实现依赖（spring-boot-starter 内 @ConfigurationProperties、mockwebserver 4.12.0 test）与既有测试装配（ToolTestFixture）均已核实存在（见 research.md D1）。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 把沙箱骨架拉回课件规范形态——顶层值对象/枚举、三 props 配置记录、绑定注册 glue、配置键迁移。本阶段完成前模块编译可通过但行为未对齐规范。

**⚠️ CRITICAL**: US1/US2/US3 的测试与接线都依赖本阶段的类型形态，必须先完成。

- [X] T001 [P] 提顶层类型：新建 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/SandboxAction.java`（record，type + target 两字段）与 `ActionType.java`（enum 四值 FILE_READ/FILE_WRITE/SHELL_COMMAND/HTTP_REQUEST），并在 `Sandbox.java` 中删除嵌套定义、接口签名改为引用顶层类型（课件 §3.1 逐字保真；公共形态变更已获用户确认）
- [X] T002 [P] 新建三个配置记录：`FileSandboxProperties.java`（`@ConfigurationProperties(prefix = "file")`，字段 `List<String> allowedPaths`）、`ShellSandboxProperties.java`（prefix `shell`，`allowedCommands`）、`HttpSandboxProperties.java`（prefix `http`，`allowedDomains`），均放 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/`
- [X] T003 绑定注册 glue：`oryxos-tool/src/main/java/com/oryxos/tool/config/ToolConfiguration.java` 类上加 `@EnableConfigurationProperties({FileSandboxProperties.class, ShellSandboxProperties.class, HttpSandboxProperties.class})`（三个 record 无 @Component，需显式注册为 Bean 供 WhitelistSandbox 构造器注入；boot 模块零改动，见 research.md D2）
- [X] T004 配置键迁移：`oryxos-boot/src/main/resources/application.yaml` 删除 `oryxos.sandbox.*` 三个旧键，新增顶层 `file.allowed_paths: [.oryxos]`、`shell.allowed_commands: [ls, cat, echo, date, python, git]`、`http.allowed_domains: ["open.feishu.cn"]`（值语义不变，仅键位置与形态变化，见 contracts/config-contract.md）；在键旁注释写明"白名单为空 = 什么都不允许，而非不校验"

**Checkpoint**: 类型形态与配置契约就位，可开始实现与测试。

---

## Phase 3: User Story 1 + 2 - 三类校验成对 + 绕过回归 (Priority: P1) 🎯 MVP

**Goal**: 白名单实现（文件路径 / shell 命令 / HTTP 域名三类"允许+拒绝"成对）＋ 两个绕过回归（`../` 路径穿越、通配符域名形似绕过）。US1 与 US2 共享同一个测试类与实现，合并为一个阶段。

**Independent Test**: `WhitelistSandboxTest` 可独立运行（`mvn test -pl oryxos-tool -Dtest=WhitelistSandboxTest`），三类校验成对断言 + 两个绕过回归断言即本阶段验收。

### Tests for User Story 1+2 (harness 先行——断言按课件 §四 逐条落地，先红后绿) ⚠️

- [X] T005 [US1] [US2] 新建 `oryxos-tool/src/test/java/com/oryxos/tool/sandbox/WhitelistSandboxTest.java`（harness 先行：断言逻辑按课件"验收 harness"映射表逐条保真；测试方法名用英文驼峰，课件中文名进 `@DisplayName`；先红——此时 WhitelistSandbox 仍是旧实现）：
  - 文件路径组：白名单内放行 / 白名单外拒绝 / 相对路径穿越被拦（`/workspace/../../outside/secret.txt`，normalize 回归，@DisplayName"相对路径穿越必须被拦"）
  - Shell 命令组：白名单内放行 / 白名单外拒绝 / 首 token 前导空格变体
  - HTTP 域名组：精确匹配放行 / 白名单外拒绝 / 通配符 `*.example.com` 命中 api.example.com 但不命中 evil-example.com（@DisplayName"通配符域名_不能被形似域名绕过"）
  - 空白名单 = 什么都不允许（配置空列表时任何动作都抛 SandboxViolationException）
  - 断言用 assertThrows/assertDoesNotThrow 直接调 `sandbox.enforce(new SandboxAction(...))`（不经 Tool 管道，测的是校验本身）

### Implementation for User Story 1+2

- [X] T006 [US1] [US2] 重构 `oryxos-tool/src/main/java/com/oryxos/tool/sandbox/WhitelistSandbox.java`（课件 §3.2 逐字保真 + 两处规范修正）：构造器改为 `(FileSandboxProperties, ShellSandboxProperties, HttpSandboxProperties)` 三 props 注入；allowedRoots 预解析为 `Path.of(...).normalize().toAbsolutePath()` 列表（相对配置按 cwd 转绝对，用户澄清确认）、allowedCommands 为 Set、allowedDomains 为 List；`checkFilePath` 目标 `Path.of(raw).normalize().toAbsolutePath()` 后 startsWith 任一允许根；`checkShellCommand` trim 后取首 token；`checkHttpUrl` 用 `URI.create(url).getHost()` 解析 host，`matchesDomain` 通配符用 `pattern.substring(1)` 保留点号边界（修复既有 `substring(2)` 的形似域名绕过漏洞，见 research.md D3）；错误信息用可读中文文案（如"命令不在白名单内: xxx"）——使 T005 变绿；**同步更新**两个测试装配点改用 props 构造：`oryxos-tool/src/test/java/com/oryxos/tool/ToolTestFixture.java`（第 41-45 行 WhitelistSandbox 注册）与 `oryxos-tool/src/test/java/com/oryxos/tool/builtin/HttpToolsTest.java`（executeAgainstMock 第 85 行）
- [X] T007 [US1] [US2] 同步更新既有引用 `Sandbox.SandboxAction(...)` 与 `Sandbox.ActionType.X` 的调用点为顶层类型引用：`FileTools.java`、`ShellTools.java`、`HttpTools.java`、`NotifyTools.java`、`WebhookNotifyAdapter.java`（`com.oryxos.tool.sandbox.SandboxAction` / `ActionType` import；enforce 首行接线本身已在，不改动任何 IO 代码）

**Checkpoint**: WhitelistSandboxTest 全绿（含两个绕过回归），模块编译通过。

---

## Phase 4: User Story 3 - 四 Tool 接线回归 + 失败审计路径确认 (Priority: P2)

**Goal**: 四个内置 Tool 的"白名单外输入被拦 + 危险动作真正没跑"接线回归用例；校验失败复用既有 ToolExecutor 失败审计路径（不改 ToolExecutor，代码审查确认即可，无新增代码任务）。

**Independent Test**: `mvn test -pl oryxos-tool -Dtest='FileToolsTest,ShellToolsTest,HttpToolsTest,NotifyToolsTest'` 全绿——每条用例含白名单外被拦断言 + 危险动作未发生断言。

### Tests for User Story 3 (接线回归——与实现任务一起落地) ⚠️

- [X] T008 [US3] `FileToolsTest.java` 增加/强化接线回归：白名单外写文件（`/tmp/evil-outside.txt`）被拦后断言 `Files.notExists` 该文件（既有 writeFileBlocked 已有该断言则保留，确认其断言方式与"IO 没有发生"语义一致，方法名保持英文）
- [X] T009 [US3] `ShellToolsTest.java` 增加接线回归：先在 tempDir 放一个目标文件，执行白名单外命令（如 `rm`），断言返回失败且目标文件仍在（进程未启动）
- [X] T010 [US3] `HttpToolsTest.java` 增加接线回归：启动 MockWebServer 拿到 localhost URL，用**不放行 localhost 的** WhitelistSandbox 执行 http_get，断言返回失败且 `server.getRequestCount() == 0`（请求从未发出）
- [X] T011 [US3] `NotifyToolsTest.java` 增加接线回归：用真实拒绝的 WhitelistSandbox（空域名白名单）+ mock WebhookNotifyAdapter，Profile 配一个 webhook URL 渠道，执行 notify，断言返回失败且 `verify(adapter, never())`（危险推送未发生）

### Implementation for User Story 3

接线代码已存在（前序节已交付，本次仅类型引用更新，见 T007）；本阶段无新增实现代码任务，接线回归用例即验收。

**Checkpoint**: 四 Tool 接线回归全绿。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 跨节契约回归 + 构建门禁收尾。

- [X] T012 前序节全部测试回归：`mvn test` 全量全绿（改造的四个 Tool 原有测试 + 其它模块测试，跨节契约证据）
- [X] T013 构建门禁全绿：`mvn clean verify` 通过（含 P3C/SpotBugs/FindSecBugs/PMD 静态检查），实现完成的定义
- [X] T014 按 quickstart.md 人工项自查清单过一遍（真实链路集成验证、接口中立性自查、配置边界文档核对——人工项留给用户执行，本任务仅核对清单存在与可执行性）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: 无外部依赖——直接开始；T001/T002 可并行
- **US1+2 (Phase 3)**: 依赖 T001/T002（类型形态）——T005 测试先于 T006 实现（harness 先行）
- **US3 (Phase 4)**: 依赖 T006/T007（实现 + 引用更新）
- **Polish (Phase 5)**: 依赖全部故事完成

### User Story Dependencies

- **US1+2 (P1)**: 无故事间依赖
- **US3 (P2)**: 依赖 US1+2 的实现（WhitelistSandbox 重构后接线回归才有意义）

### Within Each User Story

- 测试先于/伴随实现（harness 先行：T005 先红 → T006 变绿）
- 实现任务内：类型 → 实现 → 装配点同步（T006 内含 ToolTestFixture/HttpToolsTest 装配更新，避免编译中断）

### Parallel Opportunities

- T001 与 T002 可并行（不同文件）
- T008/T009/T010/T011 可并行（四个不同测试类）
- 依赖关系：T005 ↔ T006 必须同组完成（先红后绿）；T007 与 T005/T006 有文件交集（引用更新依赖新类型存在），在 T006 后执行

---

## Parallel Example: 接线回归四连

```bash
# T008-T011 四个测试类互不依赖，可并行落地：
# 同时改 FileToolsTest / ShellToolsTest / HttpToolsTest / NotifyToolsTest
```

---

## Implementation Strategy

### MVP First（US1+2）

1. Phase 2: T001+T002（类型）→ T003（glue）→ T004（yaml）
2. Phase 3: T005（测试先红）→ T006（实现变绿 + 装配点同步）→ T007（引用更新）
3. **STOP and VALIDATE**: `mvn test -pl oryxos-tool -Dtest=WhitelistSandboxTest`

### Incremental Delivery

1. Foundation ready（类型形态 + 配置契约）
2. US1+2 完成 → WhitelistSandboxTest 全绿（MVP）
3. US3 完成 → 四 Tool 接线回归全绿
4. Polish → `mvn clean verify` 全绿收尾

---

## Notes

- 反作弊纪律：不得删断言、@Disabled、放宽阈值让测试变绿；实现错修实现，测试错停下报告
- 测试方法名必须是英文（驼峰或 snake_case），课件中文用例名用 `@DisplayName` 保留原文
- 全程不自动 commit / push / package.sh，同步时机由用户决定
- 前序节交付文件被本节触碰的：FileTools/ShellTools/HttpTools/NotifyTools/WebhookNotifyAdapter/ToolTestFixture/HttpToolsTest/ToolConfiguration/application.yaml——改动仅限类型引用、构造器形态、配置键，不改任何既有行为断言
