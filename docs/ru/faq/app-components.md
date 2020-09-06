---
prev: ./
next: ./misc
sidebarDepth: 2
---

# Компоненты приложения

::: details Оглавление
[[toc]]
:::

## Что такое компоненты приложения?
Действия, службы, широковещательные приемники (также известные как приемники) и поставщики контента (также известные как поставщики) вместе называются компонентами приложения. Технически все они наследуют класс `ComponentInfo`.

## Почему компоненты, заблокированные AM, не обнаруживаются другими связанными приложениями?
Это из-за используемого мной метода блокировки. This method is called [Intent Firewall][1] (IFW) and is compatible with [Watt][2] and [Blocker][3]. [MyAndroidTool][4] (MAT) поддерживает IFW, но использует другой формат. Существуют и другие методы блокировки компонентов приложения, например _pm_ и [Shizuku][5]. Если компонент приложения заблокирован с помощью этих последних методов, уязвимое приложение может идентифицировать его и разблокировать, поскольку оно имеет полный доступ к своим собственным компонентам. Many deceptive apps actually exploit this in order to keep the tracker components unblocked.

## Does app components blocked by other tools retained in AM?
**No.** But components blocked by the Android System or any other tools are displayed in the [App Details][10] page (within the component tabs). Начиная с версии 2.5.12, вы можете импортировать эти правила в [Настройках][9]. Но поскольку нет возможности отличить компоненты, заблокированные сторонними приложениями, от компонентов, заблокированных системой, вы должны быть очень осторожны при выборе приложения.

## Что бывает с компонентами, заблокированными AM, которые также заблокированы другими инструментами?
AM снова блокирует компоненты, используя [Intent Firewall][1] (IFW). Они не разблокируются (если заблокированы с помощью метода _pm_ или [Shizuku][5]) и снова блокируются. But if you unblock a component in the [App Details][6] page, it will be reverted back to default state — blocked or unblocked as described in the corresponding app manifest — using both IFW and _pm_ method. Однако компоненты, заблокированные с помощью [MyAndroidTools][4] (MAT) с методом IFW не будет разблокирован AM. Чтобы решить эту проблему, вы можете сначала импортировать соответствующую конфигурацию в AM в [настройках][9]. В этом случае конфигурации MAT будут удалены. Эта опция доступна только с версии 2.5.12.

## Что такое глобальная блокировка компонентов?
Когда вы блокируете компонент на странице [О приложении][6], по умолчанию блокировка не применяется. It is only applied when you apply blocking using the _Apply rules_ option in the top-right menu. If you enable _global component blocking_, blocking will be applied as soon as you block a component. If you choose to block trackers, however, blocking is applied automatically regardless of this setting. You can also remove blocking for an app by simply clicking on _Remove rules_ in the same menu in the **App Details** page. Since the default behaviour gives you more control over apps, it is better to keep _global component blocking_ option disabled.

_See also: [Global Component Blocking][7]_

## Как разблокировать компоненты трекера, заблокированные с помощью операций в один клик или пакетных операций?
Some apps may misbehave due to their dependency to tracker components blocked by AM. Начиная с версии 2.5.12, есть возможность разблокировать компоненты трекеров на странице [Операции в один клик][8]. However, in previous versions, there is no such options. To unblock these tracker, first go to the [App Details][6] page of the misbehaving app. Then, switching to the _Activities_ tab, click on the _Remove rules_ options in the top-right menu. All the blocking rules related to the components of the app will be removed immediately. Alternatively, If you have found the component that is causing the issue, you can unblock the component by clicking on the _unblock_ button next to the component name. If you have enabled _global component blocking_ in Settings, disable it first as _Remove rules_ option will not be visible when it is enabled.

Если на вашем устройстве установлены **Сервисы Google Play** (`com.google.android.gms`), разблокировка следующих [сервисов][services] может решить проблему:
- **Ad Request Broker Service**<br /> `.ads.AdRequestBrokerService`
- **Cache Broker Service**<br /> `.ads.cache.CacheBrokerService`
- **Gservices Value Broker Service**<br /> `.ads.GservicesValueBrokerService`
- **Advertising Id Notification Service**<br /> `.ads.identifier.service.AdvertisingIdNotificationService`
- **Advertising Id Service**<br /> `.ads.identifier.service.AdvertisingIdService`

[1]: https://carteryagemann.com/pages/android-intent-firewall.html
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: https://www.myandroidtools.com
[4]: https://www.myandroidtools.com
[5]: https://github.com/RikkaApps/Shizuku
[6]: ../guide/app-details-page.md
[6]: ../guide/app-details-page.md
[7]: ../guide/settings-page.md#global-component-blocking
[8]: ../guide/one-click-ops-page.md
[9]: ../guide/settings-page.md#import-existing-rules
[9]: ../guide/settings-page.md#import-existing-rules
[10]: ../guide/app-details-page.md#color-codes
[services]: ../guide/app-details-page.md#services
