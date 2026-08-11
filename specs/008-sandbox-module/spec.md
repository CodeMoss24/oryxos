# Feature Specification: Sandbox 沙箱模块

**Feature Branch**: `024-lesson24-sandbox`

**Created**: 2026-08-11

**Status**: Draft

**Input**: User description: "第24节需求：Sandbox 沙箱模块——把第23节评审定下的"墙"砌成代码"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 沙箱拦截越界的文件/命令/请求，危险动作真正不执行 (Priority: P1)

Agent 在执行文件读写、shell 命令、HTTP 请求前，系统先做白名单校验：白名单内的动作放行、白名单外的动作拒绝。拒绝时错误原因回填给 Agent，Agent 据此换路径；被拒的危险动作（越界读文件、危险命令、越界域名请求）在物理上没有发生。

**Why this priority**: 这是本模块的核心价值——"劝阻级"防线防的是模型犯傻误操作。校验发生在真实 IO 之前且 IO 代码不被触碰，是"墙"成立的最小验证。

**Independent Test**: 用三类受控白名单（仅允许某个目录 / 仅允许 echo,ls / 仅允许某域名）分别执行白名单内与白名单外的动作，断言：白名单内成功、白名单外抛校验失败异常、且危险动作无副作用（被拦的写文件未落盘、被拦的 HTTP 请求无请求到达、被拦的删除命令目标文件仍在）。

**Acceptance Scenarios**:

1. **Given** 路径白名单只含 /workspace，**When** 执行读 /workspace/a.txt，**Then** 放行并返回文件内容
2. **Given** 路径白名单只含 /workspace，**When** 执行读 /etc/passwd，**Then** 抛校验失败异常且未发生任何读取
3. **Given** 命令白名单只含 echo,ls，**When** 执行 `ls /tmp`，**Then** 放行并返回列表
4. **Given** 命令白名单只含 echo,ls，**When** 执行 `rm -rf /tmp/xxx`，**Then** 抛校验失败异常且目标文件仍在
5. **Given** 域名白名单只含 api.example.com，**When** 请求 https://api.example.com/x，**Then** 放行
6. **Given** 域名白名单只含 api.example.com，**When** 请求 https://evil.com/x，**Then** 抛校验失败异常且未发出任何请求

---

### User Story 2 - 路径穿越与形似域名绕过被堵死 (Priority: P1)

攻击性的输入——`../` 序列爬出白名单目录、形似白名单域名的欺骗域名——必须被拦截。这两个是应用层白名单最容易翻车的绕过点。

**Why this priority**: 与 US-1 同优先级：白名单校验"允许 + 拒绝"成对是基础，绕过场景是校验是否真的成立的判据。课件 harness 明确点名这两个回归点。

**Independent Test**: 白名单只含 /workspace，执行读 `/workspace/../../outside/secret.txt` 必须抛校验失败异常；白名单含 `*.example.com`，请求 https://api.example.com/x 放行、https://evil-example.com/x 必须被拒（后者以 example.com 结尾但非子域，经典 endsWith 漏洞）。

**Acceptance Scenarios**:

1. **Given** 路径白名单只含 /workspace，**When** 读 `/workspace/../../outside/secret.txt`，**Then** 抛校验失败异常（normalize 后不再以白名单为前缀）
2. **Given** 域名白名单含 `*.example.com`，**When** 请求 https://api.example.com/x，**Then** 放行
3. **Given** 域名白名单含 `*.example.com`，**When** 请求 https://evil-example.com/x，**Then** 抛校验失败异常（匹配必须带点号边界，`.example.com` 结尾才命中）
4. **Given** 命令白名单只含 echo，**When** 执行带前导空格的 ` echo hi`，**Then** 放行（首 token 提取须容忍前导空白）

---

### User Story 3 - 四个内置 Tool 全部接线，失败走既有审计路径 (Priority: P2)

文件读写、shell、HTTP 三个 Tool 的所有对外方法，以及通知推送（与 HTTP 共享同一份域名白名单），在执行首行做校验；校验失败作为一次普通工具执行失败，复用既有审计路径落库（success=false、error_message 为可读的校验原因），不为沙箱单独新增任何审计逻辑。

**Why this priority**: 接线是把"墙"接进业务链路的最后一环，且是前序节（第20节）已交付 Tool 的改造点；审计复用保证"可审计"卖点不因沙箱引入而增加复杂度。

**Independent Test**: 对每个 Tool 构造白名单外输入，断言：返回失败、错误信息可读、且底层危险动作未执行（文件未创建/未修改、MockWebServer 请求计数为 0、mock 适配器从未被调用）。改造后四个 Tool 的原有测试（正常路径 + 越界拦截）全绿。

**Acceptance Scenarios**:

1. **Given** 路径白名单只含工作区目录，**When** 写文件到 /tmp 下，**Then** 返回失败且 /tmp 下无新文件
2. **Given** 域名白名单不含本地端口，**When** http_get 指向本地 MockWebServer 的 URL，**Then** 返回失败且 MockWebServer 收到 0 个请求
3. **Given** 命令白名单只含 echo,ls，**When** shell 执行白名单外命令，**Then** 返回失败且命令未执行
4. **Given** 通知渠道 URL 不在域名白名单，**When** 调通知推送，**Then** 返回失败且适配器从未被调用
5. **Given** 任意白名单外输入，**When** 校验失败，**Then** 失败按普通工具失败路径记录（成功标志为否、错误信息含校验原因）

---

### Edge Cases

- 白名单配置项为空（未配置任何允许项）→ 等价于"什么都不允许"，任何动作都拒绝，而不是"不校验放行"
- 命令首 token 带前导空格 → 先 trim 再取 token
- 域名大小写变体 → host 比较按精确匹配/通配符规则处理
- 相对路径与绝对路径混用 → 目标路径标准化为绝对路径后再比对白名单；白名单根在启动时同样标准化为绝对路径（相对配置按当前工作目录解析），两者基准一致
- 校验失败异常信息要人能读懂（说明被拒的原因），模型下一轮能据此换路走

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供沙箱抽象：单一入口接收"动作类型 + 目标"两个字段，动作类型取四值（文件读 / 文件写 / shell 命令 / HTTP 请求）。接口不出现"白名单""容器""镜像"等任何一档实现特有的字样，用最重的 microVM 实现反向套该签名也应能干净套入
- **FR-002**: 系统 MUST 提供白名单实现：按动作类型路由到三类校验——文件路径（目标路径标准化为绝对路径后必须以某允许路径为前缀，防止 `../` 穿越）、shell 命令（取命令首 token 与允许命令集比对）、HTTP 域名（解析 host 后做精确/通配符匹配）。三类校验方法不对外暴露，对外只有沙箱入口
- **FR-002a**: 系统 MUST 在启动时把白名单路径根也标准化为绝对路径（相对路径按当前工作目录解析）——只把目标转绝对、根保持原样的话，相对配置（如 `.oryxos`）永远匹配不上绝对目标，运行时读工作区内文件会被全拦
- **FR-003**: 系统 MUST 支持通配符域名：`*.example.com` 匹配 api.example.com 等子域；匹配必须带点号边界（`.example.com` 结尾），形似域名 evil-example.com 不得命中
- **FR-004**: 系统 MUST 将三类白名单作为配置项提供：文件允许路径、shell 允许命令、HTTP 允许域名，各为独立配置键；实现启动时预解析（路径解析为标准化根、命令为集合、域名为列表）
- **FR-005**: 系统 MUST 在文件读写、shell、HTTP 三个 Tool 的每个对外方法执行首行调用沙箱校验，校验通过才执行真正的 IO；通知推送与 HTTP 请求共享同一份域名白名单
- **FR-006**: 系统 MUST 使校验失败成为一次普通工具执行失败：异常被既有执行层接住，按失败记录（成功标志为否、错误信息为可读的校验原因），不新增单独审计路径
- **FR-007**: 系统 MUST 对空白名单语义定义为"什么都不允许"（而非不校验），并在配置说明中写明
- **FR-008**: 系统 MUST 保持前序节已交付工具的既有行为不变：白名单内正常路径照常工作，改造后原有测试全部通过

### Key Entities *(include if feature involves data)*

- **沙箱动作**: 一次"在受控环境里执行某个动作"的意图描述，由动作类型（四值枚举）与目标（纯字符串，具体是路径/命令/URL 由动作类型决定）组成
- **白名单配置**: 三类允许项（文件路径根、shell 命令集、HTTP 域名集），独立配置、启动时载入
- **校验失败原因**: 可读的错误信息，随工具失败路径写入审计（error_message），回填给模型

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 三类校验各自"白名单内放行 + 白名单外拒绝"成对成立：白名单外输入 100% 被拒（自动化测试断言）
- **SC-002**: 两类绕过场景 100% 被堵：`../` 路径穿越爬出白名单被拦；`*.example.com` 通配符下形似域名 evil-example.com 被拒（自动化测试断言）
- **SC-003**: 四个 Tool 各至少一条"白名单外输入被拦且危险动作未发生"的接线回归用例，且改造后四个 Tool 原有测试 100% 全绿（自动化测试断言）
- **SC-004**: 校验失败全部走既有失败审计路径（成功标志为否、错误信息可读），零新增审计逻辑（代码审查核对）
- **SC-005**: 配置说明中写明"空白名单 = 什么都不允许"（文档核对）
- **SC-006**: 接口中立性自查通过：套用更重的隔离实现（如 microVM）于同一签名，不需要新增方法（人工思维练习）

## Assumptions

- 沙箱接口形态（单一方法 + 动作类型四值）在前序节（第20节）接线时已定，本节沿用不变
- 白名单是"劝阻级"防线，防模型犯傻误操作，防不住蓄意绕过；用不可信代码/多租户是扩展阶段的事，本节不做容器/microVM 实现
- 前序节已存在一版沙箱骨架（动作值对象嵌在接口内、配置走 CSV 字符串、域名匹配缺边界判断），本节按本规格拉回规范形态：动作值对象与动作类型改为独立定义、配置改为三个独立键、修复形似域名绕过
- 校验失败复用前序节（第17节）既有的工具失败审计路径，不改执行层
- 接线回归中"危险动作未发生"的证明不依赖给工具新增测试专用构造参数，用可观察副作用（文件不存在、MockWebServer 请求计数为 0、mock 适配器未被调用）断言

## Clarifications

### Session 2026-08-11

- Q: 路径白名单配置为相对路径（如 `allowed-paths: .oryxos`）时如何与绝对路径目标比对？→ A: 启动时把白名单根也标准化为绝对路径（相对路径按当前工作目录解析），与目标标准化基准一致，保证相对配置（如 `.oryxos`）在运行时可用

