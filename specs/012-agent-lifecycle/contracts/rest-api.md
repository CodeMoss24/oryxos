# REST 契约:第 30 节端点全表(以实现回写 5.3 为准)

> 统一信封 `ApiResponse<T>`:`{code, message, data, timestamp}`;code≠200 时前端显示 message。
> 错误码沿用 26 节口径:`400` 参数/校验错误(`code=40001` 类,message 可读)、`404` 资源不存在、`503` Provider 故障/model 未配、`504` 调用超时、`500` 兜底。

## Agent 管理

### POST /api/v1/agents — 创建(脚手架 + 派生注册)

请求:`{"name": "weather-daily", "description": "每天早上查北京天气并推送穿搭建议"}`

- name 已存在 → 400("Agent 已存在: <name>"),**一个目录都不写**
- 成功:脚手架 `.oryxos/agents/<name>/`(AGENT.md + scripts/ + skills/ + REFERENCE.md)→ `deriveProfile` → `ProfileRegistry.register` → 有 schedules 则 `AgentScheduler.registerProfile`;中途失败 → 回滚已写目录
- 响应 200:`AgentView`

### GET /api/v1/agents / GET /api/v1/agents/{name} — 列表 / 详情

- 列表 200:`AgentView[]`(注册表视图,含 resources 存在性)
- 详情:不存在 → 404("agent not found: <name>")

### PUT /api/v1/agents/{name} — 更新(覆写重注册)

请求:任一字段出现即覆写该项;`body` = AGENT.md 正文,`provider` = {name, model, temperature},`schedules` = [{id, cron, zone, message}]

- 覆写 `AGENT.md`(frontmatter + 正文)→ `deriveProfile` 校验(非法 → 400 不写坏目录)→ 重注册
- `schedules` 变化:`AgentScheduler.unregisterProfile(旧)` → `registerProfile(新)`,新旧 cron 不同时跑
- 不存在 → 404;响应 200:`AgentView`

### DELETE /api/v1/agents/{name} — 删除(归档)

- 顺序:`unregisterProfile`(先停定时)→ `ProfileRegistry.remove` → 整个目录移入 `.oryxos/archive/`(不物理删;归档重名加时间戳后缀)
- 不存在 → 404;响应 200:`{status: "archived", name}`

### POST /api/v1/agents/{name}/invoke — 无状态调用(26 节,不变)

请求 `{"user_id"?, "content": "..."}`;60s 超时 504。

### GET /api/v1/agents/{name}/memory — per-agent 专属记忆

- 200:`{"memory": "<MEMORY.md 全文>"}`;Agent 不存在 → 404

### GET /api/v1/agents/{name}/session — 固定会话

- 200:`SessionView{sessionId, profileName, messages[≤100]}`;不存在 → 404
- channel=admin / user=console 固定,`getOrCreate` 幂等

### POST /api/v1/agents/{name}/session/messages — 固定会话发消息

请求 `{"content": "..."}` → 往固定会话触发 ReAct(同 invoke 入口但落固定会话,上下文累积)

- 200:`{sessionId, reply}`;超时 504

### POST /api/v1/agents/{name}/generate-files — 大模型生成文件草稿(不落盘)

请求 `{"description": "每天早上九点查北京天气..."}`

- Agent 不存在 → 404;provider/model 未配 → 503("author model 未配置",不发 model=null)
- 成功:一次 `ProviderService.chat`(落 llm_calls)→ 剥 ``` 围栏 → `AgentLoader.parseAgentMd` 校验(缺 name/provider.name → 400 可读原因)→ 200:`{"AGENT.md": "<草稿>"}`(可含其它相对路径,预览可改)

### POST /api/v1/agents/{name}/files — 保存一组文件(写入即生效)

请求 `{"files": {"AGENT.md": "...", "skills/x.md": "..."}}`

- 先校验 AGENT.md 可解析(非法 → 400,**不写坏目录**)→ `AgentStore.writeAll` → 重注册(schedules 变更先注销旧句柄)
- 响应 200:`AgentView`

## 工作区(只读 + 可编辑)

### GET /api/v1/workspace/tree

- 200:`{agents: FileNode[], archive: FileNode[]}`;FileNode = `{name, type: DIR|FILE, children?}`

### GET /api/v1/workspace/file?path=<相对 .oryxos>

- 防穿越:normalize 后必须落在 `.oryxos/` 内,越界 → 400("path escapes workspace")
- 200:`{path, content}`;文件不存在 → 404

### POST /api/v1/workspace/file

请求 `{"path": "agents/<name>/AGENT.md", "content": "..."}`

- 同一套防穿越(越界 400)
- 目标为 `agents/<name>/AGENT.md` → 走 `AgentLifecycleService.update`(写 + 校验 + 重注册);其余文件直接写盘(父目录不存在则创建)
- 200:`{path, written: true}`

## 移除

- `GET /api/v1/memory`(全局)移除(5.2.1),前端"长期记忆"页随之改造为 Agent 详情"记忆"tab。

## 新增配置键(application.yaml)

```yaml
oryxos:
  author:
    provider: deepseek   # 缺省 = oryxos.providers 第一个;生成链路用
    model: deepseek-chat # 留空 → 生成端点 503
```

## 内部约定(非端点)

- 生成用 Profile:`name="__author__"`,provider = author 配置;sessionId = `author-generator`(llm_calls 审计落点)
- `AgentScheduler.unregisterProfile(profile)`:遍历 schedules → `scheduledTasks` 句柄 `cancel(false)` → 移除 scheduledTasks/taskRefs/scheduledTaskIds 三个索引;不动 taskLocks
- `WorkspaceWatcher`:守护线程,启动全量扫 + WatchService 监听 agents/;事件 → 存在判断 → `lifecycle.register(agentDir)` / 注销;坏目录 warn 跳过
