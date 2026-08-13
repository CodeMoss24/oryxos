# API 契约:核心阶段 11 个 REST 端点

统一前缀 `/api/v1`。所有响应共用信封:`{"code": <int>, "message": <string>, "data": <any>, "timestamp": <instant>}`。错误码语义:400 参数错误、404 资源不存在、500 内部错误(统一话术"内部错误",不泄漏内部细节)、503 Provider 故障、504 调用超时。CORS 全开。

## 1. 会话管理(5 个)

### POST /api/v1/sessions —— 创建会话
- 请求体:`{"profile_name": <必填, string>, "user_id": <可选, 默认 "anonymous">, "channel": <可选, 默认 "web">}`
- `profile_name` 缺失 → 400
- 语义:按三元组幂等获取或创建(SessionManager),返回 `data: {"session_id": <string>}`
- 审计:无 LLM/工具调用

### POST /api/v1/sessions/{id}/messages —— 发消息
- 请求体:`{"content": <string>}`
- `content` 为 null 或长度 > 32×1024 → 400(引擎不被调用)
- 会话不存在 → 404
- 正常:`data: {"reply": <string>}`,回复由与 CLI 相同的处理引擎产出且**恰被调用一次**
- 审计:LLM 调用/工具执行照记(经既有链路)

### GET /api/v1/sessions/{id} —— 查历史
- 会话不存在 → 404
- 正常:`data: [{"role": ..., "content": ...}, ...]`,**最多最近 100 条**

### DELETE /api/v1/sessions/{id} —— 归档
- 会话不存在 → 404
- 正常:置 archived 并持久化,`data: {"status": "archived"}`

### GET /api/v1/sessions —— 会话列表(只读扩展,第 11 个端点)
- 正常:`data: [{"session_id", "profile_name", "channel", "user_id", "status", "last_active_at"}, ...]`,按最近活跃倒序
- 无请求体;仅供管理台与运维查询,无创建/修改能力

## 2. Agent 调用(1 个)

### POST /api/v1/agents/{name}/invoke —— 无状态调用
- 请求体:`{"content": <string>, "user_id": <可选, 默认 "anonymous">}`
- Agent 名不存在 → 404
- 处理超 60 秒 → 504,请求终止
- 正常:`data: {"reply": <string>}`;与 CLI 同一处理引擎
- 会话身份:`(channel="web", user=user_id, profile=name)` 经 SessionManager 幂等复用

## 3. 信息查询(3 个)

### GET /api/v1/profiles —— Profile 列表
- 正常:`data`: 全部已加载 Profile 概要;无修改入口

### GET /api/v1/memory —— 长期记忆全文
- 正常:`data: {"memory": <完整记忆数据, 不截断>}`(门面 `readAll()`,三档后端原样读取)

### GET /api/v1/tools —— 工具列表
- 正常:`data`: 全部已注册工具名称与描述;无修改入口

## 4. 系统状态(2 个)

### GET /api/v1/health —— 健康检查
- 正常:`data: {"status": "UP"}`;不依赖模型

### GET /api/v1/info —— 运行信息
- 正常:`data`: 名称/版本/Java 版本/时间 + `providers: {"<name>": "UP"|"DOWN", ...}`(实时探测:带超时轻量请求,失败/未配置为 DOWN;探测失败不影响端点 200)
- 不依赖真模型

## 管理平台静态资源(非 /api/v1)

- `/admin` 及子路径 → SPA(五页只读管理台);子路径刷新回落 `admin/index.html`;静态文件存在时直出
- `/swagger-ui.html`(springdoc 自动生成文档,覆盖全部端点)
