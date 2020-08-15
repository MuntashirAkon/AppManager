---
next: false
sidebarDepth: 2
---
# Settings Page
Settings can be used to customize the behavior of the app.

::: details Table of Contents
[[toc]]
:::

## App Theme
Application theme selection

## Root Mode
Enable or disable root mode. 

::: tip
To use [ADB][1], root mode has to be disabled at first and then preferably after a relaunch, ADB mode will be detected automatically.
:::

_See also: [ADB over TCP][1]_

### Global Component Blocking
Enable component blocking globally. By default, blocking rules are not applied unless they are applied in the [App Details][2] page for any package. Upon enabling this option, all (old and new) rules are applied immediately for all apps without explicitly enabling blocking for any app.

::: warning Notice
Enabling this setting may have some unintended side-effects, such as rules that are not completely removed will get applied. So, proceed with caution. This option should be kept disabled if not required for some reasons.
:::

_See also: [What is global component blocking?][7]_

## Usage Access
Turning off this option disables the **App Usage** page as well as _data usage_ and _app storage info_ in the [App Info tab][3]. With this option turned off, App Manager will never ask for _Usage Access_ permission

## Import/Export Blocking Rules
It is possible to import or export blocking rules within App Manager for all apps. There is a choice to export or import only certain rules (components, app ops or permissions) instead of all of them. It is also possible to import blocking rules from [Blocker][4] and [Watt][5]. If it is necessary to export blocking rules for a single app, use the corresponding [App Details][2] page to export rules, or for multiple apps, use [batch operations][6].

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#app-info-tab
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#batch-operations
[7]: ../faq/app-components.md#what-is-global-component-blocking
