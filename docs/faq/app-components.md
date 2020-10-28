---
prev: ./
next: ./adb
sidebarDepth: 2
---
# App Components

::: details Table of Contents
[[toc]]
:::

## What are the app components?
Activities, services, broadcast receivers (also known as receivers) and content providers (also known as providers) are combinedly called app components. More technically, they all inherit the `ComponentInfo` class.

## Why are the components blocked by AM not detected by other related apps?
It is because of the blocking method I'm using. This method is called [Intent Firewall][1] (IFW) and is compatible with [Watt][2] and [Blocker][3]. [MyAndroidTool][4] (MAT) supports IFW but it uses a different format. There are other methods for blocking app components such as _pm_ and [Shizuku][5]. If an app component is blocked using these latter methods, the affected app can identify it and can unblock it as it has full access to its own components. Many deceptive apps actually exploit this in order to keep the tracker components unblocked.

## Does app components blocked by other tools retained in AM?
**No.** But components blocked by the Android System or any other tools are displayed in the [App Details][10] page (within the component tabs). From v2.5.12, you can import these rules in [Settings][9]. But since there is no way to distinguish between components blocked by third-party apps and components blocked by the System, you should be very careful when choosing app.

## What happened to the components blocked by AM which are also blocked by other tools?
AM blocks the components again using [Intent Firewall][1] (IFW). They are not unblocked (if blocked using _pm_ or [Shizuku][5] method) and blocked again. But if you unblock a component in the [App Details][6] page, it will be reverted back to default state — blocked or unblocked as described in the corresponding app manifest — using both IFW and _pm_ method. However, components blocked by [MyAndroidTools][4] (MAT) with IFW method will not be unblocked by AM. To solve this issue, you can first import the corresponding configuration to AM in [Settings][9] in which case MAT's configurations will be removed. But this option is only available from v2.5.12.

## What is global component blocking?
When you block a component in the [App Details][6] page, the blocking is not applied by default. It is only applied when you apply blocking using the _Apply rules_ option in the top-right menu. If you enable _global component blocking_, blocking will be applied as soon as you block a component. If you choose to block tracker components, however, blocking is applied automatically regardless of this setting. You can also remove blocking for an app by simply clicking on _Remove rules_ in the same menu in the **App Details** page. Since the default behaviour gives you more control over apps, it is better to keep _global component blocking_ option disabled.

_See also: [Global Component Blocking][7]_

## Tracker classes versus tracker components
All app components are classes but not all classes are components. In fact, only a few of the classes are components. That being said, [scanner page][scanner] displays a list of trackers along with the number of classes, not just the components. In all other pages, trackers and tracker components are used synonymously to denote tracker components, i.e. blocking tracker means blocking tracker components, not tracker classes.

::: tip Info
Tracker classes cannot be blocked. They can only be removed by editing the app itself.
:::

## How to unblock the tracker components blocked using 1-Click Ops or Batch Ops?
Some apps may misbehave due to their dependency to tracker components blocked by AM. From v2.5.12, there is an option to unblock tracker components in the [1-Click Ops][8] page. However, in previous versions, there is no such options. To unblock these tracker components, first go to the [App Details][6] page of the misbehaving app. Then, switching to the _Activities_ tab, click on the _Remove rules_ options in the top-right menu. All the blocking rules related to the components of the app will be removed immediately. Alternatively, If you have found the component that is causing the issue, you can unblock the component by clicking on the _unblock_ button next to the component name. If you have enabled _global component blocking_ in Settings, disable it first as _Remove rules_ option will not be visible when it is enabled.

If you have **Google Play Services** (`com.google.android.gms`) installed, unblocking the following [services][services] may fix the problem:
- **Ad Request Broker Service**<br />
  `.ads.AdRequestBrokerService`
- **Cache Broker Service**<br />
  `.ads.cache.CacheBrokerService`
- **Gservices Value Broker Service**<br />
  `.ads.GservicesValueBrokerService`
- **Advertising Id Notification Service**<br />
  `.ads.identifier.service.AdvertisingIdNotificationService`
- **Advertising Id Service**<br />
  `.ads.identifier.service.AdvertisingIdService`

[1]: https://carteryagemann.com/pages/android-intent-firewall.html
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: https://www.myandroidtools.com
[5]: https://github.com/RikkaApps/Shizuku
[6]: ../guide/app-details-page.md
[7]: ../guide/settings-page.md#global-component-blocking
[8]: ../guide/one-click-ops-page.md
[9]: ../guide/settings-page.md#import-existing-rules
[10]: ../guide/app-details-page.md#color-codes
[services]: ../guide/app-details-page.md#services
[scanner]: ../guide/scanner-page.md
