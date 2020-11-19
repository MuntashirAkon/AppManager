---
prev: ./
next: ./misc
sidebarDepth: 2
---

# 应用组件

::: details 目录
[[toc]]
:::

## 什么是应用组件？
活动、服务、广播接收器(也称接收器)和内容提供者(也称提供者)合称为应用组件。 更严格地说，它们都继承了`ComponentInfo`类。

## 为什么被AM阻止的组件没有被其他相关应用检测到？
这是因为我使用的阻止方法。 这个方法叫做 [故意防火墙][1] (IFW) 兼容 [Watt][2] 和 [Blocker][3]。 [MyAndroidTool][4] (MAT) 支持IFW，但它使用不同的格式。 还有其他阻止应用组件的方法，例如 _pm_ and [Shizuku][5]。 如果应用组件被使用后一种方法阻止， 受影响的应用可以识别它，并且可以解除屏蔽它，因为它可以完全访问它自己的组件。 许多欺骗性应用实际上利用了这个功能，以保持追踪器组件被解除屏蔽。

## 通过其他应用或工具禁用的组件是否在 AM 中也被禁用？
**不会。** 但被 Android 系统或其他工具拦截的组件会在[ 应用详情 ][10]页（在组件页内）显示。 从版本 2.5.12 起，你可在[ 设置 ][9]中导入这些规则。 由于无法区分是由其他软件还是系统拦截的组件，故应当仔细选择和使用这些应用。

## 被 AM 禁用的组件同时也被其他应用或工具禁用，会发生什么？
AM 通过 [ Intent Firewall][1]（IFW）来拦截组件。 如果这些组件是通过 _pm_ 或 [Shizuku][5] 方法拦截的，则它们不会被取消拦截后并再次拦截。 如果你在 [应用详情][6] 页取消拦截组件，则组件会恢复为默认状态，即根据相应应用的 manifest 文件，通过 IFW 和 _pm_ 方法拦截或取消拦截组件。 然而，被 [MyAndroidTools（MAT）通过 IFW 方法拦截的组件不会被 AM 取消拦截。 要解决此问题，你可以先在 AM 的 [设置][9] 内导入相应的配置以清除 MAT 的配置。 注意，此选项仅可用于 2.5.12 及之后的版本。

## 全局拦截组件
当您在 [应用程序详细信息][6] 页面中阻止一个组件时，默认情况下不应用。 只有当您在右上角菜单中使用 _应用规则_ 选项应用阻止时，它才会被应用。 如果您启用了 _全局组件阻止_，阻止将在您阻止组件后立即应用。 但是，如果您选择阻止追踪器，无论设置如何，阻止都会自动应用。 您也可以通过点击_**APP详情**页面中同一菜单中的_删除规则_来移除应用程序的阻止。 由于默认行为让您更多地控制应用，最好保持 _全局组件阻止_ 选项。</p>

_另请参阅。[全局组件阻止][7]。_

## 如何取消阻止被一键操作或批量操作阻止的追踪器？
某些应用可能由于依赖于被应用管理器阻止的追踪器组件而出现错误。 从 v2.5.12版本开始，有一个选项可以解除在 [1-click Ops][8] 页面中的追踪器组件。 但是，在以前的版本中没有这种选择。 要解除阻止这些跟踪，请先前往错误应用的 [应用详细信息][6] 页面。 然后切换到 _活动_ 标签，点击 _删除规则_ 在右上角菜单中的选项。 所有与应用组件相关的阻止规则将立即删除。 或者，如果你找到了引起这个问题的组件， 您可以通过点击组件名称旁边的 _解除阻止_ 按钮来解锁组件. 如果您在设置中启用了 _全局组件阻止_ 首先禁用 _删除规则_ 启用时将不可见。

如果您安装了 **Google Play 服务** (`com.google.android.gms`)，解锁以下 [服务][services] 可能解决问题：
- **Ad Request Broker Service**<br /> `.ads.AdRequestBrokerService`
- **Cache Broker Service**<br /> `.ads.cache.CacheBrokerService`
- **Gservices Value Broker Service**<br /> `.ads.GservicesValueBrokerService`
- **Advertising Id Notification Service**<br /> `.ads.identifier.service.AdvertisingIdNotificationService`
- **Advertising Id Service**<br /> `.ads.identifier.service.AdvertisingIdService`

[1]: https://carteryagemann.com/pages/android-intent-firewall.html
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: https://www.myandroidtools.com
[5]: https://github.com/RikkaApps/Shizuku
[6]: ../guide/app-details-page.md
[7]: ../guide/settings-page.md#默认开启-禁用组件-选项
[8]: ../guide/one-click-ops-page.md
[9]: ../guide/settings-page.md#导入现有规则
[10]: ../guide/app-details-page.md#颜色代码含义
[services]: ../guide/app-details-page.md#服务
