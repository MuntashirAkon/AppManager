---
next: false
sidebarDepth: 2
---

# Страница «Настройки»
Настройки можно использовать для настройки поведения приложения.

::: details Таблица содержания
[[toc]]
:::

## Language
Configure in-app language. App Manager current supports 12 (twelve) languages.

## Тема приложения
Выбор темы приложения

## Mode of Operation
You can select one of the four options:
- **Auto.** Let AM select the suitable option for you. Although this is the default option, it may cause problems in some devices.
- **Root.** Select root mode. AM will request for root permission if not already granted. But when selected, AM will run in root mode even if you don't allow root. This may cause crashes, therefore, you shouldn't enable it if you haven't granted root.
- **ADB over TCP.** Enable [ADB over TCP][1] mode. AM may hang indefinitely if you haven't enabled ADB over TCP.
- **No-root.** AM runs in no-root mode. AM will perform better if this is enabled but all the root/ADB features will be disabled.

## Доступ к использованию
Отключение этой опции отключает страницу **«Использование приложений»**, а также _использование данных_ и _информация о хранилище приложения_ во вкладке [«О приложении»][3]. Если отключить эту опцию, App Manager никогда не будет запрашивать разрешение _«Доступ к использованию»_

## APK Signing
### Signature schemes
Select the [signature schemes](https://source.android.com/security/apksigning) to use. It is recommended that you use at least v1 and v2 signature schemes. Use the _Reset to Default_ button in case you're confused.

## Rules

### Глобальная блокировка компонентов
Включение глобальной блокировки компонентов. По умолчанию правила блокировки не применяются, если они не применены на странице [«Сведения о приложении»][2] для какого-либо пакета. После включения этой опции, все (старые и новые) правила применяются немедленно для всех приложений без явного включения блокировки для какого-либо приложения.

::: warning Предупреждение
Включение этого параметра может иметь некоторые непредвиденные побочные эффекты, например, будут применяться правила, которые не были полностью удалены. Поэтому, соблюдайте осторожность. Эту опцию следует оставить отключенной, если она по каким-либо причинам не требуется.
:::

_Смотрите также: [Что такое глобальная блокировка компонентов?][7]_

### Импорт/экспорт правил блокировки
В App Manager можно импортировать или экспортировать правила блокировки для всех приложений. Вы можете экспортировать или импортировать только определенные правила (компоненты, операции приложений или разрешения) вместо их всех. Также можно импортировать правила блокировки из [Blocker][4] и [Watt][5]. Если необходимо экспортировать правила блокировки для одного приложения, используйте соответствующую страницу [«Сведения о приложении»][2] для экспорта правил, или для нескольких приложений используйте [пакетные операции][6].

_Смотрите также: [App Manager: спецификация правил][rules_spec]_

#### Экспорт
Экспортируйте правила блокировки для всех приложений, настроенных в App Manager. Они могут включать в себя [компоненты приложения][what_are_components], операции и разрешения приложения на основе того, какие параметры были выбраны в параметрах множественного выбора.

#### Импорт
Импортируйте ранее экспортированные правила блокировки из App Manager. Подобно экспорту, они могут включать в себя [компоненты приложения][what_are_components], операции и разрешения приложения на основе того, какие параметры были выбраны в параметрах множественного выбора.

#### Импортирование существующих правил
Добавляйте компоненты, отключенные другими приложениями, в App Manager. App Manager следит только за компонентами, отключенные с помощью App Manager. Если вы используете другие инструменты для блокировки компонентов приложения, вы можете использовать эти инструменты для импорта этих отключенных компонентов. При нажатии на эту опцию запускается поиск отключенных компонентов и выводится список приложений, компоненты которых отключены пользователем. В целях безопасности, все приложения не выбраны по умолчанию. Вы можете вручную выбрать приложения в списке и повторно применить блокировку через App Manager.

::: danger Предупреждение
Будьте осторожны при использовании этого инструмента, так как могут возникнуть негативные последствия. Выбирайте только те приложения, в которых вы уверены.
:::

#### Импорт из Watt
При импорте файлов конфигурации из [Watt][5], каждый файл содержит правила для одного пакета, а имя файла является именем пакета с расширением `.xml`.

::: tip
Расположение файлов конфигурации в Watt: <tt>/sdcard/Android/data/com.tuyafeng.watt/files/ifw</tt>
:::

#### Импорт из Blocker
При импорте правил блокировки из [Blocker][4], каждый файл содержит правила для одного пакета. Эти файлы имеют расширение `.json`.

### Remove all rules
One-click option to remove all rules configured within App Manager. This will enable all blocked components, app ops will be set to their default values and permissions will be granted.

## Installer
Installer specific options

### Show users in installer
For root/ADB users, a list of users will be displayed before installing the app. The app will be installed only for the specified user (or all users if selected).

### Sign APK
Whether to sign the APK files before installing the app. See [APK signing](#apk-signing) section above to configure signing.

### Install location
Choose APK install location. This can be one of _auto_, _internal only_ and _prefer external_. Depending on Android version, the last option may not always install the app in the external storage.

### Installer App
Select the installer app, useful for some apps which explicitly checks for installer. This only works for root/ADB users.

## Backup/Restore
Settings related to [backup/restore][backup_restore].

### Compression method
Set which compression method to be used during backups. App Manager supports GZip and BZip2 compression methods, GZip being the default compression method. It doesn't affect the restore of an existing backup.

### Backup Options
Customise the backup/restore dialog.

### Backup apps with Android KeyStore
Allow backup of apps that has entries in the Android KeyStore (disabled by default). Some apps (such as Signal) may crash if restored. KeyStore backup also doesn't work from Android 9 but still kept as many apps having KeyStore can be restored without problem.

### Encryption
Set an encryption method for the backups. AM currently supports OpenPGP only.

::: warning Caution
In v2.5.16, App Manager doesn't remember key IDs for a particular backup. You have to remember them yourself. This has been fixed in v2.5.18.
:::

## Device Info
Display Android version, security, CPU, GPU, battery, memory, screen, languages, user info, etc.

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#вкладка-«о-приложении»
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#пакетные-операции
[7]: ../faq/app-components.md#что-такое-глобальная-блокировка-компонентов
[what_are_components]: ../faq/app-components.md#что-такое-компоненты-приложения
[rules_spec]: ../tech/rules-specification.md
[backup_restore]: ./backup-restore.md
