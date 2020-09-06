---
sidebarDepth: 2
---

# App Manager: спецификация правил

*Перейти на страницу с [похожей проблемой](https://github.com/MuntashirAkon/AppManager/issues/24) для обсуждения.*

**App Manager** в настоящее время поддерживает активити для блокировки, широковещательные приемники, поставщиков контента, службы, операции приложений и разрешения, и в будущем я могу добавить дополнительные параметры блокировки. Чтобы добавить больше портативности, необходимо импортировать/экспортировать все эти данные.

::: details Таблица содержания
[[toc]]
:::

## Фон
Все файлы конфигурации хранятся в <tt>/data/data/io.github.muntashirakon.AppManager/Files/conf</tt>, а <tt>/sdcard/Android/data/io.github.muntashirakon.AppManager/Files/ifw</tt> используется как временное хранилище. The latter directory is kept to provide compatibility for App Manager v2.5.5 or older as well. This latter directory will be removed in v2.6 as it is not secured to store sensitive data in the shared storage as any app having access to these directories can create or modify these files.

::: tip
From v2.5.6, this latter directory is mostly kept for temporary storage. If you're upgrading from v2.5.5 or older versions, make sure to apply [Global Component Blocking][gcb] which will import all the rules from this directory automatically (you can later disable this option).
:::

Maintaining a database should be the best choice when it comes to storing data. But for now, I'll be using several `tsv` files with each file having the name of the package and a `.tsv` extension. The file/database will be queried/processed by the `RulesStorageManager` class. Due to this abstraction, it should be easier to switch to database or encrypted database systems in future without changing the design of the entire project.

## Формат файла правил

### Внутренняя
The format below is used internally within App Manager and _is not compatible with the external format._
```
<name> <type> <mode>|<component_status>|<is_granted>
```
Где:
- `<name>` - имя операции компонента/разрешения/приложения (в случае операции приложения это может быть строка или целое число)
- `<type>` - один из `АКТИВИТИ`, `ПРИЕМНИКОВ`, `ПОСТАВЩИКОВ`, `СЛУЖБ`, `ОПЕРАЦИЙ ПРИЛОЖЕНИЯ`,  `РАЗРЕШЕНИЙ`
- `<mode>` - (For app ops) The associated [mode constant][mode_constants]
- `<component_status>` - (для компонентов) статус компонента
    * `true` - компонент был применен (значение `true` сохранено для совместимости)
    * `false` - компонент не применен, но будет применяться в будущем (значение `false` сохранено для совместимости)
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
[gcb]: ../guide/settings-page.md#гnобаnьная-бnокировка-компонентов
