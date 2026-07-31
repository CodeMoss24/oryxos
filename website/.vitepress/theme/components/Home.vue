<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isEn = computed(() => lang.value === 'en-US')
const t = (zh, en) => isEn.value ? en : zh

const capabilities = computed(() => [
  {
    icon: '⟳',
    title: t('ReAct 循环引擎', 'ReAct Loop Engine'),
    desc: t('自实现的 Reason+Act 循环，约数十行 Java，最大 10 轮迭代。虚拟线程高并发，机制完全可控。', 'Custom Reason+Act loop, ~10 lines of Java, max 10 iterations. Virtual threads for concurrency. Full control over execution.'),
    code: `while (iterations < maxIterations) {
  response = llm.call(prompt + history + tools)
  if (!response.hasToolCall) return response
  result = toolExecutor.execute(response.toolCall)
  history.append(toolMessage(result))
}`,
  },
  {
    icon: '▣',
    title: t('三层 Memory', 'Three-tier Memory'),
    desc: t('MemoryService 统一门面。核心记忆全量注入永不截断，归档记忆关键词检索。三档后端可切换。', 'Unified MemoryService facade. Core memory full-injection never truncated. Archival memory with keyword search. Three backends.'),
    code: `save_memory("企业合规:所有调用必须审计", CORE)
save_memory("用户偏好:输出用中文", ARCHIVAL)

memory.backend = markdown   // default
memory.backend = sqlite     // embedded
memory.backend = mem0       // cloud`,
  },
  {
    icon: '⌘',
    title: t('统一 Tool 体系', 'Unified Tool System'),
    desc: t('9 个内置 Tool + MCP 零代码接入 + @Tool Java 插件。OryxTool 抽象统一接口，Sandbox 三层白名单。', '9 built-in tools + zero-code MCP + @Tool Java plugins. Unified OryxTool interface. Three-layer Sandbox whitelist.'),
    code: `FileTools   → read_file / write_file / list_dir
ShellTools  → shell
HttpTools   → http_get / http_post
MemoryTools → save_memory / recall_memory
NotifyTools → notify`,
  },
])

</script>

<template>
  <div class="page">

    <!-- ════════════ HERO ════════════ -->
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          {{ t('Agent Harness OS · Java 原生 · 私有可审计', 'Agent Harness OS · Java-native · Private · Auditable') }}
        </div>

        <h1 class="hero-title">
          <span class="hero-title-main">OryxOS</span>
        </h1>

        <p class="hero-subtitle">
          {{ t('面向严监管企业的私有 Agent 操作系统', 'A Private Agent OS for Regulated Enterprises') }}
        </p>

        <p class="hero-desc">
          {{ t('OryxOS 是开源的 Agent Harness OS——套在模型外面、把模型变成能干活的 Agent 的那层脚手架。北极星公式：自然语言 + Memory + Tool + MCP + Skill + 知识库 + Notify = 一个 Agent。一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。', 'OryxOS is an open-source Agent Harness OS — the scaffolding around models that turns them into capable Agents. North Star formula: natural language + Memory + Tool + MCP + Skill + Knowledge Base + Notify = one Agent. One directory defines one Agent, one foundation runs a fleet of Agents, private deployment, data stays on-prem.') }}
        </p>

        <div class="hero-actions">
          <a class="btn btn-primary" :href="t('/docs/overview', '/en/docs/overview')">
            {{ t('开始使用', 'Get Started') }}
            <span class="btn-arrow">→</span>
          </a>
          <a class="btn btn-ghost" :href="t('/docs/concepts', '/en/docs/concepts')">
            {{ t('核心概念', 'Core Concepts') }}
          </a>
          <a class="btn btn-ghost" href="https://github.com/CodeMoss24/oryxos" target="_blank" rel="noopener">
            GitHub
          </a>
        </div>

        <p class="hero-tech">
          JDK 21 · Spring Boot 3 · Virtual Threads · SQLite · MCP · A2A · 9 Tools · 12 CLI Commands
        </p>
      </div>

      <!-- Decorative bottom gradient -->
      <div class="hero-gradient"></div>
    </section>

    <!-- ════════════ PROBLEM / SOLUTION ════════════ -->
    <section class="section">
      <div class="section-inner">
        <div class="problem-grid">
          <div class="problem-text">
            <div class="section-tag">{{ t('WHY ORYXOS', 'WHY ORYXOS') }}</div>
            <h2 class="section-title">{{ t('两个核心问题，一个答案', 'Two core problems, one answer') }}</h2>
            <p class="section-desc">{{ t('严监管企业想用 AI Agent，但面临两个根本障碍。OryxOS 专门解决这两个问题，让企业专注于 Agent 业务逻辑，而不是基础设施和合规。', 'Regulated enterprises want AI Agents but face two fundamental barriers. OryxOS solves exactly these two problems.') }}</p>

            <div class="problem-points">
              <div class="problem-point">
                <div class="point-num">01</div>
                <div>
                  <strong>{{ t('数据不能出企业', 'Data cannot leave the enterprise') }}</strong>
                  <p>{{ t('公有云 Agent 平台不可用，必须私有部署。OryxOS 装在你自己的 K8s 或物理服务器上，SQLite 存储，数据完全不出企业。', 'Public cloud Agent platforms are off-limits. OryxOS runs on your own K8s or bare metal with SQLite storage.') }}</p>
                </div>
              </div>
              <div class="problem-point">
                <div class="point-num">02</div>
                <div>
                  <strong>{{ t('每次调用必须可审计', 'Every call must be auditable') }}</strong>
                  <p>{{ t('金融、医疗、政务行业要求完整操作追溯。tool_invocations + llm_calls 从 Day One 写入 SQLite，每次调用全程有据可查。', 'Finance, healthcare, government require full traceability. tool_invocations + llm_calls written to SQLite from day one.') }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="problem-compare">
            <div class="compare-card compare-bad">
              <div class="compare-label">{{ t('今天的做法', 'TODAY') }}</div>
              <div class="compare-row"><span class="compare-icon bad">✕</span>{{ t('每个团队自己搭 LLM + Tool + Memory', 'Each team builds its own LLM + Tool + Memory') }}</div>
              <div class="compare-row"><span class="compare-icon bad">✕</span>{{ t('审计靠事后翻日志', 'Audit relies on post-hoc log mining') }}</div>
              <div class="compare-row"><span class="compare-icon bad">✕</span>{{ t('Agent 只能跑在公有云', 'Agents can only run on public cloud') }}</div>
              <div class="compare-row"><span class="compare-icon bad">✕</span>{{ t('多 Agent 各自为政', 'Multiple agents operate in silos') }}</div>
            </div>
            <div class="compare-card compare-good">
              <div class="compare-label">ORYXOS</div>
              <div class="compare-row"><span class="compare-icon good">✓</span>{{ t('五大核心能力开箱即用', 'Five core capabilities out of the box') }}</div>
              <div class="compare-row"><span class="compare-icon good">✓</span>{{ t('tool_invocations + llm_calls 结构化落库', 'Structured audit tables from day one') }}</div>
              <div class="compare-row"><span class="compare-icon good">✓</span>{{ t('Java 原生私有部署', 'Java-native private deployment') }}</div>
              <div class="compare-row"><span class="compare-icon good">✓</span>{{ t('多 Agent 共享统一底座', 'Multi-agent shared foundation') }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ AGENT HARNESS OS ════════════ -->
    <section class="section">
      <div class="section-inner">
        <div class="section-header">
          <div class="section-tag">{{ t('AGENT HARNESS OS', 'AGENT HARNESS OS') }}</div>
          <h2 class="section-title">{{ t('Runtime 让一个 Agent 跑起来，Harness OS 让一群 Agent 被管理起来', 'Runtime makes one Agent run; Harness OS manages a fleet of Agents') }}</h2>
        </div>

        <div class="harness-grid">
          <div class="harness-card">
            <div class="harness-label">{{ t('Agent Runtime', 'Agent Runtime') }}</div>
            <div class="harness-desc">{{ t('让单个 Agent 跑起来的执行内核：调用模型、执行工具、管理上下文、控制推理循环。', 'The execution kernel for a single Agent: calling models, executing tools, managing context, controlling the reasoning loop.') }}</div>
          </div>
          <div class="harness-arrow">→</div>
          <div class="harness-card harness-card-active">
            <div class="harness-label">{{ t('Agent Harness OS', 'Agent Harness OS') }}</div>
            <div class="harness-desc">{{ t('管理的是一群 Agent：多 Agent 生命周期、统一渠道与接入、统一记忆、多租户与治理、跨节点协作。OryxOS 是后者。', 'Manages a fleet of Agents: lifecycle management, unified channels, shared memory, multi-tenancy, governance, cross-node collaboration. OryxOS is the latter.') }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ CAPABILITIES ════════════ -->
    <section class="section section-alt">
      <div class="section-inner">
        <div class="section-header">
          <div class="section-tag">{{ t('CORE CAPABILITIES', 'CORE CAPABILITIES') }}</div>
          <h2 class="section-title">{{ t('ReAct + Memory + Tool 三位一体', 'ReAct + Memory + Tool — Unified') }}</h2>
        </div>

        <div class="caps-grid">
          <div v-for="c in capabilities" :key="c.title" class="cap-card">
            <div class="cap-header">
              <span class="cap-icon">{{ c.icon }}</span>
              <div>
                <h3 class="cap-title">{{ c.title }}</h3>
                <p class="cap-desc">{{ c.desc }}</p>
              </div>
            </div>
            <pre class="cap-code"><code>{{ c.code }}</code></pre>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ ROADMAP ════════════ -->
    <section class="section">
      <div class="section-inner">
        <div class="section-header">
          <div class="section-tag">{{ t('ROADMAP', 'ROADMAP') }}</div>
          <h2 class="section-title">{{ t('慢就是快，克制且聚焦', 'Slow is fast, restrained and focused') }}</h2>
          <p class="section-desc" style="margin: 0 auto; text-align: center;">{{ t('先把单机运行时内核做扎实，再逐步生长分布式能力。每一步都走扎实，不一开始就堆无法落地的大型架构。', 'Build a solid single-machine runtime kernel first, then grow distributed capabilities incrementally.') }}</p>
        </div>

        <div class="roadmap-grid">
          <div class="roadmap-phase">
            <div class="phase-badge phase-current">{{ t('当前', 'CURRENT') }}</div>
            <h3>{{ t('阶段一：单机运行时内核', 'Phase 1: Single-Machine Runtime Kernel') }}</h3>
            <p>{{ t('五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API 接入、对接 MCP。把单节点运行和管理一群 Agent 做到可用。', 'Five core capabilities: config-as-Agent, multi-agent coexistence, REST API, MCP. Make single-node Agent management genuinely usable.') }}</p>
          </div>
          <div class="roadmap-phase">
            <div class="phase-badge">{{ t('规划', 'PLANNED') }}</div>
            <h3>{{ t('阶段二：底座分布式', 'Phase 2: Distributed Foundation') }}</h3>
            <p>{{ t('节点无状态化、状态外置、多副本部署，支撑更大规模与高可用。', 'Node statelessness, external state, multi-replica deployment for scale and high availability.') }}</p>
          </div>
          <div class="roadmap-phase">
            <div class="phase-badge">{{ t('愿景', 'VISION') }}</div>
            <h3>{{ t('阶段三：跨节点 Agent 协作', 'Phase 3: Cross-Node Agent Collaboration') }}</h3>
            <p>{{ t('引入 Agent 通信底座，对接 A2A，让多节点上的 Agent 跨节点发现、委托、可靠异步协同。', 'Agent communication infrastructure with A2A: cross-node discovery, delegation, reliable async collaboration.') }}</p>
          </div>
        </div>

        <div class="roadmap-horizontal">
          <div class="roadmap-horizontal-label">{{ t('横向能力（伴随各阶段逐步补齐）', 'Horizontal Capabilities (progressively added)') }}</div>
          <div class="roadmap-horizontal-tags">
            <span>{{ t('多租户', 'Multi-tenancy') }}</span>
            <span>SSO</span>
            <span>{{ t('完整审计', 'Full Audit') }}</span>
            <span>{{ t('工具策略', 'Tool Policy') }}</span>
            <span>{{ t('可观测', 'Observability') }}</span>
            <span>{{ t('Web 管理', 'Web Admin') }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ INTEGRATION ════════════ -->
    <section class="section section-alt">
      <div class="section-inner">
        <div class="section-header">
          <div class="section-tag">{{ t('INTEGRATION', 'INTEGRATION') }}</div>
          <h2 class="section-title">{{ t('三种 Tool 接入，按需选择', 'Three ways to add Tools') }}</h2>
        </div>

        <div class="integration-grid">
          <div class="integration-card">
            <div class="int-card-icon">📝</div>
            <h3>{{ t('零代码 AGENT.md + MCP', 'Zero-code AGENT.md + MCP') }}</h3>
            <p>{{ t('写一个 AGENT.md 定义 Agent 意图，配置 mcp_servers.yaml 接入企业系统。LLM 自动组合能力，不写任何 Java 代码。', 'Write an AGENT.md to define intent, configure mcp_servers.yaml. LLM composes capabilities automatically — no Java code needed.') }}</p>
            <div class="int-tags">
              <span>AGENT.md</span><span>MCP Server</span><span>{{ t('零代码', 'Zero Code') }}</span>
            </div>
          </div>
          <div class="integration-card featured">
            <div class="int-card-icon">🔌</div>
            <h3>{{ t('自写 MCP Server', 'Custom MCP Server') }}</h3>
            <p>{{ t('用任何语言写 MCP Server，通过标准协议接入 OryxOS。轻量级，适合接入企业自有系统。', 'Write an MCP Server in any language, connect via standard protocol. Lightweight, ideal for enterprise systems.') }}</p>
            <div class="int-cmds">
              <code>npx @modelcontextprotocol/create-server</code>
              <code>pip install mcp</code>
              <code>go install github.com/mark3labs/mcp-go</code>
            </div>
          </div>
          <div class="integration-card">
            <div class="int-card-icon">☕</div>
            <h3>{{ t('Java @Tool 注解插件', 'Java @Tool Annotation') }}</h3>
            <p>{{ t('深度集成场景：用 @Tool 注解写 Java Bean，自动注册到 ToolRegistry。类型安全，性能最好。', 'Deep integration: annotate Java Beans with @Tool, auto-registered in ToolRegistry. Type-safe, best performance.') }}</p>
            <div class="int-tags">
              <span>@Tool</span><span>ToolRegistry</span><span>Sandbox</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ API & CLI ════════════ -->
    <section class="section">
      <div class="section-inner">
        <div class="section-header">
          <div class="section-tag">{{ t('API & CLI', 'API & CLI') }}</div>
          <h2 class="section-title">{{ t('REST API + CLI 双通道', 'REST API + CLI dual channel') }}</h2>
        </div>

        <div class="api-grid">
          <div class="api-group">
            <div class="api-group-label">REST API</div>
            <div class="api-row"><code>POST /api/v1/sessions</code><span>{{ t('创建会话', 'Create session') }}</span></div>
            <div class="api-row"><code>POST /api/v1/sessions/{id}/messages</code><span>{{ t('发送消息', 'Send message') }}</span></div>
            <div class="api-row"><code>POST /api/v1/agents/{name}/invoke</code><span>{{ t('无状态调用 Agent', 'Stateless Agent invoke') }}</span></div>
            <div class="api-row"><code>GET /api/v1/profiles</code><span>{{ t('列出 Profile', 'List profiles') }}</span></div>
            <div class="api-row"><code>GET /api/v1/tools</code><span>{{ t('列出已注册 Tool', 'List registered tools') }}</span></div>
          </div>
          <div class="api-group">
            <div class="api-group-label">CLI</div>
            <div class="api-row"><code>oryxos chat [--profile]</code><span>{{ t('交互式对话', 'Interactive chat') }}</span></div>
            <div class="api-row"><code>oryxos serve</code><span>{{ t('启动 HTTP API 服务', 'Start HTTP API server') }}</span></div>
            <div class="api-row"><code>oryxos gateway</code><span>{{ t('多渠道守护进程', 'Multi-channel daemon') }}</span></div>
            <div class="api-row"><code>oryxos profile create/list/show</code><span>{{ t('Profile 管理', 'Profile management') }}</span></div>
            <div class="api-row"><code>oryxos provider/tool/session list</code><span>{{ t('查询资源', 'Query resources') }}</span></div>
          </div>
          <div class="api-group">
            <div class="api-group-label">{{ t('审计数据表', 'AUDIT TABLES') }}</div>
            <div class="api-row"><code>tool_invocations</code><span>{{ t('Tool 调用输入/输出/耗时/成败', 'Tool call input/output/duration/status') }}</span></div>
            <div class="api-row"><code>llm_calls</code><span>{{ t('LLM 调用 Provider/Token/耗时', 'LLM call provider/tokens/duration') }}</span></div>
            <div class="api-row"><code>sessions</code><span>{{ t('会话元数据 + 完整对话历史', 'Session metadata + full history') }}</span></div>
            <div class="api-row"><code>scheduled_tasks</code><span>{{ t('定时任务登记 + 执行历史', 'Scheduled task registry + history') }}</span></div>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ CTA ════════════ -->
    <section class="section section-alt">
      <div class="section-inner">
        <div class="cta">
          <h2 class="cta-title">{{ t('开始构建你的第一个 Agent', 'Build your first Agent') }}</h2>
          <p class="cta-desc">{{ t('初始化工作区，配置 LLM Provider，5 分钟跑通第一个 Agent。', 'Initialize workspace, configure LLM Provider, run your first Agent in 5 minutes.') }}</p>

          <div class="cta-terminal">
            <div class="cta-terminal-bar">
              <span class="cta-dot cta-dot-red"></span>
              <span class="cta-dot cta-dot-yellow"></span>
              <span class="cta-dot cta-dot-green"></span>
              <span class="cta-terminal-title">terminal — oryxos</span>
            </div>
            <pre class="cta-terminal-body"><code><span class="cmd-prompt">$</span> oryxos init

<span class="cmd-prompt">$</span> <span class="cmd-comment"># Configure provider in application.yaml</span>
<span class="cmd-prompt">$</span> <span class="cmd-comment"># provider: deepseek / model: deepseek-chat</span>

<span class="cmd-prompt">$</span> oryxos chat
<span class="cmd-output">OryxOS ready. Type your message.</span>

<span class="cmd-prompt">$</span> oryxos profile create daily-weather
<span class="cmd-output">Agent "daily-weather" created.</span>

<span class="cmd-prompt">$</span> oryxos serve --port 8080
<span class="cmd-output">HTTP API server started on :8080</span></code></pre>
          </div>

          <div class="cta-links">
            <a class="btn btn-primary" :href="t('/docs/quick-start', '/en/docs/quick-start')">
              {{ t('快速开始', 'Quick Start') }}
              <span class="btn-arrow">→</span>
            </a>
            <a class="btn btn-ghost" href="https://github.com/CodeMoss24/oryxos" target="_blank" rel="noopener">GitHub</a>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ FOOTER ════════════ -->
    <footer class="site-footer">
      <div class="footer-inner">
        <p>OryxOS · {{ t('Agent Harness OS · Java 原生 · 私有可审计', 'Agent Harness OS · Java-native · Private & Auditable') }}</p>
      </div>
    </footer>

  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   OryxOS Homepage — Dark Theme
   ═══════════════════════════════════════════════════════════════ */

.page {
  min-height: 100vh;
  background: #000000;
  color: #eee;
  font-family: inherit;
}

/* ─── Hero ─── */
.hero {
  position: relative;
  padding: 120px 24px 100px;
  text-align: center;
  overflow: hidden;
}
.hero-inner {
  position: relative;
  max-width: 780px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
}
.hero-gradient {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(to top, #000, transparent);
  z-index: 0;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 16px;
  border-radius: 20px;
  border: 1px solid #222;
  background: #080808;
  color: #777;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
  margin-bottom: 32px;
}
.badge-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #FF6B2B;
  animation: pulse 2.5s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.3; transform: scale(1.6); }
}
.hero-title {
  margin: 0 0 16px;
  line-height: 1;
}
.hero-title-main {
  font-family: 'Space Grotesk', sans-serif;
  font-size: clamp(80px, 16vw, 140px);
  font-weight: 700;
  letter-spacing: -0.04em;
  color: #fff;
}
.hero-subtitle {
  font-size: 18px;
  color: #FF8C42;
  margin: 0 0 24px;
  font-weight: 500;
}
.hero-desc {
  font-size: 15px;
  line-height: 1.75;
  color: #777;
  max-width: 600px;
  margin: 0 0 36px;
}
.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 24px;
}
.hero-tech {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #444;
  letter-spacing: 0.03em;
}

/* ─── Buttons ─── */
.btn {
  padding: 11px 28px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.2s;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-primary {
  background: #FF6B2B;
  color: #000;
  border: none;
}
.btn-primary:hover { background: #FF8C42; transform: translateY(-1px); }
.btn-arrow { font-size: 16px; }
.btn-ghost {
  border: 1px solid #222;
  color: #999;
  background: transparent;
}
.btn-ghost:hover { border-color: #FF6B2B; color: #FF8C42; background: #0a0a0a; }

/* ─── Section ─── */
.section { padding: 88px 24px; }
.section-alt { background: #030303; }
.section-inner { max-width: 1100px; margin: 0 auto; }
.section-header { text-align: center; margin-bottom: 56px; }
.section-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.15em;
  color: #FF8C42;
  margin-bottom: 16px;
}
.section-title {
  font-size: clamp(24px, 4vw, 34px);
  font-weight: 700;
  color: #fff;
  margin: 0 0 12px;
  letter-spacing: -0.02em;
}
.section-desc {
  font-size: 15px;
  color: #777;
  line-height: 1.7;
  margin: 0;
  max-width: 600px;
}

/* ─── Problem / Solution ─── */
.problem-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 56px;
  align-items: start;
}
.problem-text .section-desc { margin-bottom: 32px; }
.problem-points { display: flex; flex-direction: column; gap: 24px; }
.problem-point {
  display: flex;
  gap: 16px;
}
.point-num {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 32px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1;
  flex-shrink: 0;
}
.problem-point strong {
  display: block;
  color: #eee;
  font-size: 15px;
  margin-bottom: 4px;
}
.problem-point p {
  color: #777;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.problem-compare { display: flex; flex-direction: column; gap: 14px; }
.compare-card {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #1a1a1a;
  background: #080808;
}
.compare-bad { border-color: #1a1a1a; }
.compare-good { border-color: #FF6B2B33; background: #080804; }
.compare-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #555;
  margin-bottom: 14px;
}
.compare-good .compare-label { color: #FF8C42; }
.compare-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: #888;
  line-height: 1.5;
  margin-bottom: 8px;
}
.compare-row:last-child { margin-bottom: 0; }
.compare-icon { flex-shrink: 0; font-style: normal; font-weight: 700; width: 14px; }
.compare-icon.bad { color: #333; }
.compare-icon.good { color: #FF6B2B; }

/* ─── Capabilities ─── */
.caps-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.cap-card {
  padding: 24px;
  border-radius: 14px;
  border: 1px solid #1a1a1a;
  background: #080808;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: border-color 0.25s, box-shadow 0.25s;
}
.cap-card:hover {
  border-color: #FF6B2B55;
  box-shadow: 0 0 30px rgba(255, 107, 43, 0.06);
}
.cap-header { display: flex; align-items: flex-start; gap: 14px; }
.cap-icon {
  font-size: 26px;
  flex-shrink: 0;
  color: #FF8C42;
  font-weight: 700;
}
.cap-title { font-size: 16px; font-weight: 700; color: #eee; margin: 0 0 4px; }
.cap-desc { font-size: 12px; color: #777; margin: 0; line-height: 1.55; }
.cap-code {
  background: #000;
  border: 1px solid #161616;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 11px;
  line-height: 1.65;
  color: #999;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
  flex: 1;
}
.cap-code code { font-family: 'JetBrains Mono', monospace; background: none; color: inherit; }

/* ─── Agent Harness OS ─── */
.harness-grid {
  display: grid;
  grid-template-columns: 1fr 60px 1fr;
  gap: 20px;
  align-items: center;
  max-width: 800px;
  margin: 0 auto;
}
.harness-card {
  padding: 28px 24px;
  border-radius: 14px;
  border: 1px solid #1a1a1a;
  background: #080808;
  text-align: center;
}
.harness-card-active {
  border-color: #FF6B2B33;
  background: #080804;
}
.harness-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #555;
  margin-bottom: 12px;
}
.harness-card-active .harness-label { color: #FF8C42; }
.harness-desc {
  font-size: 13px;
  color: #888;
  line-height: 1.6;
}
.harness-arrow {
  text-align: center;
  font-size: 28px;
  color: #333;
  font-weight: 300;
}

/* ─── Roadmap ─── */
.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}
.roadmap-phase {
  padding: 28px 24px;
  border-radius: 14px;
  border: 1px solid #1a1a1a;
  background: #080808;
  transition: border-color 0.2s;
}
.roadmap-phase:hover { border-color: #222; }
.phase-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 20px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: #0d0d0d;
  color: #555;
  margin-bottom: 16px;
  border: 1px solid #1a1a1a;
}
.phase-badge.phase-current {
  color: #FF8C42;
  border-color: #FF6B2B33;
}
.roadmap-phase h3 {
  font-size: 16px;
  font-weight: 700;
  color: #eee;
  margin: 0 0 10px;
}
.roadmap-phase p {
  font-size: 13px;
  color: #888;
  line-height: 1.6;
  margin: 0;
}
.roadmap-horizontal {
  text-align: center;
  padding: 20px 0;
}
.roadmap-horizontal-label {
  font-size: 11px;
  font-weight: 600;
  color: #555;
  margin-bottom: 14px;
}
.roadmap-horizontal-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.roadmap-horizontal-tags span {
  padding: 4px 14px;
  border-radius: 20px;
  border: 1px solid #1a1a1a;
  background: #0d0d0d;
  color: #999;
  font-size: 11px;
  font-weight: 600;
}

/* ─── Integration ─── */
.integration-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.integration-card {
  background: #080808;
  border: 1px solid #1a1a1a;
  border-radius: 14px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: border-color 0.2s;
}
.integration-card:hover { border-color: #222; }
.integration-card.featured { border-color: #FF6B2B33; }
.integration-card.featured:hover { border-color: #FF6B2B66; }
.int-card-icon { font-size: 28px; }
.integration-card h3 { font-size: 16px; font-weight: 700; color: #eee; margin: 0; }
.integration-card p { font-size: 13px; color: #888; line-height: 1.6; margin: 0; flex: 1; }
.int-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.int-tags span {
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #1a1a1a;
  background: #0d0d0d;
  color: #999;
  font-size: 11px;
  font-weight: 600;
}
.int-cmds { display: flex; flex-direction: column; gap: 6px; }
.int-cmds code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  background: #000;
  border: 1px solid #161616;
  border-radius: 6px;
  padding: 6px 12px;
  color: #FF8C42;
  display: block;
}

/* ─── API & CLI ─── */
.api-grid { display: flex; flex-direction: column; gap: 24px; }
.api-group { display: flex; flex-direction: column; gap: 6px; }
.api-group-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #FF8C42;
  margin-bottom: 6px;
}
.api-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 9px 16px;
  border-radius: 8px;
  background: #080808;
  border: 1px solid #141414;
  flex-wrap: wrap;
  transition: border-color 0.15s;
}
.api-row:hover { border-color: #1a1a1a; }
.api-row code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #FF8C42;
  background: #0d0d0d;
  border: 1px solid #1a1a1a;
  padding: 3px 10px;
  border-radius: 4px;
  flex-shrink: 0;
  white-space: nowrap;
}
.api-row span { font-size: 12px; color: #888; flex: 1; }

/* ─── CTA ─── */
.cta { text-align: center; max-width: 720px; margin: 0 auto; }
.cta-title { font-size: 30px; font-weight: 700; color: #fff; margin: 0 0 12px; }
.cta-desc { font-size: 15px; color: #888; margin: 0 0 32px; }
.cta-terminal {
  background: #0a0a0a;
  border: 1px solid #1a1a1a;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 28px;
  text-align: left;
}
.cta-terminal-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #0d0d0d;
  border-bottom: 1px solid #1a1a1a;
}
.cta-dot { width: 10px; height: 10px; border-radius: 50%; }
.cta-dot-red { background: #FF5F57; }
.cta-dot-yellow { background: #FFBD2E; }
.cta-dot-green { background: #28CA42; }
.cta-terminal-title {
  margin-left: 8px;
  font-size: 11px;
  color: #555;
  font-family: 'JetBrains Mono', monospace;
}
.cta-terminal-body {
  padding: 20px 24px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #ccc;
  margin: 0;
  white-space: pre;
  background: #0a0a0a;
}
.cta-terminal-body code {
  font-family: 'JetBrains Mono', monospace;
  background: none;
  color: inherit;
}
.cmd-prompt { color: #FF8C42; font-weight: 600; }
.cmd-comment { color: #555; }
.cmd-output { color: #666; }
.cta-links { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

/* ─── Footer ─── */
.site-footer {
  border-top: 1px solid #0d0d0d;
  padding: 32px 24px;
  text-align: center;
}
.footer-inner p {
  font-size: 12px;
  color: #444;
  margin: 0;
}

/* ─── Responsive ─── */
@media (max-width: 900px) {
  .caps-grid { grid-template-columns: 1fr; }
  .integration-grid { grid-template-columns: 1fr; }
  .roadmap-grid { grid-template-columns: 1fr; }
  .harness-grid { grid-template-columns: 1fr; }
  .harness-arrow { transform: rotate(90deg); }
}
@media (max-width: 768px) {
  .hero { padding: 80px 20px 72px; }
  .hero-title-main { font-size: clamp(56px, 14vw, 80px); }
  .problem-grid { grid-template-columns: 1fr; gap: 40px; }
  .section { padding: 60px 20px; }
}
</style>