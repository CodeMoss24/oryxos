# 契约：CLI 12 子命令

主入口 `OryxOsCli`（`oryxos-cli`，整个程序的 main 函数），Picocli 注册。轻命令不启 Spring；重命令由 `OryxOsApplication`（boot）起 Spring 后派发。

| 命令 | 类型 | 行为 |
|------|------|------|
| `oryxos init` | 轻 | 初始化 OryxOS 工程（工作区目录/默认文件） |
| `oryxos status` | 轻 | 查看配置与运行状态 |
| `oryxos chat [--profile <name>] [--message "xxx"]` | 重 | 交互式对话：读 stdin 写 stdout，`/quit` 退出；`--profile` 默认 `default`；`--message` 发单条后退出 |
| `oryxos serve` | 重 | 启动 HTTP API 服务（SpringApplication.run 起 Tomcat 常驻） |
| `oryxos gateway` | 重 | 启动多渠道守护进程（扩展阶段补多通道，命令占位） |
| `oryxos profile list` | 轻 | 列出所有 Agent/Profile（`.oryxos/agents/` 目录） |
| `oryxos profile create <name>` | 轻 | 创建新 Profile（生成最小 AGENT.md 模板） |
| `oryxos profile show <name>` | 轻 | 查看 Profile 详情 |
| `oryxos profile delete <name>` | 轻 | 删除 Profile（整个目录） |
| `oryxos provider list` | 轻 | 列出已配置的 Provider |
| `oryxos tool list` | 轻 | 列出已注册的 Tool |
| `oryxos session list` | 轻 | 列出会话历史（JDBC 直连 `.oryxos/oryxos.db` 读 sessions 表，不启 Spring） |

## chat 交互契约（CliChannel）

1. `getOrCreate("cli", currentUser(), profileName)` 取会话——幂等，历史自动带回。
2. 循环：打印 `> ` → 读一行 → trim：
   - `/quit`（忽略大小写）→ 退出循环、命令结束；
   - 空行 → 不交给引擎，继续循环；
   - 其他 → `agentService.process(session, input)` 打印回复。
3. `currentUser()` = `System.getProperty("user.name")`，兜底 `"console"`。

## 注入契约（重命令）

- ChatCommand 标 `@Component`，boot 的 `PicocliConfig` 用 `SpringCommandFactory` 创建命令实例（@Autowired 生效）。
- boot 启动类排除 `PicocliAutoConfiguration`，避免其 CommandLineRunner 与 `OryxOsApplication` 双执行。
- 启动日志须出现 "Found N JPA repository interfaces" 且 N > 0。
