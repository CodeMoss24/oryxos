<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import logo from './assets/logo.svg'

// ── 导航与页面状态 ─────────────────────────────────────────────
const views = [
  { key: 'sessions', label: '会话列表' },
  { key: 'profiles', label: 'Profile 列表' },
  { key: 'tools', label: 'Tool 列表' },
  { key: 'memory', label: '长期记忆' },
  { key: 'status', label: '运行状态' },
]
const active = ref('sessions')
const navOpen = ref(false) // 窄屏导航收起

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

// ── 五个只读视图的数据源 ───────────────────────────────────────
const sessions = useView('/api/v1/sessions', { isList: true })
const profiles = useView('/api/v1/profiles', { isList: true })
const tools = useView('/api/v1/tools', { isList: true })
const memory = useView('/api/v1/memory')
const status = useView('/api/v1/info')

const loaded = ref(false)
onMounted(() => {
  if (!loaded.value) {
    loaded.value = true
    sessions.load()
    profiles.load()
    tools.load()
    memory.load()
    status.load()
  }
})

const activeView = computed(() => {
  switch (active.value) {
    case 'profiles':
      return profiles
    case 'tools':
      return tools
    case 'memory':
      return memory
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
        <img :src="logo" alt="OryxOS" class="brand-logo" />
        <span class="brand-name">OryxOS 管理台</span>
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

      <!-- 三态:加载中 -->
      <div v-if="activeView.loading" class="placeholder">加载中…</div>

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
          <tr v-for="s in sessions.data" :key="s.session_id">
            <td class="mono">{{ s.session_id }}</td>
            <td>{{ s.profile_name }}</td>
            <td>{{ s.channel }}</td>
            <td class="mono">{{ s.user_id }}</td>
            <td><span class="badge" :class="s.status === 'archived' ? 'badge-warn' : 'badge-ok'">{{ s.status }}</span></td>
            <td class="mono dim">{{ formatTime(s.last_active_at) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Profile 列表 -->
      <table v-else-if="active === 'profiles'" class="table">
        <thead>
          <tr><th>名称</th><th>描述</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in profiles.data" :key="p.name">
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
  width: 200px;
  flex-shrink: 0;
  background: var(--bg);
  border-right: 1px solid var(--border);
  padding: 20px 12px;
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 20px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}
.brand-logo { height: 28px; width: 28px; }
.brand-name { font-weight: 600; color: var(--text-1); }
.nav { display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  text-align: left;
  background: none;
  border: none;
  color: var(--text-2);
  padding: 9px 10px;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 14px;
  font-family: var(--font-base);
}
.nav-item:hover { color: var(--brand-2); background: var(--bg-mute); }
.nav-item.active { color: var(--brand); font-weight: 600; background: var(--bg-mute); }

/* ── 内容区 ── */
.content { flex: 1; padding: 24px 28px; min-width: 0; }
.topbar { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.page-title { font-size: 1.25rem; font-weight: 600; margin: 0; color: var(--text-1); }
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
.table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
.table th {
  text-align: left;
  color: var(--text-1);
  font-weight: 600;
  padding: 10px 12px;
  border-bottom: 2px solid var(--border);
  background: var(--bg-soft);
}
.table td { padding: 10px 12px; border-bottom: 1px solid var(--border); color: var(--text-2); }
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
.badge-warn { color: var(--warn); background: var(--brand-soft); }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.dot-ok { background: var(--ok); }
.dot-err { background: var(--err); }

/* ── 面板与记忆 ── */
.panel {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.status-row { display: flex; align-items: center; gap: 10px; }
.status-row .dim { width: 110px; flex-shrink: 0; }
.memory {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  color: var(--text-2);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}

/* ── 三态占位 ── */
.placeholder {
  padding: 60px 20px;
  text-align: center;
  color: var(--text-3);
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}
.placeholder-error p { color: var(--err); margin: 0 0 12px; }
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
