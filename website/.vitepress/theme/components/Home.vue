<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isEn = computed(() => lang.value === 'en-US')
const t = (zh, en) => isEn.value ? en : zh

const capabilities = computed(() => [
  {
    icon: '🔄',
    title: t('ReAct 循环引擎', 'ReAct Loop Engine'),
    subtitle: t('自实现 Reason+Act · 最大 10 轮 · 虚拟线程高并发', 'Custom Reason+Act · max 10 iterations · virtual threads'),
    code: `# ReAct loop pseudocode
while (iterations < maxIterations) {
  response = llm.call(prompt + history + tools)
  if (!response.hasToolCall) return response
  result = toolExecutor.execute(response.toolCall)
  history.append(toolMessage(result))
}`,
  },
  {
    icon: '🧠',
    title: t('三层 Memory 架构', 'Three-tier Memory'),
    subtitle: t('会话历史 + 核心记忆(永不断) + 归档记忆(检索)', 'Session history + Core memory(never truncated) + Archival(searchable)'),
    code: `# Core memory — full injection, never truncated
save_memory("企业合规要求:所有调用必须审计", scope=CORE)

# Archival memory — keyword search + truncation
save_memory("用户偏好:输出用中文", scope=ARCHIVAL)

# Three backends, switch via config
memory.backend = markdown   # default
memory.backend = sqlite     # embedded
memory.backend = mem0       # cloud`,
  },
  {
    icon: '🔧',
    title: t('统一 Tool 体系', 'Unified Tool System'),
    subtitle: t('9 个内置 + MCP 零代码接入 + @Tool Java 插件', '9 built-in + zero-code MCP + @Tool Java plugins'),
    code: `# Built-in tools (5 groups, 9 tools)
FileTools   → read_file / write_file / list_dir
ShellTools  → shell
HttpTools   → http_get / http_post
MemoryTools → save_memory / recall_memory
NotifyTools → notify

# MCP integration — zero code
# Just configure in .oryxos/mcp_servers.yaml
mcp_servers:
  - name: enterprise-erp
    command: npx @your-company/erp-mcp-server`,
  },
])

const scenarios = computed(() => [
  {
    num: '01',
    title: t('每日天气 Agent', 'Daily Weather Agent'),
    desc: t('光杆 AGENT.md 定义意图,ReAct 自动调 HTTP Tool 查天气,NotifyTools 推送结果,AgentScheduler 定时触发——零 Java 代码。', 'Bare AGENT.md defines intent. ReAct calls HTTP Tool, NotifyTools pushes result, AgentScheduler triggers on cron — zero Java code.'),
  },
  {
    num: '02',
    title: t('多 Agent 共享基础设施', 'Multi-agent shared infrastructure'),
    desc: t('多个 Agent 共享同一套 Provider / Memory / Tool / Sandbox,各自独立 AGENT.md 定义行为,互不干扰又复用底座。', 'Multiple Agents share Provider / Memory / Tool / Sandbox. Each has its own AGENT.md, independent yet reusing the same foundation.'),
  },
  {
    num: '03',
    title: t('企业合规审计', 'Enterprise compliance audit'),
    desc: t('tool_invocations 和 llm_calls 两张表 day one 落库,每次 Tool 调用和 LLM 请求都有完整记录,满足审计要求。', 'tool_invocations and llm_calls tables are written from day one. Every Tool call and LLM request is fully recorded for audit compliance.'),
  },
  {
    num: '04',
    title: t('私有化部署', 'Private deployment'),
    desc: t('部署在企业自己的 K8s 或物理服务器,SQLite 存储,数据不出企业,满足严监管行业的数据主权要求。', 'Deploy on your own K8s or bare metal. SQLite storage, data never leaves your premises — data sovereignty for regulated industries.'),
  },
  {
    num: '05',
    title: t('MCP 零代码接入企业系统', 'Zero-code MCP integration'),
    desc: t('在 mcp_servers.yaml 配置一行即可接入企业 ERP / CRM / 内部 API,Agent 通过 ReAct 自动发现并调用,不需要写 Java 代码。', 'One line in mcp_servers.yaml connects your ERP / CRM / internal APIs. Agent discovers and calls via ReAct — no Java code needed.'),
  },
  {
    num: '06',
    title: t('CLI + Web 双通道', 'CLI + Web dual channel'),
    desc: t('开发者用 oryxos chat 交互调试,生产环境用 REST API /serve 启动服务,同一套 Agent 逻辑两种触发方式。', 'Developers use oryxos chat for interactive debugging. Production uses REST API via /serve. Same Agent logic, two trigger channels.'),
  },
  {
    num: '07',
    title: t('定时自主 Agent', 'Scheduled autonomous Agent'),
    desc: t('AgentScheduler 按 cron 表达式自动触发 Agent,支持时区配置,执行结果写入审计表,失败也记录——"钟推"模式。', 'AgentScheduler triggers Agents by cron expression with timezone support. Results and failures are both logged — "clock-driven" mode.'),
  },
  {
    num: '08',
    title: t('Sandbox 安全边界', 'Sandbox security boundary'),
    desc: t('文件路径白名单、命令白名单、域名白名单,三层防护防止 Agent 越权。接口先行,未来升级容器隔离或 microVM 不改调用方。', 'File path, command, and domain whitelists — three layers prevent Agent overreach. Interface-first: upgrade to container / microVM without changing callers.'),
  },
])
</script>

<template>
  <div class="oryx-page">

    <!-- ── HERO ── -->
    <section class="oryx-hero">
      <div class="oryx-hero-inner">
        <div class="oryx-badge">
          <span class="oryx-badge-dot"></span>
          {{ t('Java 原生 · 私有可审计 · 面向严监管企业', 'Java-native · Private · Auditable · For Regulated Enterprises') }}
        </div>

        <h1 class="oryx-title">
          <span class="oryx-title-name">OryxOS</span>
        </h1>

        <p class="oryx-title-sub">{{ t('面向严监管企业的私有 Agent OS', 'A private Agent OS for regulated enterprises') }}</p>

        <p class="oryx-hero-desc">
          {{ t('OryxOS 是 Java 原生的 Agent 操作系统,部署在企业自己的服务器上。五大核心能力——LLM 对接、ReAct 循环、Memory、Tool、Web Service——开箱即用。数据不出企业,每次调用可审计。', 'OryxOS is a Java-native Agent OS deployed on your own servers. Five core capabilities — LLM integration, ReAct loop, Memory, Tool system, Web Service — work out of the box. Data stays on-prem, every call is auditable.') }}
        </p>

        <div class="oryx-hero-actions">
          <a class="oryx-btn-primary" :href="t('/docs/overview', '/en/docs/overview')">
            {{ t('开始使用', 'Get Started') }} →
          </a>
          <a class="oryx-btn-ghost" :href="t('/docs/concepts', '/en/docs/concepts')">
            {{ t('核心概念', 'Core Concepts') }}
          </a>
          <a class="oryx-btn-ghost" href="https://github.com/CodeMoss24/oryxos" target="_blank" rel="noopener">
            GitHub
          </a>
        </div>

        <div class="oryx-hero-note">
          {{ t('JDK 21 + Spring Boot 3 · 虚拟线程 · SQLite · MCP · 9 个内置 Tool · 12 个 CLI 命令', 'JDK 21 + Spring Boot 3 · Virtual Threads · SQLite · MCP · 9 Built-in Tools · 12 CLI Commands') }}
        </div>
      </div>
    </section>

    <!-- ── PROBLEM ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-problem">
          <div class="oryx-problem-text">
            <h2 class="oryx-section-title">{{ t('两个核心问题', 'Two Foundational Problems') }}</h2>
            <p>{{ t('严监管企业想用 AI Agent,但面临两个根本障碍。', 'Regulated enterprises want AI Agents, but face two fundamental barriers.') }}</p>
            <p class="oryx-problem-item">
              <strong>{{ t('① 数据不能出企业,如何跑 Agent?', '① Data cannot leave — how to run Agents?') }}</strong>
              {{ t('公有云 Agent 平台不可用,必须私有部署,但自建基础设施成本极高。', 'Public cloud Agent platforms are off-limits. Private deployment is required, but building infrastructure from scratch is prohibitively expensive.') }}
            </p>
            <p class="oryx-problem-item">
              <strong>{{ t('② 每次调用必须可审计,如何保证?', '② Every call must be auditable — how?') }}</strong>
              {{ t('金融、医疗、政务行业要求完整的操作追溯,传统 Agent 框架不内置审计。', 'Finance, healthcare, and government require complete operational traceability. Traditional Agent frameworks lack built-in audit.') }}
            </p>
            <p class="oryx-solution-line">{{ t('OryxOS 专门解决这两个问题,让企业专注于 Agent 业务逻辑,而不是基础设施和合规。', 'OryxOS solves exactly these two problems, so enterprises focus on Agent logic, not infrastructure and compliance.') }}</p>
          </div>
          <div class="oryx-problem-compare">
            <div class="oryx-compare-item oryx-compare-bad">
              <div class="oryx-compare-label">{{ t('今天的做法', 'Today') }}</div>
              <div class="oryx-compare-rows">
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('每个团队自己搭 LLM 接入 + 工具调用 + 记忆', 'Every team builds its own LLM integration + tool calling + memory') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('审计靠事后翻日志,没有结构化记录', 'Audit relies on after-the-fact log mining, no structured records') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('Agent 只能跑在公有云,数据安全无法保证', 'Agents can only run on public cloud, data security not guaranteed') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('多个 Agent 各自为政,无法共享基础设施', 'Multiple Agents operate independently, no shared infrastructure') }}</span>
                </div>
              </div>
            </div>
            <div class="oryx-compare-item oryx-compare-good">
              <div class="oryx-compare-label">OryxOS</div>
              <div class="oryx-compare-rows">
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('五大核心能力开箱即用,不重复造轮子', 'Five core capabilities out of the box, no reinventing the wheel') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('tool_invocations + llm_calls 落库审计', 'tool_invocations + llm_calls persisted for audit') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('Java 原生私有部署,数据不出企业', 'Java-native private deployment, data stays on-prem') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('多 Agent 共享 Provider / Memory / Tool 底座', 'Multi-agent shared Provider / Memory / Tool foundation') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CAPABILITIES ── -->
    <section class="oryx-section oryx-primitives-section">
      <div class="oryx-section-inner oryx-primitives-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('核心能力', 'Core Capabilities') }}</div>
          <h2 class="oryx-section-title">{{ t('ReAct + Memory + Tool 三位一体', 'ReAct + Memory + Tool — unified') }}</h2>
        </div>
        <div class="oryx-primitives">
          <div v-for="p in capabilities" :key="p.title" class="oryx-primitive">
            <div class="oryx-primitive-header">
              <span class="oryx-primitive-icon">{{ p.icon }}</span>
              <div>
                <h3 class="oryx-primitive-title">{{ p.title }}</h3>
                <p class="oryx-primitive-subtitle">{{ p.subtitle }}</p>
              </div>
            </div>
            <pre class="oryx-code"><code>{{ p.code }}</code></pre>
          </div>
        </div>
      </div>
    </section>

    <!-- ── SCENARIOS ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('真实场景', 'Real Scenarios') }}</div>
          <h2 class="oryx-section-title">{{ t('八个真实使用场景', 'Eight real-world use cases') }}</h2>
        </div>
        <div class="oryx-scenarios">
          <div v-for="s in scenarios" :key="s.num" class="oryx-scenario">
            <div class="oryx-scenario-num">{{ s.num }}</div>
            <div>
              <h3 class="oryx-scenario-title">{{ s.title }}</h3>
              <p class="oryx-scenario-desc">{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── INTEGRATION ── -->
    <section class="oryx-section oryx-sdk-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('接入方式', 'Integration') }}</div>
          <h2 class="oryx-section-title">{{ t('三种 Tool 接入,按需选择', 'Three ways to add Tools — pick what fits') }}</h2>
        </div>
        <div class="oryx-sdk-cards">
          <div class="oryx-sdk-card">
            <div class="oryx-sdk-card-icon">📝</div>
            <h3 class="oryx-sdk-card-title">{{ t('零代码 AGENT.md + MCP', 'Zero-code AGENT.md + MCP') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('写一个 AGENT.md 定义 Agent 意图,配置 mcp_servers.yaml 接入企业系统。LLM 自动组合能力,不需要写任何 Java 代码。', 'Write an AGENT.md to define intent, configure mcp_servers.yaml to connect enterprise systems. LLM composes capabilities automatically — no Java code needed.') }}</p>
            <div class="oryx-langs">
              <span v-for="l in ['AGENT.md', 'MCP Server', '零代码']" :key="l" class="oryx-lang">{{ l }}</span>
            </div>
          </div>
          <div class="oryx-sdk-card oryx-sdk-card-featured">
            <div class="oryx-sdk-card-icon">🔌</div>
            <h3 class="oryx-sdk-card-title">{{ t('自写 MCP Server', 'Custom MCP Server') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('用任何语言写 MCP Server,通过标准协议接入 OryxOS。轻量级,适合接入企业自有系统,Node/Python/Go/Java 皆可。', 'Write an MCP Server in any language, connect via standard protocol. Lightweight, ideal for enterprise systems — Node / Python / Go / Java all work.') }}</p>
            <div class="oryx-sdk-installs">
              <code>npx @modelcontextprotocol/create-server</code>
              <code>pip install mcp</code>
              <code>go install github.com/mark3labs/mcp-go</code>
            </div>
          </div>
          <div class="oryx-sdk-card">
            <div class="oryx-sdk-card-icon">☕</div>
            <h3 class="oryx-sdk-card-title">{{ t('Java @Tool 注解插件', 'Java @Tool Annotation Plugin') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('深度集成场景:用 @Tool 注解写 Java Bean,自动注册到 ToolRegistry,类型安全,性能最好。适合需要精细控制的内部工具。', 'Deep integration: annotate Java Beans with @Tool, auto-registered in ToolRegistry. Type-safe, best performance. For internal tools requiring fine control.') }}</p>
            <div class="oryx-sdk-badges">
              <span class="oryx-sdk-badge">@Tool</span>
              <span class="oryx-sdk-badge">ToolRegistry</span>
              <span class="oryx-sdk-badge">Sandbox</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── API & CLI ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('接口总览', 'API & CLI') }}</div>
          <h2 class="oryx-section-title">{{ t('REST API + CLI 双通道', 'REST API + CLI dual channel') }}</h2>
          <p class="oryx-section-desc">{{ t('核心阶段 10 个 REST 端点 + 12 个 CLI 命令,覆盖会话管理、Agent 调用、Profile 配置和系统运维。', '10 REST endpoints + 12 CLI commands in core phase — covering session management, Agent invocation, profile configuration, and system operations.') }}</p>
        </div>
        <div class="oryx-proto-grid">
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">{{ t('REST API', 'REST API') }}</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">POST /api/v1/sessions</code>
              <span class="oryx-proto-desc">{{ t('创建会话', 'Create session') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">POST /api/v1/sessions/{id}/messages</code>
              <span class="oryx-proto-desc">{{ t('发送消息', 'Send message') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">POST /api/v1/agents/{name}/invoke</code>
              <span class="oryx-proto-desc">{{ t('无状态调用 Agent', 'Stateless Agent invocation') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">GET /api/v1/profiles</code>
              <span class="oryx-proto-desc">{{ t('列出 Profile', 'List profiles') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">GET /api/v1/tools</code>
              <span class="oryx-proto-desc">{{ t('列出已注册 Tool', 'List registered tools') }}</span>
            </div>
          </div>
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">{{ t('CLI 命令', 'CLI Commands') }}</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos chat [--profile]</code>
              <span class="oryx-proto-desc">{{ t('交互式对话', 'Interactive chat') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos serve</code>
              <span class="oryx-proto-desc">{{ t('启动 HTTP API 服务', 'Start HTTP API server') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos gateway</code>
              <span class="oryx-proto-desc">{{ t('多渠道守护进程', 'Multi-channel daemon') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos profile create/list/show</code>
              <span class="oryx-proto-desc">{{ t('Profile 管理', 'Profile management') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos provider/tool/session list</code>
              <span class="oryx-proto-desc">{{ t('查询资源', 'Query resources') }}</span>
            </div>
          </div>
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">{{ t('审计数据表', 'Audit Tables') }}</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">tool_invocations</code>
              <span class="oryx-proto-desc">{{ t('每次 Tool 调用的输入、输出、耗时、成功/失败', 'Input, output, duration, success/failure per Tool call') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">llm_calls</code>
              <span class="oryx-proto-desc">{{ t('每次 LLM 调用的 Provider、Token 用量、耗时', 'Provider, token usage, duration per LLM call') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">sessions</code>
              <span class="oryx-proto-desc">{{ t('会话元数据与完整对话历史', 'Session metadata and full conversation history') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">scheduled_tasks / task_executions</code>
              <span class="oryx-proto-desc">{{ t('定时任务登记与执行记录', 'Scheduled task registry and execution history') }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CTA ── -->
    <section class="oryx-section oryx-cta-section">
      <div class="oryx-section-inner">
        <div class="oryx-cta">
          <h2 class="oryx-cta-title">{{ t('开始构建', 'Start Building') }}</h2>
          <p class="oryx-cta-desc">{{ t('初始化工作区,配置 LLM Provider,开始第一个 Agent——5 分钟跑通。', 'Initialize workspace, configure LLM Provider, start your first Agent — running in 5 minutes.') }}</p>
          <pre class="oryx-code oryx-cta-code"><code># Initialize workspace
oryxos init

# Configure provider in .oryxos/config.yaml
# provider:
#   name: openai
#   api-key: sk-xxx
#   model: gpt-4o

# Start chatting
oryxos chat

# Or start HTTP API server
oryxos serve --port 8080

# Create a new Agent (zero code)
oryxos profile create daily-weather
# Edit .oryxos/agents/daily-weather/AGENT.md
# Define intent → ReAct + Tools handle the rest</code></pre>
          <div class="oryx-cta-links">
            <a class="oryx-btn-primary" :href="t('/docs/quick-start', '/en/docs/quick-start')">{{ t('快速开始', 'Quick Start') }}</a>
            <a class="oryx-btn-ghost" href="https://github.com/CodeMoss24/oryxos" target="_blank" rel="noopener">GitHub</a>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.oryx-page {
  min-height: 100vh;
  background: #ffffff;
  color: #000000;
  font-family: inherit;
}

/* ── Hero ── */
.oryx-hero {
  position: relative;
  padding: 100px 24px 80px;
  text-align: center;
  overflow: hidden;
}
.oryx-hero-inner {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.oryx-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  color: #555555;
  font-size: 12px;
  margin-bottom: 28px;
}
.oryx-badge-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #000000;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%,100% { opacity:1; transform:scale(1); }
  50% { opacity:0.4; transform:scale(1.4); }
}
.oryx-title {
  margin: 0 0 12px;
  line-height: 1;
}
.oryx-title-name {
  font-size: clamp(72px, 14vw, 120px);
  font-weight: 900;
  letter-spacing: -0.03em;
  color: #000000;
}
.oryx-title-sub {
  font-size: 18px;
  color: #666666;
  margin: 0 0 20px;
}
.oryx-hero-desc {
  font-size: 16px;
  line-height: 1.7;
  color: #444444;
  max-width: 600px;
  margin: 0 0 32px;
}
.oryx-hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 20px;
}
.oryx-btn-primary {
  padding: 11px 28px;
  border-radius: 8px;
  background: #000000;
  color: #ffffff;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: opacity 0.2s, transform 0.15s;
}
.oryx-btn-primary:hover { opacity: 0.75; transform: translateY(-1px); }
.oryx-btn-ghost {
  padding: 11px 28px;
  border-radius: 8px;
  border: 1px solid #d4d4d4;
  color: #333333;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: border-color 0.2s, background 0.2s;
}
.oryx-btn-ghost:hover { border-color: #000000; background: #f5f5f5; }
.oryx-hero-note {
  font-size: 12px;
  color: #999999;
}

/* ── Section ── */
.oryx-section { padding: 72px 24px; }
.oryx-section-inner { max-width: 1000px; margin: 0 auto; }
.oryx-primitives-inner { max-width: 1400px; }
.oryx-section-header { text-align: center; margin-bottom: 48px; }
.oryx-section-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555555;
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  margin-bottom: 14px;
}
.oryx-section-title {
  font-size: clamp(22px, 4vw, 32px);
  font-weight: 700;
  color: #000000;
  margin: 0 0 12px;
}
.oryx-section-desc {
  font-size: 15px;
  color: #666666;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

/* ── Problem ── */
.oryx-problem {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: start;
}
.oryx-problem-text p { color: #666666; line-height: 1.7; margin: 0 0 14px; font-size: 15px; }
.oryx-problem-item strong { color: #000000; display: block; margin-bottom: 4px; }
.oryx-solution-line { color: #000000 !important; font-weight: 600; }
.oryx-problem-compare { display: flex; flex-direction: column; gap: 16px; }
.oryx-compare-item {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
}
.oryx-compare-bad { background: #fafafa; }
.oryx-compare-good { background: #f5f5f5; border-color: #d4d4d4; }
.oryx-compare-label { font-size: 11px; font-weight: 700; color: #999999; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.08em; }
.oryx-compare-rows { display: flex; flex-direction: column; gap: 8px; }
.oryx-compare-row { display: flex; align-items: flex-start; gap: 10px; font-size: 13px; color: #555555; line-height: 1.5; }
.oryx-compare-icon { flex-shrink: 0; font-style: normal; color: #bbbbbb; font-weight: 700; width: 14px; }
.oryx-icon-ok { color: #000000; }

/* ── Primitives ── */
.oryx-primitives-section { background: #f5f5f5; }
.oryx-primitives { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); grid-auto-rows: 1fr; gap: 16px; }
.oryx-primitive {
  padding: 20px;
  border-radius: 14px;
  border: 1px solid #e5e5e5;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
  min-width: 0;
  overflow: hidden;
}
.oryx-primitive .oryx-code { flex: 1; }
.oryx-primitive:hover { border-color: #000000; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.oryx-primitive-header { display: flex; align-items: flex-start; gap: 12px; }
.oryx-primitive-icon { font-size: 28px; flex-shrink: 0; }
.oryx-primitive-title { font-size: 17px; font-weight: 700; color: #000000; margin: 0 0 2px; }
.oryx-primitive-subtitle { font-size: 12px; color: #999999; margin: 0; }
.oryx-code {
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: #333333;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
}
.oryx-code code { font-family: 'JetBrains Mono', 'Fira Code', monospace; background: none; color: inherit; }

/* ── Scenarios ── */
.oryx-scenarios { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
.oryx-scenario {
  display: flex;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
  background: #fafafa;
}
.oryx-scenario-num {
  font-size: 28px;
  font-weight: 900;
  color: #e5e5e5;
  line-height: 1;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.oryx-scenario-title { font-size: 15px; font-weight: 600; color: #000000; margin: 0 0 6px; }
.oryx-scenario-desc { font-size: 13px; color: #666666; line-height: 1.6; margin: 0; }

/* ── SDK ── */
.oryx-sdk-section { background: #f5f5f5; }
.oryx-sdk-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.oryx-sdk-card {
  background: #ffffff;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.oryx-sdk-card-featured { border-color: #000000; }
.oryx-sdk-card-icon { font-size: 28px; }
.oryx-sdk-card-title { font-size: 17px; font-weight: 700; color: #000000; margin: 0; }
.oryx-sdk-card-desc { font-size: 14px; color: #666666; line-height: 1.6; margin: 0; flex: 1; }
.oryx-langs { display: flex; flex-wrap: wrap; gap: 8px; }
.oryx-lang {
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  color: #333333;
  font-size: 12px;
  font-weight: 600;
}
.oryx-sdk-installs { display: flex; flex-direction: column; gap: 6px; }
.oryx-sdk-installs code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  padding: 5px 10px;
  color: #000000;
  display: block;
}
.oryx-sdk-badges { display: flex; flex-wrap: wrap; gap: 8px; }
.oryx-sdk-badge {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 700;
  background: #f0f0f0;
  border: 1px solid #d4d4d4;
  color: #333333;
}

/* ── Protocol grid ── */
.oryx-proto-grid { display: flex; flex-direction: column; gap: 28px; }
.oryx-proto-group { display: flex; flex-direction: column; gap: 6px; }
.oryx-proto-group-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555555;
  margin-bottom: 4px;
}
.oryx-proto-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #e5e5e5;
  flex-wrap: wrap;
}
.oryx-proto-subject {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #000000;
  background: #f0f0f0;
  border: 1px solid #d4d4d4;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  white-space: nowrap;
}
.oryx-proto-desc { font-size: 13px; color: #666666; flex: 1; }

/* ── CTA ── */
.oryx-cta-section { background: #f5f5f5; }
.oryx-cta { text-align: center; max-width: 680px; margin: 0 auto; }
.oryx-cta-title { font-size: 28px; font-weight: 700; color: #000000; margin: 0 0 12px; }
.oryx-cta-desc { font-size: 15px; color: #666666; margin: 0 0 24px; }
.oryx-cta-code { text-align: left; margin-bottom: 28px; }
.oryx-cta-links { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

/* ── Responsive ── */
@media (max-width: 900px) {
  .oryx-sdk-cards { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .oryx-hero { padding: 72px 20px 60px; }
  .oryx-problem { grid-template-columns: 1fr; }
  .oryx-primitives { grid-template-columns: 1fr; }
  .oryx-scenarios { grid-template-columns: 1fr; }
  .oryx-section { padding: 48px 20px; }
}
</style>
