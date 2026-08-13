import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 管理台由 Spring 托在 /admin 子路径；产物直接落进 oryxos-web 的 static/admin，随 fat JAR 分发。
// dev 时把 /api 代理到本地 serve（默认 8080；端口被占时用 ORYXOS_API_PROXY 环境变量覆盖，
// 如 ORYXOS_API_PROXY=http://localhost:8081 npm run dev），便于热更调试（发布形态不经代理）。
export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  build: {
    outDir: '../resources/static/admin',
    emptyOutDir: true,
  },
  server: {
    proxy: { '/api': process.env.ORYXOS_API_PROXY || 'http://localhost:8080' },
  },
})
