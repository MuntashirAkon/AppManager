const { description } = require('../../package')

module.exports = {
  locales: {
    '/': {
      lang: 'en-US',
      title: 'App Manager Docs',
      description: description,      
    },
    '/ru/': {
      lang: 'ru-RU',
      title: 'Документация App Manager',
      description: description,      
    },
    '/zh-CN/': {
      lang: 'zh-CN',
      title: 'App Manager 文档',
      description: description,      
    },
  },
  head: [
    ['meta', { name: 'theme-color', content: '#3eaf7c' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { name: 'apple-mobile-web-app-status-bar-style', content: 'black' }]
  ],
  base: '/AppManager/',
  markdown: {
    lineNumbers: true,
  },
  themeConfig: {
    repo: '',
    editLinks: true,
    docsDir: '',
    editLinkText: '',
    lastUpdated: true,
    logo: '/icon.png',
    locales: {
      '/': {
        selectText: 'Languages',
        // text for the language dropdown
        selectText: 'Languages',
        // label for this locale in the language dropdown
        label: 'English',
        // Aria Label for locale in the dropdown
        ariaLabel: 'Languages',
        // config for Service Worker
        serviceWorker: {
          updatePopup: {
            message: "New content is available.",
            buttonText: "Refresh"
          }
        },
        nav: [
          {
            text: 'Instructions',
            link: '/guide/',
          },
          {
            text: 'Changelog',
            link: '/changelog',
          },
          {
            text: 'F-Droid',
            link: 'https://f-droid.org/packages/io.github.muntashirakon.AppManager'
          },
          {
            text: 'Source Code',
            link: 'https://github.com/MuntashirAkon/AppManager'
          },
        ],
        sidebar: [
          {
            title: 'Instructions',
            collapsable: false,
            children: [
              '/guide/',
              '/guide/adb-over-tcp',
              '/guide/main-page',
              '/guide/app-details-page',
              '/guide/one-click-ops-page',
              '/guide/scanner-page',
              '/guide/shared-pref-editor-page',
              '/guide/settings-page',
              '/guide/profiles-page',
              '/guide/profile-page',
              '/guide/backup-restore',
            ]
          },
          {
            title: 'Frequently Asked Questions',
            path: '/faq',
            collapsable: false,
            children: [
              '/faq/app-components',
              '/faq/adb',
              '/faq/misc',
            ]
          },
          {
            title: 'Technical Information',
            path: '/tech',
            collapsable: false,
            children: [
              '/tech/AppOps',
              '/tech/rules-specification',
            ]
          },
        ],
      },
      '/ru/': {
        selectText: 'Языки',
        // text for the language dropdown
        selectText: 'Языки',
        // label for this locale in the language dropdown
        label: 'Русский',
        // Aria Label for locale in the dropdown
        ariaLabel: 'Языки',
        // config for Service Worker
        serviceWorker: {
          updatePopup: {
            message: 'Доступен новый контент.',
            buttonText: 'Обновить',
          }
        },
        nav: [
          {
            text: 'Инструкции',
            link: '/ru/guide/',
          },
          {
            text: 'Список изменений',
            link: '/ru/changelog',
          },
          {
            text: 'F-Droid',
            link: 'https://f-droid.org/packages/io.github.muntashirakon.AppManager'
          },
          {
            text: 'Исходный код',
            link: 'https://github.com/MuntashirAkon/AppManager'
          },
        ],
        sidebar: [
          {
            title: 'Инструкции',
            collapsable: false,
            children: [
              '/ru/guide/',
              '/ru/guide/adb-over-tcp',
              '/ru/guide/main-page',
              '/ru/guide/app-details-page',
              '/ru/guide/one-click-ops-page',
              '/ru/guide/exodus-page',
              '/ru/guide/shared-pref-editor-page',
              '/ru/guide/settings-page',
            ]
          },
          {
            title: 'Часто задаваемые вопросы',
            path: '/ru/faq',
            collapsable: false,
            children: [
              '/ru/faq/app-components',
              '/ru/faq/adb',
              '/ru/faq/misc',
            ]
          },
          {
            title: 'Техническая информация',
            path: '/ru/tech',
            collapsable: false,
            children: [
              '/ru/tech/AppOps',
              '/ru/tech/rules-specification',
            ]
          },
        ],
      },
      '/zh-CN/': {
        selectText: '语言',
        // text for the language dropdown
        selectText: '语言',
        // label for this locale in the language dropdown
        label: '简体中文',
        // Aria Label for locale in the dropdown
        ariaLabel: '语言',
        // config for Service Worker
        serviceWorker: {
          updatePopup: {
            message: "内容可更新",
            buttonText: "刷新"
          }
        },
        nav: [
          {
            text: '指南',
            link: '/zh-CN/guide/',
          },
          {
            text: '更新日志',
            link: '/zh-CN/changelog',
          },
          {
            text: 'F-Droid',
            link: 'https://f-droid.org/packages/io.github.muntashirakon.AppManager'
          },
          {
            text: '源码',
            link: 'https://github.com/MuntashirAkon/AppManager'
          },
        ],
        sidebar: [
          {
            title: '指南',
            collapsable: false,
            children: [
              '/zh-CN/guide/',
              '/zh-CN/guide/adb-over-tcp',
              '/zh-CN/guide/main-page',
              '/zh-CN/guide/app-details-page',
              '/zh-CN/guide/one-click-ops-page',
              '/zh-CN/guide/exodus-page',
              '/zh-CN/guide/shared-pref-editor-page',
              '/zh-CN/guide/settings-page',
            ]
          },
          {
            title: '常见问题',
            path: '/zh-CN/faq',
            collapsable: false,
            children: [
              '/zh-CN/faq/app-components',
              '/zh-CN/faq/adb',
              '/zh-CN/faq/misc',
            ]
          },
          {
            title: '技术信息',
            path: '/zh-CN/tech',
            collapsable: false,
            children: [
              '/zh-CN/tech/AppOps',
              '/zh-CN/tech/rules-specification',
            ]
          },
        ],
      },
    },
  },
  plugins: [
    '@vuepress/last-updated',
    '@vuepress/plugin-back-to-top',
    '@vuepress/plugin-medium-zoom',
  ]
}
