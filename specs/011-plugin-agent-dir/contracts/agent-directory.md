# Contract: Agent Directory (`.oryxos/agents/<name>/`)

**Date**: 2026-08-16 | **Spec**: [spec.md](../spec.md)

本节对外契约是**文件系统目录格式**（业务方零 Java 定义一个 Agent 的接口），非 REST/CLI 新端点（API 管理放下节）。

## 目录契约

一个目录 = 一个 Agent。目录名 = Agent 唯一标识。

```
.oryxos/agents/<name>/
├── AGENT.md            # 必需
├── REFERENCE.md        # 可选
├── skills/<x>.md       # 可选（0 或多个）
└── scripts/<x>         # 可选（0 或多个）
```

## AGENT.md 契约

YAML frontmatter（`---` 包围）+ Markdown 正文。

### frontmatter
```yaml
---
name: <可省, 以目录名为准>
description: <可省>
identity:
  agent_name: <可省>
  prompt: <可省>
provider:                    # 必填,name 非空
  name: <provider name>
  model: <可省>
  temperature: <可省>
tools: [<tool name>, ...]    # 可省, 引用未注册能力告警不阻断
notify_channels:             # 可省
  - type: webhook
    url: ${ENV_VAR}
schedules:                   # 可省
  - id: <必填, 缺则该条跳过>
    cron: "<表达式>"
    zone: <可省, 缺省系统时区>
    message: <可省>
---
<正文: 任务指令, 触发时进 system prompt, 常驻、不缓存、改完即时生效>
```

### 正文
- 进 system prompt（`ContextLoader` 注入，与 Bootstrap 同层）。
- 子指令/参考/脚本**不预载**：正文指引"读 `skills/x.md`"→ 底座 `read_file` 按需取；"运行 `scripts/x`"→ 底座 `shell` 按需跑（产出进上下文、代码不进）。

## 校验契约（两层）
1. **core（`AgentLoader.deriveProfile`）**：`provider.name` 缺 → `IllegalArgumentException("Agent '<name>': missing required field 'provider.name'")`，点名 Agent+字段，单 Agent 失败不阻断其余。启动扫描与运行时注册走同一段、同一异常类型+同一消息。
2. **boot（扫描后）**：`provider.name` → ChatModel 映射校验（复用 16 节 `ProviderService`），未映射 `log.warn` 不阻断。
3. **tools 告警（core `AgentLoader.warnUnregisteredTools`）**：未注册能力 `log.warn` 不阻断。

## 信任边界（脚本）
装一个带 `scripts/` 的 Agent = 信任其作者。脚本能自己发网络请求、绕过内置 `http_get` 域名白名单。核心阶段沙箱对脚本只做"解释器 + 该 Agent scripts 目录"两道白名单；容器/网络隔离放扩展。

## 不做的契约
- 跨 Agent 共享能力库 / `use_skill` / 全局 Skill 索引。
- Agent 版本管理、同名冲突策略、文件监听热加载（下节）。
