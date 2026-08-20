# Quickstart: 动态管理 Agent 验证指引

> Phase 1 输出。自动化判定 = 课件 harness 测试套件(`mvn clean verify` 全绿);本文件是运行与人工验收指引。

## 前置

- `mvn install`(改依赖模块后先装,避免 m2 旧 jar 坑)
- 工作区 `.oryxos/` 存在;生成链路需要 `application.yaml` 配 `oryxos.author.provider/model`(或环境变量);`DEEPSEEK_API_KEY` 或走 mock

## 自动化判定(harness 已判卷)

```bash
mvn clean verify            # 全绿 = 自动化部分通过(含 P3C/SpotBugs/FindSecBugs/PMD)
```

关键回归(课件 harness 点名):

| 测试 | 守点 |
|---|---|
| `AgentLifecycleServiceTest` | create 按序;name 冲突一步拒(零目录写入);注册失败回滚(verify agentStore.delete + never registerProfile + registry 无残留);create 与 watcher 同一段 register(agentDir);delete InOrder(unregisterProfile→remove→archive);update schedules 先注销后注册 |
| `WorkspaceWatcherTest` | 手工丢目录 → register 出现于 ProfileRegistry(免重启);删目录 → 注销;坏目录不拖垮 |
| `WorkspaceApiControllerTest` | tree 结构;`file?path=../../etc/passwd` → 400(关键回归);正常文件返回内容 |
| `GenerateTest` | 产出可被 AgentLoader 解析;只生成不落盘不注册;非法产出 → 400 |
| `AgentApiControllerTest` | 薄转发;冲突 400、不存在 404;统一 ApiResponse |

## 人工验收(harness 判不了,等人工过)

### 1. 建 Agent 即上线(真链路)

```bash
java -jar oryxos-boot/target/oryxos-boot-*.jar &   # 或 mvn -pl oryxos-boot spring-boot:run -am
curl -s -X POST localhost:8080/api/v1/agents -H 'Content-Type: application/json' \
  -d '{"name":"weather-demo","description":"每天早上九点查北京天气并推送穿搭建议"}'
curl -s localhost:8080/api/v1/agents | jq .          # 立刻可见,无需重启
```

- 把创建的 AGENT.md 的 `schedules` cron 临时改成每分钟,到点真跑(日志/会话可见);有 webhook 则收到推送
- 预期:`curl localhost:8080/api/v1/agents/weather-demo` 返回 200 AgentView

### 2. 丢目录也即上线

```bash
mkdir -p .oryxos/agents/hand-dropped && cp docs/class/第29节*.md > /dev/null  # 或直接 cp 一个合法 AGENT.md 进去
# 等几秒(WatchService 事件)
curl -s localhost:8080/api/v1/agents | jq '.data | map(.name)'   # 出现 hand-dropped
rm -rf .oryxos/agents/hand-dropped
# 几秒后列表消失(注销)
```

### 3. 一句话生成(页面预览)

```bash
curl -s -X POST localhost:8080/api/v1/agents/weather-demo/generate-files \
  -H 'Content-Type: application/json' -d '{"description":"每天早上九点查北京天气,给出穿搭建议"}'
# → 200 返回 AGENT.md 草稿;再查 agents 目录无新文件(不落盘)
curl -s -X POST localhost:8080/api/v1/agents/weather-demo/files \
  -H 'Content-Type: application/json' -d '{"files":{"AGENT.md":"<改过的草稿>"}}'
# → 保存即生效;AGENT.md 非法时 → 400 且目录不被写坏
```

### 4. 目录落对地方

- 建 Agent 后 `.oryxos/agents/<name>/` 有 AGENT.md(+scripts/+skills/+REFERENCE.md),frontmatter 格式与 29 节手写一致

### 5. 文件浏览器(管理台"工作区"或直接 API)

```bash
curl -s 'localhost:8080/api/v1/workspace/tree' | jq .                      # agents/ + archive/
curl -s 'localhost:8080/api/v1/workspace/file?path=agents/weather-demo/AGENT.md'
curl -s -o /dev/null -w '%{http_code}' 'localhost:8080/api/v1/workspace/file?path=../../etc/passwd'  # 400
curl -s -X POST localhost:8080/api/v1/workspace/file -H 'Content-Type: application/json' \
  -d '{"path":"agents/weather-demo/AGENT.md","content":"---\nname: weather-demo\n..."}'   # AGENT.md 走 update 重注册
```

### 6. 删除可追溯

```bash
curl -s -X DELETE localhost:8080/api/v1/agents/weather-demo
ls .oryxos/archive/                            # 目录在,不物理删
curl -s localhost:8080/api/v1/agents | jq .    # 列表不再有
# 历史 llm_calls / tool_invocations 仍查得到(SQLite)
```

### 7. 管理台全流程

浏览器打开 `http://localhost:8080/admin`:新建 Agent(name+description)→ 列表可见 → 详情 5 tab(基本信息/生成→预览→保存并生效/文件浏览编辑/会话发消息/记忆)→ 删除(二次确认)→ 归档可见。

## 关键命令对照

| 命令 | 预期 |
|---|---|
| `mvn clean verify` | 全绿(harness 判卷) |
| `curl POST /api/v1/agents` 冲突名 | 400,`.oryxos/agents/` 无新目录 |
| `curl GET /api/v1/workspace/file?path=../../etc/passwd` | 400 |
| `curl GET /api/v1/agents/不存在` | 404 |
| 手工拷目录进 `.oryxos/agents/` | 秒级出现在 `GET /agents` |
