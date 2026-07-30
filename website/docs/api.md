# API 参考

## REST API

### 创建会话

```
POST /api/v1/sessions
Content-Type: application/json

{
  "profile": "default"
}
```

### 发送消息

```
POST /api/v1/sessions/{id}/messages
Content-Type: application/json

{
  "content": "你好，帮我查一下今天北京的天气"
}
```

### 查询会话历史

```
GET /api/v1/sessions/{id}
```

### 归档会话

```
DELETE /api/v1/sessions/{id}
```

### 无状态调用 Agent

```
POST /api/v1/agents/{name}/invoke
Content-Type: application/json

{
  "message": "帮我生成周报",
  "profile": "weekly-report"
}
```

### 列出 Profile

```
GET /api/v1/profiles
```

### 查询长期记忆

```
GET /api/v1/memory
```

### 列出可用 Tool

```
GET /api/v1/tools
```

### 健康检查

```
GET /api/v1/health
```

### 运行信息

```
GET /api/v1/info
```

## 错误码

| HTTP 状态码 | 说明 |
|-------------|------|
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用 |
| 504 | Agent 调用超时（最长 60 秒） |
