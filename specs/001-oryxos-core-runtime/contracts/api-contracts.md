# API Contracts: OryxOS Core Runtime

## Base URL

所有端点前缀：`http://<host>:8080/api/v1`

## 会话管理

### POST /sessions

创建新会话。

**Request Body**:
```json
{
  "profile_name": "weather-agent",
  "channel": "web",
  "user_id": "user-001"
}
```

**Response** (201):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": "web-user-001-weather-agent",
    "status": "active",
    "created_at": "2026-08-01T08:00:00Z"
  },
  "timestamp": "2026-08-01T08:00:00Z"
}
```

### POST /sessions/{id}/messages

发送消息到已有会话。

**Request Body**:
```json
{
  "content": "今天北京天气怎么样？"
}
```

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "response": "北京今天晴，25-32°C，建议穿短袖..."
  },
  "timestamp": "2026-08-01T08:00:05Z"
}
```

**约束**: `content` 最大 32KB，响应最长 60 秒超时返回 504。

### GET /sessions/{id}

查询会话历史。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": "web-user-001-weather-agent",
    "profile_name": "weather-agent",
    "messages": [
      {"role": "user", "content": "今天北京天气怎么样？", "timestamp": "..."},
      {"role": "assistant", "content": "北京今天晴...", "timestamp": "..."}
    ],
    "status": "active",
    "created_at": "...",
    "last_active_at": "..."
  }
}
```

**约束**: 返回最近 100 条消息。

### DELETE /sessions/{id}

归档会话。

**Response** (200):
```json
{
  "code": 0,
  "message": "session archived",
  "timestamp": "..."
}
```

## Agent 调用

### POST /agents/{name}/invoke

无状态调用 Agent。

**Request Body**:
```json
{
  "content": "今天北京天气怎么样？",
  "user_id": "user-001"
}
```

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "response": "北京今天晴，25-32°C..."
  },
  "timestamp": "..."
}
```

## 查询

### GET /profiles

列出所有 Profile。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {"name": "weather-agent", "description": "每日天气", "model": "deepseek-chat", "tools": ["http_get", "notify"]}
  ],
  "timestamp": "..."
}
```

### GET /memory

查询长期记忆。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "core": ["- **2026-08-01**: 用户偏好：关注 AI 和芯片方向"],
    "archival": ["- **2026-08-01**: 用户询问了天气穿搭建议"]
  },
  "timestamp": "..."
}
```

### GET /tools

列出可用 Tool。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {"name": "read_file", "description": "Read file content"},
    {"name": "http_get", "description": "HTTP GET request"},
    {"name": "notify", "description": "Send notification"}
  ],
  "timestamp": "..."
}
```

## 系统状态

### GET /health

健康检查。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "providers": {"deepseek": "UP"}
  },
  "timestamp": "..."
}
```

### GET /info

运行信息。

**Response** (200):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "version": "1.0.0-SNAPSHOT",
    "profiles_count": 3,
    "tools_count": 9,
    "uptime_seconds": 3600
  },
  "timestamp": "..."
}
```

## 统一错误响应

```json
{
  "code": 400,
  "message": "参数错误：session_id 不能为空",
  "timestamp": "..."
}
```

| 状态码 | 说明 |
|--------|------|
| 400 | 参数错误 |
| 404 | 资源不存在 |
| 500 | 内部错误 |
| 503 | Provider 故障 |
| 504 | Agent 调用超时（>60秒） |

## CORS

核心阶段：全开（`*`），扩展阶段加白名单。