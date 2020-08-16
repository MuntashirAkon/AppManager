const { description } = require('../../package')

module.exports = {
  locales: {
    '/': {
      lang: 'en-US',
      title: 'App Manager Docs',
      description: description,      
    }
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
      }
    ],
    sidebar: [
      {
        title: 'Instructions',
        collapsable: false,
        children: [
          '',
          '/guide/',
          '/guide/adb-over-tcp',
          '/guide/main-page',
          '/guide/app-details-page',
          '/guide/one-click-ops-page',
          '/guide/exodus-page',
          '/guide/shared-pref-editor-page',
          '/guide/settings-page',
        ]
      },
      {
        title: 'Frequently Asked Questions',
        path: '/faq',
        collapsable: false,
        children: [
          '/faq/app-components',
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
  plugins: [
    '@vuepress/last-updated',
    '@vuepress/plugin-back-to-top',
    '@vuepress/plugin-medium-zoom',
  ]
}
