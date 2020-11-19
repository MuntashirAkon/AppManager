---
prev: ./app-components
next: ./misc
sidebarDepth: 2
---

# 基于TCP的ADB
在这个页面中， **AoT** 表示 **基于TCP的ADB** 两者将会被交替使用。

::: details 目录
[[toc]]
:::

## 我每次重启时是否需要重新启用ADB over TCP？
是的。 但截至v2.5.13版本, 您不需要保持Aot在所有时间都开启，因为它现在正在使用服务器-客户端机制与系统交互，但您确实必须保持 **开发者选项** 以及 **USB调试模式** 已启用。 要做到这一点，请启用 [基于TCP的ADB][aot] 并打开应用管理器。 您应该在底部看到 _正在使用 ADB 模式_ 的提示消息。 如果你看到它，你可以安全地停止服务器。 对于Lineage OS 或其衍生操作系统， 您可以在没有任何PC或Mac的情况下切换触发器。只需切换位于 **USB 调试** 下面的 **ADB 网络上的** 选项。

## 无法启用 USB 调试。 怎么办？
查看 [启用 USB 调试][aott]。

## 我可以使用基于TCP的ADB来阻止追踪器或任何其他应用程序组件吗？
遗憾的是，不能。 ADB限制了 [个权限][adb_perms] 并且控制应用程序组件不是其中之一。

## 哪些功能可以在ADB模式下使用？
ADB模式支持的大多数功能都是默认启用的，一旦应用管理器检测到ADB支持。 它包括禁用、强制停止、清除数据、授予/撤销app ops权限和permissions权限。 您也可以在没有任何提示的情况下安装应用程序并展示 [运行中的应用程序/进程][running_apps]。

[aot]: ../guide/adb-over-tcp.md
[aott]: ../guide/adb-over-tcp.md#_2-启用usb调试
[adb_perms]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml
[running_apps]: ../guide/main-page.md#正在运行的应用
