# 快速验证指南：ReAct 循环引擎

**日期**: 2026-08-08 | **关联计划**: [plan.md](./plan.md)

## 前置条件

- JDK 21 + Maven
- 16 节 Provider 模块已通过 `mvn test`
- SQLite 数据库 `.oryxos/oryxos.db` 已初始化（运行过 16 节测试或手动执行 schema.sql）

## 运行测试

### 跑全部单测（日常）

```bash
cd /home/pengpeng/ORYXOS
mvn test -pl oryxos-core,oryxos-storage
```

预期：5 个测试类全部绿。

### 跑单个测试类（调试时）

```bash
mvn test -pl oryxos-core -Dtest=ReActLoopTest
mvn test -pl oryxos-core -Dtest=PromptBuilderTest
mvn test -pl oryxos-core -Dtest=ToolExecutorTest
mvn test -pl oryxos-core -Dtest=AgentServiceTest
mvn test -pl oryxos-core -Dtest=ContextLoaderTest
```

### 跑全量门禁（最终验收）

```bash
mvn clean verify
```

预期：全绿（含 P3C/SpotBugs/FindSecBugs/PMD 静态检查）。

## 关键验收点

### ReActLoopTest

| 场景 | 预期 |
|------|------|
| 无工具调用一轮收尾 | `providerPort.chat()` 调用 1 次，返回文本 |
| 有工具调用多轮 | ToolExecutor 被调用，工具结果回填 Session |
| 转满最大轮数强制停 | `providerPort.chat()` 恰好调用 maxIterations 次，返回"达到最大轮数" |
| 每轮响应累积 | `session.getMessages()` 包含每轮的 assistant/tool 消息 |

### PromptBuilderTest

| 场景 | 预期 |
|------|------|
| 四部分顺序 | system prompt 在最前、history 在后、system prompt 含日期 |
| 历史超 N 轮截断 | 30 轮输入 → 输出最多 20 轮 |
| system prompt 含日期 | 末尾包含当前日期时间的文本 |

### ToolExecutorTest

| 场景 | 预期 |
|------|------|
| 成功写审计 success=true | `toolInvocationRepository.save()` 被调用，entity.success=true |
| 失败写审计 success=false | entity.success=false，entity.errorMessage 不为空 |

### AgentServiceTest

| 场景 | 预期 |
|------|------|
| ProfileContext 可取到 Profile | 处理期间 `ProfileContext.get()` 非 null |
| 抛异常时 finally 清除 | `assertThrows(...)` 后 `ProfileContext.get()` 为 null |
| Session 被持久化 | `sessionRepository.save()` 被调用 |

### ContextLoaderTest

| 场景 | 预期 |
|------|------|
| 改文件后重新读到 | 修改 Bootstrap 文件 → 下一次 build 读到新内容 |
| Bootstrap 缺失 WARN | 日志输出含 WARN 级别记录 |

## 剩余人工验证

自动化测试全绿后，以下需要人工确认：

1. **真模型连通性**：用 Demo 一的对话版（`oryxos chat`）问一次真天气，验证 ReAct 循环真正跑通——Agent 调了 http_get、拿到数据、给出建议。
2. **循环自实现**：code review 确认 ReActLoop 没有依赖 Spring AI 的 Agent 抽象或任何外部 Agent 运行时。
3. **死循环兜底、累积、截断、失败审计**：已由 harness 覆盖，`mvn test` 绿即打勾。