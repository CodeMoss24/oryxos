<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import logoMark from './assets/logo-mark.svg'

// ── 导航与页面状态 ─────────────────────────────────────────────
const views = [
  { key: 'overview', label: '总览' },
  { key: 'agents', label: 'Agent' },
  { key: 'sessions', label: '会话列表' },
  { key: 'schedules', label: '定时任务' },
  { key: 'tools', label: 'Tool 列表' },
  { key: 'memory', label: '长期记忆' },
  { key: 'sandbox', label: 'Sandbox 白名单' },
  { key: 'providers', label: 'Provider 列表' },
  { key: 'status', label: '运行状态' },
]
const active = ref('overview')
const navOpen = ref(false) // 窄屏导航收起

// ── 总览页静态预览数据(后续逐步接入 /info、/profiles 等动态数据) ──
const capabilities = [
  { name: '对接 LLM', desc: 'Provider 抽象 + 显式映射,多模型自由切换' },
  { name: 'ReAct 循环', desc: '自实现 Reason + Act 引擎,不依赖外部 Agent 框架' },
  { name: 'Memory 记忆', desc: '会话记忆 + 长期记忆,三档后端可切换' },
  { name: 'Plugin Tool', desc: '内置工具 + MCP + 三档接入,零代码优先' },
  { name: 'Web Service', desc: 'REST API 对外门面 + 本管理台' },
]
const stack = ['JDK 21', 'Spring Boot 3.x', 'Spring AI', 'SQLite', 'MCP', 'Picocli', 'Vue 3']

// ── 统一取数:信封 {code, message, data};code≠200 或请求异常 → 把 message 交给页面 ──
async function fetchData(url) {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = await res.json()
  if (body.code !== 200) throw new Error(body.message || `错误码 ${body.code}`)
  return body.data
}

function useView(url, { isList = false } = {}) {
  const loading = ref(true)
  const error = ref('')
  const data = ref(isList ? [] : null)
  async function load() {
    loading.value = true
    error.value = ''
    try {
      data.value = await fetchData(url)
    } catch (e) {
      error.value = e.message || String(e)
    } finally {
      loading.value = false
    }
  }
  // 必须 reactive:模板访问 view.loading/view.data 时 ref 才会自动解包;
  // 普通对象属性里的 ref 不参与模板解包,会导致"永远加载中/表格永远空"
  return reactive({ loading, error, data, load })
}

// ── 各视图的数据源 ─────────────────────────────────────────────
const sessions = useView('/api/v1/sessions', { isList: true })
const agents = useView('/api/v1/profiles', { isList: true }) // Agent 列表 = Profile 注册表
const tools = useView('/api/v1/tools', { isList: true })
const memory = useView('/api/v1/memory')
const schedules = useView('/api/v1/schedules', { isList: true })
const providers = useView('/api/v1/info') // Provider 列表取自 /info 的 providers 连通状态
const status = useView('/api/v1/info')

async function runNow(taskId) {
  try {
    await fetch('/api/v1/schedules/' + taskId + '/run', { method: 'POST' })
    schedules.load()
  } catch (e) {
    alert('执行失败: ' + (e.message || String(e)))
  }
}
async function toggleEnabled(task) {
  try {
    await fetch('/api/v1/schedules/' + task.taskId, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: !task.enabled }),
    })
    schedules.load()
  } catch (e) {
    alert('操作失败: ' + (e.message || String(e)))
  }
}

const loaded = ref(false)
onMounted(() => {
  if (!loaded.value) {
    loaded.value = true
    sessions.load()
    agents.load()
    schedules.load()
    tools.load()
    memory.load()
    providers.load()
    status.load()
  }
})

const activeView = computed(() => {
  switch (active.value) {
    case 'agents':
      return agents
    case 'schedules':
      return schedules
    case 'tools':
      return tools
    case 'memory':
      return memory
    case 'providers':
      return providers
    case 'status':
      return status
    default:
      return sessions
  }
})

function statusDot(ok) {
  return ok ? 'dot-ok' : 'dot-err'
}
function statusText(ok) {
  return ok ? '连通' : '断开'
}
function formatTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="layout">
    <!-- 左侧竖直导航 -->
    <aside class="sidebar" :class="{ open: navOpen }">
      <div class="brand">
        <img :src="logoMark" alt="OryxOS" class="brand-logo" />
      </div>
      <nav class="nav">
        <button
          v-for="v in views"
          :key="v.key"
          class="nav-item"
          :class="{ active: active === v.key }"
          @click="active = v.key; navOpen = false"
        >
          {{ v.label }}
        </button>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <main class="content">
      <header class="topbar">
        <button class="nav-toggle" aria-label="打开导航" @click="navOpen = !navOpen">☰</button>
        <h1 class="page-title">{{ views.find(v => v.key === active).label }}</h1>
      </header>

      <!-- 总览:静态预览信息,不依赖 API(后续逐步接入动态数据) -->
      <div v-if="active === 'overview'" class="overview">
        <div class="overview-hero">
          <img :src="logoMark" alt="OryxOS" class="overview-logo" />
          <div>
            <h2 class="overview-title">OryxOS</h2>
            <p class="overview-slogan">面向严监管企业的私有可审计 Agent OS</p>
            <p class="overview-sub dim">Java 原生 · 部署在企业自己的服务器 · 数据不出企业</p>
            <span class="badge badge-warn">静态预览 · 数据接入中</span>
          </div>
        </div>

        <div class="overview-grid">
          <div v-for="c in capabilities" :key="c.name" class="overview-card">
            <div class="overview-card-name">{{ c.name }}</div>
            <div class="overview-card-desc dim">{{ c.desc }}</div>
          </div>
        </div>

        <div class="panel overview-stack">
          <div class="overview-card-name">技术栈</div>
          <div class="overview-stack-list">
            <span v-for="t in stack" :key="t" class="overview-chip mono">{{ t }}</span>
          </div>
        </div>
      </div>

      <!-- Sandbox 白名单:列表视图,内容接入中(暂无 API 数据源) -->
      <div v-else-if="active === 'sandbox'" class="placeholder">
        暂无数据(白名单配置视图接入中)
      </div>

      <!-- 三态:加载中 -->
      <div v-else-if="activeView.loading" class="placeholder">加载中…</div>

      <!-- 三态:请求错误(显示统一信封 message,不白屏) -->
      <div v-else-if="activeView.error" class="placeholder placeholder-error">
        <p>{{ activeView.error }}</p>
        <button class="btn" @click="activeView.load()">重试</button>
      </div>

      <!-- 三态:空数据 -->
      <div v-else-if="Array.isArray(activeView.data) && activeView.data.length === 0" class="placeholder">
        暂无数据
      </div>

      <!-- 会话列表 -->
      <table v-else-if="active === 'sessions'" class="table">
        <thead>
          <tr><th>会话 ID</th><th>Profile</th><th>渠道</th><th>用户</th><th>状态</th><th>最近活跃</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in sessions.data" :key="s.sessionId">
            <td class="mono">{{ s.sessionId }}</td>
            <td>{{ s.profileName }}</td>
            <td>{{ s.channel }}</td>
            <td class="mono">{{ s.userId }}</td>
            <td><span class="badge" :class="s.status === 'archived' ? 'badge-warn' : 'badge-ok'">{{ s.status }}</span></td>
            <td class="mono dim">{{ formatTime(s.lastActiveAt) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 定时任务列表(第 28 节:管理台第一个带写操作的页) -->
      <table v-else-if="active === 'schedules'" class="table">
        <thead>
          <tr><th>任务 ID</th><th>Profile</th><th>cron</th><th>下次触发</th><th>上次结果</th><th>次数</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in schedules.data" :key="t.taskId">
            <td class="mono">{{ t.taskId }}</td>
            <td>{{ t.profileName }}</td>
            <td class="mono dim">{{ t.cron }}</td>
            <td class="mono dim">{{ formatTime(t.nextRunAt) }}</td>
            <td>
              <span class="badge" :class="t.lastStatus === 'success' ? 'badge-ok' : t.lastStatus === 'failed' ? 'badge-err' : 'badge-warn'">
                {{ t.lastStatus || '—' }}
              </span>
            </td>
            <td class="mono">{{ t.runCount }}</td>
            <td><span class="badge" :class="t.enabled ? 'badge-ok' : 'badge-warn'">{{ t.enabled ? '启用' : '停用' }}</span></td>
            <td class="action-cell">
              <button class="btn btn-sm" @click="runNow(t.taskId)" title="立即执行一次">▶ 执行</button>
              <button class="btn btn-sm" @click="toggleEnabled(t)" :title="t.enabled ? '停用' : '启用'">
                {{ t.enabled ? '⏸ 停用' : '▶ 启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Agent 列表(= Profile 注册表) -->
      <table v-else-if="active === 'agents'" class="table">
        <thead>
          <tr><th>名称</th><th>描述</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in agents.data" :key="p.name">
            <td class="mono">{{ p.name }}</td>
            <td class="dim">{{ p.description }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Tool 列表 -->
      <table v-else-if="active === 'tools'" class="table">
        <thead>
          <tr><th>工具</th><th>描述</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tools.data" :key="t.name">
            <td class="mono">{{ t.name }}</td>
            <td class="dim">{{ t.description }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Provider 列表(连通状态取自 /info) -->
      <table v-else-if="active === 'providers'" class="table">
        <thead>
          <tr><th>Provider</th><th>连通状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="(ok, name) in providers.data.providers || {}" :key="name">
            <td class="mono">{{ name }}</td>
            <td><span class="dot" :class="statusDot(ok)"></span>{{ statusText(ok) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 长期记忆(全文,等宽渲染) -->
      <pre v-else-if="active === 'memory'" class="memory mono">{{ memory.data && memory.data.memory }}</pre>

      <!-- 运行状态 -->
      <div v-else-if="active === 'status'" class="panel">
        <div class="status-row"><span class="dim">名称</span><span>{{ status.data.name }}</span></div>
        <div class="status-row"><span class="dim">版本</span><span class="mono">{{ status.data.version }}</span></div>
        <div class="status-row"><span class="dim">Java</span><span class="mono">{{ status.data.java }}</span></div>
        <div class="status-row"><span class="dim">时间</span><span class="mono dim">{{ formatTime(status.data.time) }}</span></div>
        <div class="status-row"><span class="dim">Provider 连通</span></div>
        <div v-for="(ok, name) in status.data.providers || {}" :key="name" class="status-row">
          <span class="dot" :class="statusDot(ok)"></span>
          <span class="mono">{{ name }}</span>
          <span class="dim">{{ statusText(ok) }}</span>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

/* ── 侧边导航 ── */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--bg);
  border-right: 1px solid var(--border);
  padding: 24px 14px;
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand {
  display: flex;
  align-items: center;
  padding: 4px 12px 24px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 18px;
}
.brand-logo { height: 64px; width: 64px; }
.nav { display: flex; flex-direction: column; gap: 3px; }
.nav-item {
  text-align: left;
  background: none;
  border: none;
  color: var(--text-2);
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 14.5px;
  font-family: var(--font-base);
  border-left: 2px solid transparent;
  transition: color 0.15s, background 0.15s, border-color 0.15s;
}
.nav-item:hover { color: var(--brand-2); background: var(--bg-mute); }
.nav-item.active {
  color: var(--brand);
  font-weight: 600;
  background: var(--bg-mute);
  border-left-color: var(--brand);
}

/* ── 内容区 ── */
.content { flex: 1; padding: 28px 40px; min-width: 0; }
.topbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0;
  color: var(--text-1);
}
.nav-toggle {
  display: none;
  background: none;
  border: 1px solid var(--border);
  color: var(--text-1);
  border-radius: var(--radius);
  padding: 4px 10px;
  cursor: pointer;
}

/* ── 表格 ── */
.table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.table th {
  text-align: left;
  color: var(--text-1);
  font-weight: 600;
  font-size: 0.8rem;
  letter-spacing: 0.08em;
  padding: 12px 14px;
  border-bottom: 2px solid var(--border);
  background: var(--bg-soft);
}
.table td { padding: 12px 14px; border-bottom: 1px solid var(--border); color: var(--text-2); }
.table tbody tr { transition: background 0.12s; }
.table tbody tr:hover { background: var(--bg-mute); }
.table tr:last-child td { border-bottom: none; }
.table td.mono { color: var(--text-1); }
.dim { color: var(--text-2); }

/* ── 徽标与状态点 ── */
.badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.badge-ok { color: var(--ok); background: rgba(74, 222, 128, 0.1); }
.badge-err { color: var(--err); background: rgba(248, 113, 113, 0.1); }
.badge-warn { color: var(--warn); background: var(--brand-soft); }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.dot-ok { background: var(--ok); }
.dot-err { background: var(--err); }

/* ── 面板与记忆 ── */
.panel {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.status-row { display: flex; align-items: center; gap: 10px; }
.status-row .dim { width: 120px; flex-shrink: 0; }
.memory {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  color: var(--text-2);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.8;
  margin: 0;
}

/* ── 总览页 ── */
.overview { display: flex; flex-direction: column; gap: 20px; }
.overview-hero {
  display: flex;
  gap: 20px;
  align-items: center;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px;
}
.overview-logo { width: 88px; height: 88px; }
.overview-title { font-size: 1.75rem; font-weight: 700; margin: 0; color: var(--text-1); }
.overview-slogan { color: var(--brand); font-weight: 600; margin: 6px 0 2px; font-size: 15px; }
.overview-sub { margin: 0 0 12px; font-size: 13.5px; }
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.overview-card {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px;
}
.overview-card-name { color: var(--text-1); font-weight: 600; margin-bottom: 6px; }
.overview-card-desc { font-size: 13px; }
.overview-stack .overview-card-name { margin-bottom: 10px; }
.overview-stack-list { display: flex; flex-wrap: wrap; gap: 8px; }
.overview-chip {
  padding: 4px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--brand-2);
  font-size: 12px;
  background: var(--bg-mute);
}

/* ── 三态占位 ── */
.placeholder {
  padding: 80px 24px;
  text-align: center;
  color: var(--text-3);
  font-size: 15px;
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}
.placeholder-error p { color: var(--err); margin: 0 0 14px; }
.btn {
  background: var(--bg-mute);
  color: var(--brand-2);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 6px 18px;
  cursor: pointer;
  font-size: 13px;
  font-family: var(--font-base);
}
.btn:hover { border-color: var(--brand); }
.btn-sm { font-size: 11px; padding: 3px 8px; margin-right: 6px; }
.action-cell { white-space: nowrap; }

/* ── 响应式:窄屏导航收起 ── */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: -200px;
    z-index: 10;
    transition: left 0.2s;
  }
  .sidebar.open { left: 0; }
  .nav-toggle { display: inline-block; }
  .content { padding: 16px; }
}
</style>
