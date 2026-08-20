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
const agents = useView('/api/v1/agents', { isList: true }) // Agent 管理列表(第 30 节)
const tools = useView('/api/v1/tools', { isList: true })
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

// ── Agent 管理(第 30 节):新建 / 删除 / 详情 5 tab ─────────────
async function fetchJson(url, options) {
  const res = await fetch(url, options)
  const body = await res.json()
  if (body.code !== 200) throw new Error(body.message || `错误码 ${body.code}`)
  return body.data
}

const detailAgent = ref(null)
const detailTab = ref('basic')
const detailTabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'generate', label: '生成' },
  { key: 'files', label: '文件' },
  { key: 'session', label: '会话' },
  { key: 'memory', label: '记忆' },
]
const createName = ref('')
const createDesc = ref('')
const agentError = ref('')

async function createAgent() {
  agentError.value = ''
  if (!createName.value.trim()) {
    agentError.value = 'name 不能为空'
    return
  }
  try {
    await fetchJson('/api/v1/agents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: createName.value.trim(), description: createDesc.value.trim() }),
    })
    createName.value = ''
    createDesc.value = ''
    agents.load()
  } catch (e) {
    agentError.value = e.message || String(e)
  }
}

async function removeAgent(name) {
  if (!confirm(`确定归档 Agent「${name}」?目录将移入 .oryxos/archive/`)) return // 删前二次确认
  try {
    await fetchJson('/api/v1/agents/' + name, { method: 'DELETE' })
    agents.load()
  } catch (e) {
    alert(e.message || String(e))
  }
}

function openDetail(agent) {
  detailAgent.value = agent
  detailTab.value = 'basic'
  genDesc.value = ''
  genDraft.value = ''
  genError.value = ''
  filePath.value = ''
  fileContent.value = ''
  fileError.value = ''
  sessionMsg.value = ''
  sessionError.value = ''
  memoryError.value = ''
  loadSession()
  loadMemory()
  loadTree()
}

// 生成 tab:一句话 → LLM 草稿 → 预览可改 → 保存生效
const genDesc = ref('')
const genDraft = ref('')
const genError = ref('')
async function generateDraft() {
  genError.value = ''
  try {
    const files = await fetchJson('/api/v1/agents/' + detailAgent.value.name + '/generate-files', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ description: genDesc.value }),
    })
    genDraft.value = files['AGENT.md'] || ''
  } catch (e) {
    genError.value = e.message || String(e)
  }
}
async function saveDraft() {
  genError.value = ''
  try {
    await fetchJson('/api/v1/agents/' + detailAgent.value.name + '/files', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files: { 'AGENT.md': genDraft.value } }),
    })
    agents.load()
    alert('已保存并生效')
  } catch (e) {
    genError.value = e.message || String(e)
  }
}

// 文件 tab:目录树 → 点文件读内容 → 改后保存
const fileTree = ref(null)
const filePath = ref('')
const fileContent = ref('')
const fileError = ref('')
async function loadTree() {
  fileError.value = ''
  try {
    fileTree.value = await fetchData('/api/v1/workspace/tree')
  } catch (e) {
    fileError.value = e.message || String(e)
  }
}
function flattenTree(node, depth, out) {
  out.push({ node, depth })
  for (const c of node.children || []) flattenTree(c, depth + 1, out)
  return out
}
function agentFileList() {
  if (!fileTree.value) return []
  const agentsNode = (fileTree.value.children || []).find(c => c.name === 'agents')
  const node = (agentsNode && agentsNode.children || []).find(c => c.name === detailAgent.value.name)
  return node ? flattenTree(node, 0, []) : []
}
async function openFile(node) {
  filePath.value = node.path
  fileError.value = ''
  try {
    const data = await fetchData('/api/v1/workspace/file?path=' + encodeURIComponent(node.path))
    fileContent.value = data.content
  } catch (e) {
    fileError.value = e.message || String(e)
  }
}
async function saveFile() {
  fileError.value = ''
  try {
    await fetchJson('/api/v1/workspace/file', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: filePath.value, content: fileContent.value }),
    })
    loadTree()
    alert('已保存')
  } catch (e) {
    fileError.value = e.message || String(e)
  }
}

// 会话 tab:固定会话看上下文、发消息
const sessionView = ref(null)
const sessionMsg = ref('')
const sessionError = ref('')
async function loadSession() {
  sessionError.value = ''
  try {
    sessionView.value = await fetchData('/api/v1/agents/' + detailAgent.value.name + '/session')
  } catch (e) {
    sessionError.value = e.message || String(e)
  }
}
async function sendSessionMessage() {
  sessionError.value = ''
  if (!sessionMsg.value.trim()) return
  try {
    await fetchJson('/api/v1/agents/' + detailAgent.value.name + '/session/messages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: sessionMsg.value }),
    })
    sessionMsg.value = ''
    loadSession()
  } catch (e) {
    sessionError.value = e.message || String(e)
  }
}

// 记忆 tab:这个 Agent 自己的 MEMORY.md
const memoryText = ref('')
const memoryError = ref('')
async function loadMemory() {
  memoryError.value = ''
  try {
    const data = await fetchData('/api/v1/agents/' + detailAgent.value.name + '/memory')
    memoryText.value = data.memory
  } catch (e) {
    memoryError.value = e.message || String(e)
  }
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

      <!-- Agent 详情(第 30 节:基本信息/生成/文件/会话/记忆 5 tab) -->
      <div v-if="detailAgent" class="detail">
        <div class="detail-head">
          <button class="btn" @click="detailAgent = null">← 返回列表</button>
          <h2 class="detail-title mono">{{ detailAgent.name }}</h2>
          <div class="tabs">
            <button
              v-for="t in detailTabs"
              :key="t.key"
              class="tab"
              :class="{ active: detailTab === t.key }"
              @click="detailTab = t.key"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <div v-if="detailTab === 'basic'" class="panel">
          <div class="status-row"><span class="dim">名称</span><span class="mono">{{ detailAgent.name }}</span></div>
          <div class="status-row"><span class="dim">描述</span><span>{{ detailAgent.description }}</span></div>
          <div class="status-row">
            <span class="dim">Provider</span>
            <span class="mono">{{ detailAgent.provider && detailAgent.provider.name }} / {{ detailAgent.provider && detailAgent.provider.model }}</span>
          </div>
          <div class="status-row"><span class="dim">Tools</span><span class="mono">{{ (detailAgent.tools || []).join(', ') }}</span></div>
          <div class="status-row"><span class="dim">Skills</span><span class="mono">{{ (detailAgent.skills || []).join(', ') || '—' }}</span></div>
          <div class="status-row">
            <span class="dim">定时</span>
            <span class="mono">{{ (detailAgent.schedules || []).map(s => s.cron).join('; ') || '—' }}</span>
          </div>
        </div>

        <div v-else-if="detailTab === 'generate'" class="panel">
          <label class="dim">一句话描述(LLM 生成 AGENT.md 草稿,不落盘)</label>
          <input v-model="genDesc" class="input" placeholder="例:每天早上 9 点推送天气给 webhook" />
          <button class="btn" :disabled="!genDesc.trim()" @click="generateDraft">生成草稿</button>
          <label v-if="genDraft" class="dim">草稿预览(可改,含 frontmatter)</label>
          <textarea v-if="genDraft" v-model="genDraft" class="textarea mono" rows="16"></textarea>
          <button v-if="genDraft" class="btn btn-primary" @click="saveDraft">保存并生效</button>
          <p v-if="genError" class="err">{{ genError }}</p>
        </div>

        <div v-else-if="detailTab === 'files'" class="panel">
          <div class="files-layout">
            <div class="file-side">
              <div v-if="fileError" class="err">{{ fileError }}</div>
              <div v-for="f in agentFileList()" :key="f.node.path" class="file-row" :style="{ paddingLeft: (12 + f.depth * 18) + 'px' }">
                <span v-if="f.node.type === 'dir'" class="dim">{{ f.node.name }}/</span>
                <button v-else class="file-link" :class="{ active: f.node.path === filePath }" @click="openFile(f.node)">
                  {{ f.node.name }}
                </button>
              </div>
            </div>
            <div class="file-main">
              <div v-if="filePath" class="mono dim file-path">{{ filePath }}</div>
              <textarea v-if="filePath" v-model="fileContent" class="textarea mono" rows="22"></textarea>
              <button v-if="filePath" class="btn" @click="saveFile">保存文件</button>
            </div>
          </div>
        </div>

        <div v-else-if="detailTab === 'session'" class="panel">
          <div v-if="sessionError" class="err">{{ sessionError }}</div>
          <div class="chat">
            <div v-for="(m, i) in (sessionView ? sessionView.messages : [])" :key="i" class="chat-row">
              <span class="badge" :class="m.role === 'user' ? 'badge-warn' : 'badge-ok'">{{ m.role }}</span>
              <span class="chat-content">{{ m.content }}</span>
            </div>
            <div v-if="sessionView && !sessionView.messages.length" class="dim">还没有消息,发一条试试</div>
          </div>
          <div class="chat-input">
            <input v-model="sessionMsg" class="input" placeholder="给这个 Agent 发消息(固定会话,上下文累积)" @keyup.enter="sendSessionMessage" />
            <button class="btn" @click="sendSessionMessage">发送</button>
          </div>
        </div>

        <div v-else-if="detailTab === 'memory'" class="panel">
          <div v-if="memoryError" class="err">{{ memoryError }}</div>
          <pre class="memory mono">{{ memoryText || '(暂无记忆)' }}</pre>
        </div>
      </div>

      <!-- 总览:静态预览信息,不依赖 API(后续逐步接入动态数据) -->
      <div v-else-if="active === 'overview'" class="overview">
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

      <!-- Agent 管理(第 30 节:新建 / 列表 / 删除,删除前二次确认) -->
      <div v-else-if="active === 'agents'" class="agents-panel">
        <div class="create-row">
          <input v-model="createName" class="input" placeholder="Agent 名称(英文)" />
          <input v-model="createDesc" class="input" placeholder="一句话描述" @keyup.enter="createAgent" />
          <button class="btn btn-primary" @click="createAgent">新建</button>
        </div>
        <p v-if="agentError" class="err">{{ agentError }}</p>
        <table class="table">
          <thead>
            <tr><th>名称</th><th>描述</th><th>Provider</th><th>定时</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in agents.data" :key="a.name">
              <td class="mono">{{ a.name }}</td>
              <td class="dim">{{ a.description }}</td>
              <td class="mono dim">{{ a.provider && a.provider.name }}</td>
              <td class="mono dim">{{ (a.schedules || []).length }}</td>
              <td class="action-cell">
                <button class="btn btn-sm" @click="openDetail(a)">查看</button>
                <button class="btn btn-sm" @click="removeAgent(a.name)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
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

/* ── Agent 管理(第 30 节) ── */
.agents-panel { display: flex; flex-direction: column; gap: 14px; }
.create-row { display: flex; gap: 10px; flex-wrap: wrap; }
.input {
  flex: 1;
  min-width: 180px;
  background: var(--bg);
  color: var(--text-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px 12px;
  font-size: 14px;
  font-family: var(--font-base);
}
.input:focus, .textarea:focus { outline: none; border-color: var(--brand); }
.err { color: var(--err); font-size: 13px; margin: 0; }
.btn-primary { background: var(--brand); color: #1a0a00; border-color: var(--brand); font-weight: 600; }
.btn-primary:hover { background: var(--brand-2); border-color: var(--brand-2); }

.detail { display: flex; flex-direction: column; gap: 18px; }
.detail-head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.detail-title { font-size: 1.3rem; font-weight: 700; margin: 0; color: var(--text-1); }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border); margin-left: auto; }
.tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-2);
  padding: 8px 14px;
  cursor: pointer;
  font-size: 14px;
  font-family: var(--font-base);
}
.tab:hover { color: var(--brand-2); }
.tab.active { color: var(--brand); border-bottom-color: var(--brand); font-weight: 600; }

.textarea {
  width: 100%;
  box-sizing: border-box;
  background: var(--bg);
  color: var(--text-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}

.files-layout { display: flex; gap: 16px; align-items: flex-start; }
.file-side {
  width: 260px;
  flex-shrink: 0;
  max-height: 480px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px 4px;
  background: var(--bg);
}
.file-row { padding-top: 4px; padding-bottom: 4px; }
.file-link {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 13px;
  font-family: var(--font-base);
  padding: 2px 8px;
  border-radius: var(--radius);
}
.file-link:hover { color: var(--brand-2); }
.file-link.active { color: var(--brand); background: var(--bg-mute); font-weight: 600; }
.file-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.file-path { font-size: 12px; }

.chat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 380px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px;
  background: var(--bg);
}
.chat-row { display: flex; gap: 10px; align-items: flex-start; }
.chat-content { color: var(--text-2); font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.chat-input { display: flex; gap: 10px; }

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
