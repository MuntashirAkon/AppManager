---
sidebarDepth: 2
---

# App Manager: Rules Specification

*Go to the [related issue](https://github.com/MuntashirAkon/AppManager/issues/24) for discussion.*

**App Manager** currently supports blocking activities, broadcast receivers, content providers, services, app ops and permissions, and in future I may add more blocking options. In order to add more portability, it is necessary to import/export all these data.

::: details Оглавление
[[toc]]
:::

## Background
All configuration files are stored at <tt>/data/data/io.github.muntashirakon.AppManager/Files/conf</tt>, and <tt>/sdcard/Android/data/io.github.muntashirakon.AppManager/Files/ifw</tt> is used as a temporary storage. The latter directory is kept to provide compatibility for App Manager v2.5.5 or older as well. This latter directory will be removed in v2.6 as it is not secured to store sensitive data in the shared storage as any app having access to these directories can create or modify these files.

::: tip
From v2.5.6, this latter directory is mostly kept for temporary storage. If you're upgrading from v2.5.5 or older versions, make sure to apply [Global Component Blocking][gcb] which will import all the rules from this directory automatically (you can later disable this option).
:::

Maintaining a database should be the best choice when it comes to storing data. But for now, I'll be using several `tsv` files with each file having the name of the package and a `.tsv` extension. The file/database will be queried/processed by the `RulesStorageManager` class. Due to this abstraction, it should be easier to switch to database or encrypted database systems in future without changing the design of the entire project.

## Rules File Format

### Internal
The format below is used internally within App Manager and _is not compatible with the external format._
```
<name> <type> <mode>|<component_status>|<is_granted>
```
Where:
- `<name>` - Component/permission/app op name (in case of app op, it could be string or integer)
- `<type>` - One of the `ACTIVITY`, `RECEIVER`, `PROVIDER`, `SERVICE`, `APP_OP`,  `PERMISSION`
- `<mode>` - (For app ops) The associated [mode constant][mode_constants]
- `<component_status>` - (For components) Component status
    * `true` - Component has been applied (`true` value is kept for compatibility)
    * `false` - Component hasn't been applied yet, but will be applied in future (`false` value is kept for compatibility)
    * `разблокировано` - компонент планируется разблокировать
- `<is_granted>` - показывает, предоставлено ли разрешение или же отозвано

### Внешний
Внешний формат используется для импорта или экспорта правил в App Manager.
```
<package_name> <component_name> <type> <mode>|<component_status>|<is_granted>
```
Этот формат по сути такой же, как и выше, за исключением первого элемента, который является именем пакета.

::: danger Caution
The exprted rules have a different format than the internal one and should not be copied directly to the **conf** folder.
:::

[mode_constants]: ./AppOps.md#mode-constants
[gcb]: ../guide/settings-page.md#global-component-blocking
