---
name: oryxos-admin-ui
description: 生成/修改 OryxOS 管理台前端页面(Vue 3 + Vite,深色+橙、只读、总览+七页结构)。当用户说「生成管理台」「给管理台加一页」「管理台 UI」时使用。
---

# OryxOS 管理台 UI 规范(项目内 skill)

OryxOS 管理台 = 托管在 `/admin` 的 Vue 3 + Vite 单页应用,只调 `/api/v1` 的查询端点渲染数据。本 skill 固化设计 token、工程约定与验收清单,保证每次产出与官网首页同源、可复现。30 节加"Agent 管理"页时同样调本 skill。

## 1. 设计 token(唯一风格来源:website/.vitepress/theme/custom.css)

> ⚠️ 以 custom.css 实际值为准。课件提示词中曾列过 `#f97316/#ea6a00/#c2550a/#111111/#f5f5f5/#a3a3a3/#666666` 一组值,与文件不符——一律以本表(取自文件)为准,一个字别自创。

| Token | 值 | 用途 |
|---|---|---|
| 背景 | `#000000` | 页面背景 |
| 卡片/悬浮 | `#080808`(soft)/ `#0d0d0d`(mute) | 卡片、面板 |
| 边框/分隔 | `#1a1a1a` | 分隔线、表格行线 |
| 主色(橙) | `#FF6B2B` | 激活项、链接、数值高亮——只做强调,不铺大面积 |
| hover | `#FF8C42` | 悬浮态 |
| 强调/深橙 | `#E8450A` | 强强调(数字/徽标) |
| 文字主 | `#eeeeee` | 标题/正文 |
| 文字次 | `#999999` | 描述、表体 |
| 文字弱 | `#555555` | 次要元信息 |
| 字体正文 | `'Space Grotesk', 'Inter', system-ui, sans-serif` | 正文 |
| 字体等宽 | `'JetBrains Mono', 'Fira Code', monospace` | 代码/ID/JSON/时间戳 |
| 圆角 | 4–6px | 按钮/卡片/行 hover |
| 状态色 | 成功 `#4ade80` / 失败 `#f87171` / 警告 `#FF6B2B` | 状态小圆点/标签 |

## 2. 工程约定

- 工程位置:`oryxos-web/src/main/frontend/`;Vue 3 + Vite,与 `website/` 首页同栈,不用裸 HTML
- `vite.config.js`:`base: '/admin/'`,`build.outDir` 指向 `../resources/static/admin`,产物相对资源路径
- 构建串联:frontend-maven-plugin(oryxos-web/pom.xml,generate-resources 阶段 install-node-and-npm → npm install → npm run build);产物**不入库**(.gitignore),由插件打包时重建;仅后端迭代 `-Dfrontend.skip=true`
- 数据来源:只调 `GET /api/v1/...`;统一响应信封 `{code, message, data, timestamp}`,**code≠200 或请求异常时把 message 显示给用户**
- SPA 回落:Spring 侧 `/admin/**` 未命中文件回落 `index.html`(已由 WebConfig 的 PathResourceResolver 实现),前端路由随意切,不担心刷新 404
- 不引入外部 UI 组件库;确需组件库时只选可被本 token 完全覆盖的 headless 方案

## 3. 布局与页面规范

- 左侧竖直导航(深色,宽 ~200px,窄屏收起为顶部条)+ 右侧内容区;顶部 `/logo.svg` + "OryxOS 管理台"
- 导航八项:**总览**(静态预览信息,hero + 五大能力卡片 + 技术栈 chips,暂不调 API,逐步动态化)→ Agent(GET /api/v1/profiles,注册表即 Agent 列表)→ 会话列表(GET /api/v1/sessions)→ Tool 列表(GET /api/v1/tools)→ 长期记忆(GET /api/v1/memory)→ Sandbox 白名单(列表视图,暂无 API 数据源,静态空态占位)→ Provider 列表(取自 GET /api/v1/info 的 providers 连通状态)→ 运行状态(GET /api/v1/info)
- **只读**:任何页面不得出现新建/编辑/删除按钮,不做任何 POST/PUT/DELETE
- 表格深色、行分隔 `#1a1a1a`、表头文字 `#eeeeee`;记忆页渲染 Markdown 文本(等宽字体);状态页 provider 连通用"成功绿/断开红"圆点 + 标签;总览页卡片栅格 + 等宽 chips,hero 区主色仅用于 slogan 强调
- **三态占位必做**:加载中(骨架/loading 文案)、空数据(明确空态文案)、请求错误(显示信封 message + 重试按钮)——任何情况下不白屏;静态页(如总览)置于三态判断之前渲染
- 克制、留白足、圆角小(4–6px),与官网首页一个气质

## 4. 验收清单(生成后逐项自查)

- [ ] `npm run build` 无报错,产物落 `static/admin/`
- [ ] 五页分别渲染真实数据;断掉后端时每页显示错误 message,不白屏
- [ ] 全站无任何写操作入口(没有按钮/表单提交)
- [ ] 子路径(如 /admin/sessions)刷新不 404
- [ ] 色值/字体与 §1 token 表一致(抽查 3 处)
- [ ] 窄屏(<768px)导航可收起,页面不横向滚动
