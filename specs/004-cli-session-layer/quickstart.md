# 快速验证指南：第 18 节

## 自动化门禁

```bash
# 全量门禁（含 P3C/SpotBugs/FindSecBugs/PMD 静态检查）
mvn clean verify

# 只跑本节测试（storage 模块会话层）
mvn -pl oryxos-storage test -Dtest='SessionManagerTest,SessionRepositoryTest'

# 前序节回归
mvn -pl oryxos-core,oryxos-provider,oryxos-storage test
```

预期：全部绿。`SessionManagerTest` 覆盖幂等/隔离/id 唯一；`SessionRepositoryTest` 覆盖手工建表能存能读、messages_json 完整回读、模拟重启历史还在。

## 人工验收清单（课件"五、做完怎么验"）

| # | 动作 | 预期 |
|---|------|------|
| 1 | `mvn -pl oryxos-boot -am spring-boot:run -Dspring-boot.run.arguments="chat"`（或打包后 `oryxos chat`） | 进入交互，一问一答多轮，`/quit` 退出；启动日志 "Found N JPA repository interfaces" 的 N > 0 |
| 2 | `oryxos chat --profile weather` | 与指定 Agent 对话，Session 落同一存储 |
| 3 | `oryxos init`、`oryxos profile list` | 秒回（无 Spring 启动延迟） |
| 4 | `oryxos serve` | Tomcat 启动、8080 常驻 |
| 5 | `oryxos session list` | 列出历史会话（session_id / profile / channel / user / 最后活跃时间） |
| 6 | 12 个子命令逐个 `--help` | Picocli 帮助正常 |
| 7 | 会话幂等/隔离/持久化 | 已由 harness 覆盖，`mvn test` 绿即打勾 |
| 8 | 三种模式共享 Profile 和 Session 存储 | chat 后 session list 能看到同一条历史；切换模式数据不丢 |
| 9 | 重启后继续对话 | 再次 `oryxos chat` 同三元组 → 历史还在（多轮上下文延续） |

## 演示脚本（Demo 一对话版）

```bash
mvn -pl oryxos-boot -am package -DskipTests -q
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat --profile default
# > 你好
# > 帮我查一下今天北京天气（触发 ReAct 调 HTTP Tool，前提 Provider/Tool 已配）
# > /quit
```
