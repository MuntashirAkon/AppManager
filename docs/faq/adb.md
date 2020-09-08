---
prev: ./app-components
next: ./misc
sidebarDepth: 2
---
# ADB over TCP
In this page, **AoT** denotes **ADB over TCP** and will be used interchangeably.

::: details Table of Contents
[[toc]]
:::

## Do I have to enable ADB over TCP everytime I restart?
Unfortunately, yes. But as of v2.5.13, you don't need to keep AoT enabled all the time as it now uses a server-client mechanism to interact with the system but you do have to keep the **Developer options** as well as **USB debugging** enabled. To do that, enable [ADB over TCP][aot] and open App Manager. You should see _working on ADB mode_ toast message in the bottom. If you see it, you can safely stop the server. For Lineage OS or its derivative OS, you can toggle AoT without any PC or Mac by simply toggling the **ADB over network** option located just below the **USB debugging**.

## Cannot enable USB debugging. What to do?
See [enable USB debugging][aott].

## Can I block trackers or any other application components using ADB over TCP?
Sadly, no. ADB has limited [permissions][adb_perms] and controlling application components is not one of them.

## Which features can be used in ADB mode?
Most of the features supported by ADB mode are enabled by default once ADB support is detected by AM. These include disable, force-stop, clear data, grant/revok app ops and permissions. You can also install applications without any prompt as well as display [running apps/processes][running_apps].

[aot]: ../guide/adb-over-tcp.md
[aott]: ../guide/adb-over-tcp.md#_2-enable-usb-debugging
[adb_perms]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml
[running_apps]: ../guide/main-page.md#running-apps
