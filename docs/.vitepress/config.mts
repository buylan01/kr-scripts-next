import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  base: '/kr-scripts-next/',
  title: "Kr Scripts Next",
  description: "A Xml-Based UI Engine with Shell-binding Logic",
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Guide', link: '/Intro.md' }
    ],

    sidebar: [
      {
        text: 'Introduction',
        items: [
          { text: 'What is KrScript?', link: '/Intro.md' }
        ]
      },
      {
        text: 'Feature Node',
        items: [
          { text: 'page', link: '/Page.md' },
          { text: 'action', link: '/Action.md' },
          { text: 'switch', link: '/Switch.md' },
          { text: 'picker', link: '/Picker.md' }
        ]
      },
      {
        text: 'Appearance Node',
        items: [
          { text: 'text', link: '/Text.md' },
          { text: 'group', link: '/Group.md' }
        ]
      },
      {
        text: 'Suggestion',
        items: [
          { text: 'scripts', link: '/Script.md' },
          { text: 'resources', link: '/Resource.md' },
          { text: 'property', link: '/Property_Other.md' },
          { text: 'extra', link: '/Extra.md' },
        ]
      },
      {
        text: 'Other',
        items: [
          { text: 'other', link: '/Other.md' },
          { text: 'bootstrap', link: '/Bootstrap.md' },
          { text: 'web', link: '/Web.md' }
        ]
      },
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/vuejs/vitepress' }
    ],

    footer: {
      message: '本文档基于 <a href="https://github.com/helloklf/kr-scripts" target="_blank">kr-scripts</a>（GPL v3）修改整理。',
      copyright: '原始文档 © kr-scripts 原作者 | 修改与站点构建 © 2026 buylan'
    }
  }
})
