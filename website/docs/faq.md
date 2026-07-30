# 常见问题

## OryxOS 跟其他 Agent 框架有什么区别？

OryxOS 不是一个 Agent 框架，而是一个 Agent 操作系统。框架帮你写一个 Agent，OryxOS 帮你运行和管理一群 Agent。核心区别：私有部署、全链路审计、多 Agent 共享底座、自然语言定义 Agent。

## 为什么用 Java 而不是 Python？

企业级部署。严监管行业（金融、医疗、政务）的 IT 基础设施以 Java 为主，运维团队熟悉 Java 工具链。JDK 21 的 virtual thread 让 Java 在高并发场景下也有竞争力。单 JAR 部署，运维简单。

## 为什么自实现 ReAct 而不是用 LangChain？

可控性和简洁性。自实现的 ReAct 循环只有几十行代码，行为完全可控，不依赖外部框架的抽象。Spring AI 只用 Provider 抽象 + 协议转换，Tool 调度完全由 OryxOS 自己控制。

## 数据存在哪里？

核心阶段全部存本地：SQLite（会话、审计、元数据）+ 文件系统（Agent 定义、Memory、配置）。数据不出企业，不依赖任何云服务。

## 支持哪些 LLM？

支持所有 OpenAI 兼容协议的模型，包括 OpenAI、通义千问、智谱、DeepSeek 等。通过 Spring AI 的 Provider 抽象统一对接，运行时切换无锁定。

## 如何保证安全？

三层安全机制：
1. **Sandbox**：文件路径、命令、域名白名单
2. **审计**：`tool_invocations` 和 `llm_calls` 两张表 day one 落库
3. **凭证**：API Key 走企业密钥体系，不落地

## 能跑多少个 Agent？

核心阶段单机运行，实例数量取决于硬件资源和 Agent 负载。virtual thread 提供高并发支撑。扩展阶段支持多副本部署和分布式。
