import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'OryxOS',
  titleTemplate: ':title — OryxOS',
  description: 'Java 原生、私有可审计的 Agent OS — 为严监管企业设计，数据不出企业。',
  base: '/',
  cleanUrls: true,
  appearance: 'force-light',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@700&display=swap' }],
    ['meta', { name: 'author', content: 'OryxOS' }],
    ['meta', { name: 'keywords', content: 'OryxOS, Agent OS, AI Agent, Java, Spring Boot, private deployment, audit, regulated enterprise, ReAct, MCP, multi-agent' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'OryxOS' }],
    ['meta', { property: 'og:title', content: 'OryxOS — Java-native Private Agent OS' }],
    ['meta', { property: 'og:description', content: 'Java 原生、私有可审计的 Agent OS — 为严监管企业设计，数据不出企业。' }],
  ],

  locales: {
    root: {
      label: '中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: [
          { text: '首页', link: '/' },
          { text: '文档', link: '/docs/overview' },
          { text: 'GitHub', link: 'https://github.com/CodeMoss24/oryx-labs' },
        ],
        sidebar: {
          '/docs/': [
            {
              text: '快速入门',
              items: [
                { text: '项目概述', link: '/docs/overview' },
                { text: '快速开始', link: '/docs/quick-start' },
                { text: '核心概念', link: '/docs/concepts' },
              ],
            },
            {
              text: '核心能力',
              items: [
                { text: 'LLM Provider', link: '/docs/provider' },
                { text: 'ReAct Loop', link: '/docs/react' },
                { text: 'Memory', link: '/docs/memory' },
                { text: 'Tool 体系', link: '/docs/tools' },
                { text: 'Web Service', link: '/docs/web-service' },
              ],
            },
            {
              text: '扩展',
              items: [
                { text: 'MCP 集成', link: '/docs/mcp' },
                { text: 'Agent 开发指南', link: '/docs/agent-guide' },
                { text: 'CLI 命令', link: '/docs/cli' },
              ],
            },
            {
              text: '参考',
              items: [
                { text: 'API 参考', link: '/docs/api' },
                { text: 'FAQ', link: '/docs/faq' },
                { text: '路线图', link: '/docs/roadmap' },
              ],
            },
          ],
        },
      },
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'Docs', link: '/en/docs/overview' },
          { text: 'GitHub', link: 'https://github.com/CodeMoss24/oryx-labs' },
        ],
        sidebar: {
          '/en/docs/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Overview', link: '/en/docs/overview' },
                { text: 'Quick Start', link: '/en/docs/quick-start' },
                { text: 'Core Concepts', link: '/en/docs/concepts' },
              ],
            },
            {
              text: 'Core Capabilities',
              items: [
                { text: 'LLM Provider', link: '/en/docs/provider' },
                { text: 'ReAct Loop', link: '/en/docs/react' },
                { text: 'Memory', link: '/en/docs/memory' },
                { text: 'Tool System', link: '/en/docs/tools' },
                { text: 'Web Service', link: '/en/docs/web-service' },
              ],
            },
            {
              text: 'Extensions',
              items: [
                { text: 'MCP Integration', link: '/en/docs/mcp' },
                { text: 'Agent Development', link: '/en/docs/agent-guide' },
                { text: 'CLI Commands', link: '/en/docs/cli' },
              ],
            },
            {
              text: 'Reference',
              items: [
                { text: 'API Reference', link: '/en/docs/api' },
                { text: 'FAQ', link: '/en/docs/faq' },
                { text: 'Roadmap', link: '/en/docs/roadmap' },
              ],
            },
          ],
        },
      },
    },
  },

  themeConfig: {
    siteTitle: false,
    logo: '/logo.svg',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/CodeMoss24/oryx-labs' },
    ],
  },
})
