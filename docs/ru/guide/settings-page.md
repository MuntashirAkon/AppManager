---
next: false
sidebarDepth: 2
---

# Страница настроек
Настройки можно использовать для настройки поведения приложения.

::: details Таблица содержания
[[toc]]
:::

## Тема приложения
Выбор темы приложения

## Режим root
Включение или отключение режима root.

::: tip
To use [ADB][1], root mode has to be disabled at first and then preferably after a relaunch, ADB mode will be detected automatically.
:::

_Смотрите также: [ADB через TCP][1]_

### Глобальная блокировка компонентов
Включение глобальной блокировки компонентов. По умолчанию правила блокировки не применяются, если они не применены на странице [О приложении][2] для любого пакета. После включения этой опции все (старые и новые) правила применяются немедленно для всех приложений без явного включения блокировки для любого приложения.

::: warning Notice
Enabling this setting may have some unintended side-effects, such as rules that are not completely removed will get applied. Поэтому, соблюдайте осторожность. Эту опцию следует оставить отключенной, если она по каким-либо причинам не требуется.
:::

_Смотрите также: [Что такое глобальная блокировка компонентов?][7]_

## Доступ к использованию
Деактивация этой опции отключает страницу **Использование приложений**, а также _использование данных_ и _информация о хранилище приложения_ во вкладке [О приложении][3]. Если отключить эту опцию, App Manager никогда не будет запрашивать разрешение _Доступ к использованию_

## Импорт/экспорт правил блокировки
В App Manager можно импортировать или экспортировать правила блокировки для всех приложений. Вы можете экспортировать или импортировать только определенные правила (компоненты, операции приложений или разрешения) вместо их всех. Также можно импортировать правила блокировки из [Blocker][4] и [Watt][5]. Если необходимо экспортировать правила блокировки для одного приложения, используйте соответствующую страницу [О приложении][2] для экспорта правил, или для нескольких приложений используйте [пакетные операции][6].

_Смотрите также: [App Manager: спецификация правил][rules_spec]_

### Экспорт
Экспортируйте правила блокировки для всех приложений, настроенных в App Manager. Это может включать в себя [компоненты приложения][what_are_components], операции и разрешения приложения на основе того, какие параметры выбраны в параметрах множественного выбора.

### Импорт
Импортируйте ранее экспортированные правила блокировки из App Manager. Similar to export, this may include [app components][what_are_components], app ops and permissions based on what options is/are selected in the multichoice options.

### Импортирование существующих правил
Добавить компоненты, отключенные другими приложениями, в App Manager. App Manager отслеживает только компоненты, отключенные с помощью App Manager. Если вы используете другие инструменты для блокировки компонентов приложения, вы можете использовать эти инструменты для импорта этих отключенных компонентов. Clicking on this option triggers a search for disabled components and will lists apps with components disabled by user. For safety, all the apps are unselected by default. Вы можете вручную выбрать приложения в списке и повторно применить блокировку через App Manager.

::: danger Предупреждение
Будьте осторожны при использовании этого инструмента, так как могут возникнуть негативные последствия. Выбирайте только те приложения, в которых вы уверены.
:::

### Импорт из Watt
Import configuration files from [Watt][5], each file containing rules for a single package and file name being the name of the package with `.xml` extension.

::: tip
Location of configuration files in Watt: <tt>/sdcard/Android/data/com.tuyafeng.watt/files/ifw</tt>
:::

### Импорт из Blocker
Import blocking rules from [Blocker][4], each file containing rules for a single package. These files have a `.json` extension.

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#вкnадка-«о-приnожении»
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#пакетные-операции
[7]: ../faq/app-components.md#что-такое-гnобаnьная-бnокировка-компонентов
[what_are_components]: ../faq/app-components.md#что-такое-компоненты-приnожения
[rules_spec]: ../tech/rules-specification.md
