# API Reference

## REST API

### Create Session

```
POST /api/v1/sessions
Content-Type: application/json

{
  "profile": "default"
}
```

### Send Message

```
POST /api/v1/sessions/{id}/messages
Content-Type: application/json

{
  "content": "Hello, check the weather in Beijing today"
}
```

### Get Session History

```
GET /api/v1/sessions/{id}
```

### Archive Session

```
DELETE /api/v1/sessions/{id}
```

### Stateless Agent Invocation

```
POST /api/v1/agents/{name}/invoke
Content-Type: application/json

{
  "message": "Generate weekly report",
  "profile": "weekly-report"
}
```

### List Profiles

```
GET /api/v1/profiles
```

### Query Long-term Memory

```
GET /api/v1/memory
```

### List Available Tools

```
GET /api/v1/tools
```

### Health Check

```
GET /api/v1/health
```

### Runtime Info

```
GET /api/v1/info
```

## Error Codes

| HTTP Status | Description |
|-------------|-------------|
| 400 | Bad request |
| 404 | Not found |
| 500 | Internal server error |
| 503 | Service unavailable |
| 504 | Agent invocation timeout (max 60 seconds) |
