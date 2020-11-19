---
next: false
sidebarDepth: 2
---

# 设置页
自定义应用程序的行为。

::: details 目录
[[toc]]
:::

## 主题
Application theme selection

## Root 模式
Enable or disable root mode.

::: tip
To use [ADB][1], root mode has to be disabled at first and then preferably after a relaunch, ADB mode will be detected automatically.
:::

_See also: [ADB over TCP][1]_

### 默认开启 禁用组件 选项
Enable component blocking globally. By default, blocking rules are not applied unless they are applied in the [App Details][2] page for any package. Upon enabling this option, all (old and new) rules are applied immediately for all apps without explicitly enabling blocking for any app.

::: warning Notice
Enabling this setting may have some unintended side-effects, such as rules that are not completely removed will get applied. So, proceed with caution. This option should be kept disabled if not required for some reasons.
:::

_See also: [What is global component blocking?][7]_

## 使用情况访问权限
Turning off this option disables the **App Usage** page as well as _data usage_ and _app storage info_ in the [App Info tab][3]. With this option turned off, App Manager will never ask for _Usage Access_ permission

## 导入/导出规则
It is possible to import or export blocking rules within App Manager for all apps. There is a choice to export or import only certain rules (components, app ops or permissions) instead of all of them. It is also possible to import blocking rules from [Blocker][4] and [Watt][5]. If it is necessary to export blocking rules for a single app, use the corresponding [App Details][2] page to export rules, or for multiple apps, use [batch operations][6].

_See also: [App Manager: Rules Specification][rules_spec]_

### 导出
Export blocking rules for all apps configured within App Manager. This may include [app components][what_are_components], app ops and permissions based on what options is/are selected in the multichoice options.

### 导入
Import previously exported blocking rules from App Manager. Similar to export, this may include [app components][what_are_components], app ops and permissions based on what options is/are selected in the multichoice options.

### 导入现有规则
Add components disabled by other apps to App Manager. App Manager only keeps track of component disabled within App Manager. If you use other tools to block app components, you can use this tools to import these disabled components. Clicking on this option triggers a search for disabled components and will lists apps with components disabled by user. For safety, all the apps are unselected by default. You can manually select the apps in the list and re-apply the blocking through App Manager.

::: danger Caution
Be careful when using this tool as there can be many false positives. Choose only the apps that you are certain about.
:::

### 从 Watt 导入
Import configuration files from [Watt][5], each file containing rules for a single package and file name being the name of the package with `.xml` extension.

::: tip
Location of configuration files in Watt: <tt>/sdcard/Android/data/com.tuyafeng.watt/files/ifw</tt>
:::

### 从 Blocker 导入
Import blocking rules from [Blocker][4], each file containing rules for a single package. These files have a `.json` extension.

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#应用详情标签
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#批量操作
[7]: ../faq/app-components.md#全局拦截组件
[what_are_components]: ../faq/app-components.md#什么是应用组件
[rules_spec]: ../tech/rules-specification.md
