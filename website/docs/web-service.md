# Web Service

核心阶段 10 个 REST 端点，只做查询和调用，不做创建。

## 端点列表

| 类别 | 端点 | 说明 |
|------|------|------|
| 会话管理 | `POST /api/v1/sessions` | 创建会话 |
| 会话管理 | `POST /api/v1/sessions/{id}/messages` | 发消息 |
| 会话管理 | `GET /api/v1/sessions/{id}` | 查历史 |
| 会话管理 | `DELETE /api/v1/sessions/{id}` | 归档会话 |
| Agent 调用 | `POST /api/v1/agents/{name}/invoke` | 无状态调用 |
| Profile 信息 | `GET /api/v1/profiles` | 列 Profile |
| Memory 操作 | `GET /api/v1/memory` | 查长期记忆 |
| Tool 信息 | `GET /api/v1/tools` | 列可用 Tool |
| 系统状态 | `GET /api/v1/health` | 健康检查 |
| 系统状态 | `GET /api/v1/info` | 运行信息 |

## 设计要点

- **错误码规范**：400 / 404 / 500 / 503
- **CORS**：核心阶段全开，方便调试
- **单条消息**：最大 32KB
- **Session 历史**：返回最多最近 100 条
- **Agent 调用**：最长 60 秒超时，返回 504

## 核心阶段不做

认证、流式 SSE、WebSocket、RBAC、限流、Agent 目录上传、Webhook 触发、Prometheus metrics、OpenAPI spec —— 放扩展阶段。
