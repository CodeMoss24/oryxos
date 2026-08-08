# Research: OryxOS Core Runtime

**Phase 0 output** — Technical decisions and implementation approach consolidation.

## Overview

All major technical decisions for OryxOS Core Runtime are pre-defined in the [constitution](../.specify/memory/constitution.md) and [CLAUDE.md](../../CLAUDE.md). No NEEDS CLARIFICATION items exist. This document consolidates the rationale behind key choices for reference during implementation.

## Key Decisions

### Decision 1: 自实现 ReAct Loop 而非使用 Spring AI Agent

| 方面 | 结论 |
|------|------|
| **决策** | 自实现 ReActLoop（约数十行 Java），不依赖 Spring AI 的 Agent 抽象 |
| **理由** | 完全可控，保留未来定制循环行为的空间（并行 Tool 调用、Agent 间委托） |
| **替代方案** | Spring AI 自带 Agent 执行器 — 禁用，否则 Tool 会被调两次 |
| **影响** | 需自行管理迭代次数、消息累积、上下文截断 |

### Decision 2: Spring AI 使用边界

| 方面 | 结论 |
|------|------|
| **决策** | ✅ Provider 抽象 + 协议转换 + @Tool schema 生成；❌ 禁用自动 tool 执行 |
| **理由** | 避免 tool 被调两次，ReAct 循环完全由 OryxOS 掌控 |
| **验证方式** | 代码审查时检查是否有 Spring AI 的自动 tool calling 机制被启用 |

### Decision 3: SQLite + MEMORY.md 持久化

| 方面 | 结论 |
|------|------|
| **决策** | 结构化数据用 SQLite + Spring Data JPA，长期记忆用 MEMORY.md 文件 |
| **理由** | 单二进制部署，零外部依赖；MEMORY.md 人可读、git 可跟踪 |
| **替代方案** | PostgreSQL/pgvector — 引入外部进程依赖，放扩展阶段 |
| **风险** | SQLite ALTER TABLE 能力有限，需手工维护建表脚本或引入 Flyway |

### Decision 4: 同步执行模型 + Virtual Thread

| 方面 | 结论 |
|------|------|
| **决策** | 同步阻塞 + Java 21 virtual thread |
| **理由** | 直观简洁，无需响应式编程，单节点撑高并发 |
| **替代方案** | WebFlux/响应式 — 调试复杂，收益被 virtual thread 抵消 |

### Decision 5: WhitelistSandbox 而非 SecurityManager

| 方面 | 结论 |
|------|------|
| **决策** | Sandbox 接口 + WhitelistSandbox 实现（应用层 Path/Pattern 白名单） |
| **理由** | SecurityManager 在 JDK 17 起废弃、JDK 21 已不可用 |
| **升级路径** | 接口不变：白名单 → 容器隔离 → microVM |

### Decision 6: 一个目录 = 一个 Agent

| 方面 | 结论 |
|------|------|
| **决策** | `.oryxos/agents/<name>/AGENT.md` 定义 Agent（frontmatter = profile，正文 = 指令） |
| **理由** | 借鉴 Anthropic Agent Skills 目录形态，零代码定义 Agent |
| **影响** | AGENT.md 不进 ToolRegistry，归 ContextLoader 注入 system prompt |

### Decision 7: Provider 显式映射

| 方面 | 结论 |
|------|------|
| **决策** | 维护 provider name → ChatModel 显式映射，不靠类型扫描 |
| **理由** | 多 Provider 时 Bean 类型相同，类型扫描无法可靠区分 |

## Implementation Approach

### 现有代码状态

项目已有 69 个 Java 文件（~3048 行），覆盖全部 9 个 Maven 模块。核心实现重点在于：
1. 修通编译链路（Spring Bean 配置、依赖注入）
2. 验证 ReActLoop 没有误用 Spring AI 自动 tool 执行
3. SQLite 建表脚本就位
4. 补齐测试覆盖
5. 创建 `.oryxos/` 工作区目录结构

### 实施顺序

按 spec 中 User Story 优先级：
- **P1**: US-1 (Init + Provider) + US-2 (ReAct + CLI) — 第 1 周
- **P2**: US-3 (Memory) + US-4 (Tool) — 第 2 周
- **P2-P3**: US-4 (Web Service) — 第 3 周
- **P3**: US-5 (Scheduler + Notify + Demo) — 第 4 周