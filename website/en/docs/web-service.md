# Web Service

10 REST endpoints in the core phase — queries and invocations only, no creation endpoints.

## Endpoints

| Category | Endpoint | Description |
|----------|----------|-------------|
| Sessions | `POST /api/v1/sessions` | Create session |
| Sessions | `POST /api/v1/sessions/{id}/messages` | Send message |
| Sessions | `GET /api/v1/sessions/{id}` | Get history |
| Sessions | `DELETE /api/v1/sessions/{id}` | Archive session |
| Agent | `POST /api/v1/agents/{name}/invoke` | Stateless invocation |
| Profile | `GET /api/v1/profiles` | List profiles |
| Memory | `GET /api/v1/memory` | Query long-term memory |
| Tool | `GET /api/v1/tools` | List available tools |
| System | `GET /api/v1/health` | Health check |
| System | `GET /api/v1/info` | Runtime info |

## Design Notes

- **Error codes**: 400 / 404 / 500 / 503
- **CORS**: Fully open in core phase for debugging
- **Message size**: Max 32KB per message
- **Session history**: Returns up to 100 most recent entries
- **Agent invocation**: 60-second timeout, returns 504

## Not in Core Phase

Authentication, streaming SSE, WebSocket, RBAC, rate limiting, Agent directory upload, Webhook triggers, Prometheus metrics, OpenAPI spec — deferred to extension phase.
