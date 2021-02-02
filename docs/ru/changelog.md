---
sidebar: auto
---

# Список изменений

![Отладочная версия](https://github.com/MuntashirAkon/AppManager/workflows/Debug%20Build/badge.svg) [![Релиз GitHub](https://img.shields.io/github/v/release/MuntashirAkon/AppManager)](https://github.com/MuntashirAkon/AppManager/releases/latest) [![F-Droid](https://img.shields.io/f-droid/v/io.github.muntashirakon.AppManager)](https://f-droid.org/packages/io.github.muntashirakon.AppManager) ![Размер репозитория GitHub](https://img.shields.io/github/repo-size/MuntashirAkon/AppManager) ![Коммиты GitHub за неделю](https://img.shields.io/github/commit-activity/w/MuntashirAkon/AppManager)

В настоящее время поддерживаемые версии: [2.5.13](#v2-5-13-348), [2.5.12](#v2-5-12-341), [2.5.11](#v2-5-11-333) и [2.5.10](#v2-5-10-324). Обновите App Manager, если вы пользуетесь более ранней версией.

::: details Таблица содержания
[[toc]]
:::

## v2.5.20 (375)
### Introducing Profiles
[Profiles][profile_page] finally closes the [related issue](https://github.com/MuntashirAkon/AppManager/issues/72). Profiles can be used to execute certain tasks repeatedly without doing everything manually. A profile can be applied (or invoked) either from the [Profiles page][profiles_page] or from the home screen by creating shortcuts. There are also some presets which consist of debloating profiles taken from [Universal Android Debloater](https://gitlab.com/W1nst0n/universal-android-debloater).

**Known limitations:**
- Exporting rules and applying permissions are not currently working.
- Profiles are applied for all users.

### The Interceptor
[Intent Intercept](https://github.com/MuntashirAkon/intent-intercept) works as a man-in-the-middle between source and destination, that is, when you open a file or URL with another app, you can see what is being shared by opening it with Interceptor first. You can also add or modify the intents before sending them to the destination. Additionally, you can double click on any exportable activities in the Activities tab in the App Details page to open them in the Interceptor to add more configurations.

**Known limitation:** Editing extras is not currently possible.

### UnAPKM: DeDRM the APKM files
When I released a small tool called [UnAPKM](https://f-droid.org/en/packages/io.github.muntashirakon.unapkm), I promised that similar feature will be available in App Manager. I am proud to announce that you can open APKM files directly in the App Info page or convert them to APKS or install them directly.

### Multiple user
App manager now supports multiple users! For now, this requires root or ADB. But no-root support is also being considered. If you have multiple users enabled and click on an app installed in multiple profiles, an alert prompt will be displayed where you can select the user.

### Vive la France!
Thanks to the contributors, we have one more addition to the language club: French. You can add more languages or improve existing translations at [Weblate](https://hosted.weblate.org/engage/app-manager).

### Report crashes
If App Manager crashes, you can now easily report the crash from the notifications which opens the share options. Crashes are not reported by App Manager, it only redirects you to your favourite Email client.

### Android 11
Added support for Android 11. Not everything may work as expected though.

### App Installer Improvements
#### Set installation locations
In settings page, you can set install locations such as auto (default), internal only and prefer external.
#### Set APK installer
In settings page, you can also set default APK installer (root/ADB only) instead of App Manager.
#### Multiple users
In settings page, you can allow App Manager to display multiple users during APK installation.
#### Signing APK files
In settings page, you can choose to sign APK files before installing them. You can also select which signature scheme to use in the _APK signing_ option in settings.

**Known limitation:** Currently, only a generic key is used to sign APK files

## v2.5.17 (368)
### App Installer
As promised, it is now possible to select splits. AM also provides recommendations based on device configurations. If the app is already installed, recommendations are provided based on the installed app. It is also possible to downgrade to a lower version without data loss if the device has root or ADB. But it should be noted that not all app can be downgraded. Installer is also improved to speed up the install process, especially, for root users. If the app has already been installed and the new (x)apk(s) is newer or older or the same version with a different signature, AM will display a list of changes similar to [what's new][whats_new] before prompting the user to install the app. This is useful if the app has introduced tracker components, new permissions, etc.

**Known Limitations:**
- Large app can take a long time to fetch app info and therefore it may take a long time display the install prompt.
- If the apk is not located in the internal storage, the app has to be cached first which might also take a long time depending on the size of the apk.

### Scanner: Replacement for Exodus Page
exodus page is now replaced with scanner page. [Scanner page][scanner] contains not only a list of trackers but also a list of used libraries. This is just a start. In future, this page will contain more in depth analysis of the app.

### Introducing System Config
System Config lists various system configurations and whitelists/blacklists included in Android by either OEM/vendor, AOSP or even some Magisk modules. Root users can access this option from the overflow menu in the main page. There isn't any official documentation for these options therefore it's difficult to write a complete documentation for this page. But I will gradually add documentations using my own knowledge. But some of the functions should be understandable by their name.

### More Languages
Thanks to the contributors, AM now has more than 12 languages. New languages include Bengali, Hindi, Norwegian, Polish, Russian, Simplified Chinese, Turkish and Ukrainian. You can add more languages or improve existing translations at [Weblate](https://hosted.weblate.org/engage/app-manager).

### App Info Tab
More tags are added in the [app info tab][app_info] such as **KeyStore** (apps with KeyStore items), **Systemless app** (apps installed via Magisk), **Running** (apps that are running). For external apk, two more options are added namely **Reinstall** and **Downgrade**. Now it is possible to share an apk via Bluetooth. For system apps, it is possible to uninstall updates for root/ADB users. But like the similar option in the system settings, this operation will clear all app data. As stated above, exodus has been replaced with scanner.

### Navigation Improvements
It's now relatively easy to navigate to various UI components just by using keyboard. You can use up/down button to navigate between list items and tab button to navigate to UI components inside an item.

### Running Apps Page
It is now possible to sort and filter processes in this tab. Also the three big buttons are replaced with an easy to use three dot menu. Previously the memory usage was wrong which is fixed in this version.

### Built-in Toybox
Toybox (an alternative to busybox) is bundled with AM. Although Android has this utility built-in from API 23, toybox is bundled in order to prevent buggy implementations and to support API < 23.

### Component Blocker Improvements
Component blocker seemed to be problematic in the previous version, especially when global component blocking is enabled. The issues are mostly fixed now.

::: warning Caution
The component blocking mechanism is no longer compatible with v2.5.6 due to various security issues. If you have this version, upgrade to v2.5.13 or earlier versions first. After that enable [global component blocking][5] and disable it again.
:::

### Improvements in the App Details Page
Value of various app ops depend on their parent app ops. Therefore, when you allow/deny an app op, the parent of the app op gets modified. This fixes the issues some users have been complaining regarding some app ops that couldn't be changed.

If an app has the target API 23 or less, its permissions cannot be modified using the `pm grant ...` command. Therefore, for such apps, option to toggle permission has been disabled.

The signature tab is improved to support localization. It also displays multiple checksums for a signature.

### App Manifest
Manifest no longer crashes if the size of the manifest is too long. Generated manifest are now more accurate than before.

## v2.5.13 (348)
### Пакетное приложение (Split APK)
Добавлена поддержка форматов пакетных приложений, таких как **APKS** и **XAPK**. Вы можете установить эти приложения с помощью обычных кнопок установки. Для пользователей root и ADB приложения устанавливаются с использованием метода оболочки, а для пользователей без root-прав используется метод платформы по умолчанию.

**Известные ограничения:**
- _Все_ части APKS установлены. Но это поведение изменится в следующей версии приложения. Если вам нужно всего несколько частей вместо всех, извлеките файл **APKS** или **XAPK**, а затем создайте новый ZIP-файл с желаемыми частями APKS и замените расширение **ZIP** на **APKS**. Теперь откройте его с помощью AM.
- Нет диалогового окна прогресса для отображения хода установки.

### Поддержка прямой установки
Теперь вы можете установить файлы **APK**, **APKS** или **XAPK** непосредственно из вашего любимого браузера или файлового менеджера. Для приложений, которым необходимы обновления, отображается диалоговое окно **«Что нового»**, показывающее изменения в новой версии.

**Известные ограничения:**
- Понижение версии пока что недоступно.
- Нет диалогового окна прогресса для отображения хода установки. Если вы не можете взаимодействовать с текущей страницей, дождитесь завершения установки.

### Удаление всех правил блокировки
На странице настроек добавлен новый параметр, который можно использовать для удаления всех правил блокировки, настроенных в App Manager.

### Операции приложения
- Операции приложения теперь генерируются с использованием техники, аналогичной AppOpsX. Это должно значительно сократить время загрузки вкладки «Операции приложения».
- На вкладке «Операции приложения» добавлен пункт меню, который можно использовать для вывода списка только активных операций приложения без включения операций приложения по умолчанию. Эти параметры сохраняются в общих настройках.

**Известные ограничения:** часто вкладка «Операции приложения» может не реагировать на нажатия. В этом случае перезапустите AM.

### Расширенная поддержка ADB
Команды оболочки ADB теперь выполняются с использованием техники, аналогичной AppOpsX (это _свободная_ альтернатива Shizuku). Это должно значительно увеличить время выполнения.

**Известные ограничения:** AM часто может давать сбой или перестать реагировать. В этом случае перезапустите AM.

### Фильтрация на главной странице
Добавлена опция для фильтрации приложений, у которых есть хотя бы одно активити.

### Резервное копирование/отправка APK
Файлы APK теперь сохраняются как `app name_version.extension` вместо `package.name.extension`.

### Пакетные операции
- Добавлен служба переднего плана для выполнения пакетных операций. Результат операции отображается в уведомлении. Если операция не удалась для некоторых пакетов, нажатие на уведомление откроет диалоговое окно со списком неудачных пакетов. Также есть кнопка **«Повторить попытку»** внизу, которую можно использовать для повторного выполнения операции с ошибочными пакетами.
- Замена Linux _kill_ на **force-stop**.

### Переводы
Добавлены переводы на немецкий и португальский (бразильский) языки.

**Известные ограничения:** еще не все переводы проверены.

### Резервное копирование данных приложений
Установка приложения только для текущего пользователя во время восстановления резервных копий. Также добавлена ​​поддержка разделенных APKS.

_Опция резервного копирования данных в данный момент считается нестабильной. Если у вас возникнут какие-либо проблемы, пожалуйста, сообщите о них без колебаний._


## v2.5.12 (341)
- <span><tagfeature/></span>  Добавлена поддержка разделения резервных копий данных на файлы размером 1 ГБ для обхода ограничения файловой системы FAT32
- <span><tagfeature/></span>  Добавлена возможность разблокировать трекеры
- <span><tagfeature/></span>  Добавлена опция пропуска проверки подписи при восстановлении резервных копий
- <span><tagfeature/></span>  Поддержка [Termux][termux]: <code>run-as</code> отладка приложения или запуск нового сеанса в виде приложения во вкладке [«О приложении»][app_info]
- <span><tagfeature/></span>  Отображание информации о резервной копии приложения на [главной странице][main_page]
- <span><tagfeature/></span>  Восстановление исходных файлов (за исключением файлов APK) отключено на неподдерживаемых архитектурах
- <span><tagfeature/></span>  Отображение диалогового окна подтверждения операции перед очисткой данных приложения
- <span><tagfeature/></span>  Возможность импорта компонентов отключена с помощью IFW на MAT
- <span><tagfeature/></span>  Включение внешнего носителя и каталога OBB для резервного копирования
- <span><tagfeature/></span>  Разрешение импорта существующих правил другими приложениями или инструментами
- <span><tagfeature/></span>  Добавлена опция для извлечения значков приложений во вкладке [«О приложении»][app_info]
- <span><tagfix/></span> Отображание функции восстановления и удаления резервных копий только для приложений с существующими резервными копиями
- <span><tagfix/></span> Отображание индикатора прогресса во время создания резервной копии
- <span><tagfix/></span> Отображение индикатора прогресса во время загрузки операций приложения
- <span><tagfix/></span> Исправлено неоткрытие приложения в последней и единственной поддерживаемой версии Aurora Droid (версия 1.0.6)
- <span><tagfix/></span> Исправлен сбой приложения при смене ночного режима во время просмотра страницы [«О приложении»][1]
- <span><tagfix/></span> Исправлен сбой приложения при попытке открыть внешний файл APK
- <span><tagfix/></span> Исправлена ошибка NullPointerException, когда внешний каталог данных имел значение NULL
- <span><tagfix/></span> Исправлено отображение панели инструментов в полноэкранном диалоге
- <span><tagfix/></span> Исправлен поиск без учета регистра
- <span><tagfix/></span> Оптимизация темы приложения
- <span><tagfix/></span> Замена AndroidShell на LibSuperuser
- <span><tagfix/></span> апрос разрешения доступа к внешнему хранилищу при сохранении файлов APK
- <span><tagfix/></span> Обход ошибки AppBarLayout в Material Design
- <span><tagfix/></span> Обновление внешней информации об APK при установке/удалении событий

Чтобы использовать возможности Termux, убедитесь, что вы используете Termux версии 0.96 или новее, а команда `allow-external-apps=true` добавлена в <tt>~/.termux/termux.properties</tt>.

Функция резервного копирования данных по-прежнему является экспериментальной, и поэтому, пожалуйста, не полагайтесь на нее при управлении своими резервными копиями.

## v2.5.11 (333)
- <span><tagfeature/></span>  Добавлена экспериментальная поддержка резервного копирования данных приложений. Пожалуйста, тестируйте только те приложения, которые вам не нужны. (только в режиме root)
- <span><tagfeature/></span>  Добавлена возможность отправки разделенных файлов APK в формате APKS (можно установить через [SAI][8]).
- <span><tagfeature/></span>  Реализовано сохранение файлов APK в режиме пакетного выбора.
- <span><tagfeature/></span>  Добавлен список изменений для файла APK, который нуждается в обновлении (при открытии внешних файлов APK).
- <span><tagfeature/></span>  Добавлена возможность применения операций в один клик к системным приложениям (по умолчанию отключено).
- <span><tagfeature/></span>  Добавлена отображение информации о версии установленного приложения во вкладке «О приложении». Нажатие на значок _i_ открывает вкладку [«О приложении»][app_info].
- <span><tagfeature/></span>  Новые разрешения по запросу <tt>READ_EXTERNAL_STORAGE</tt> и <tt>WRITE_EXTERNAL_STORAGE</tt> для поддержки резервного копирования приложений
- <span><tagfeature/></span>  Отображение удаленных приложений, у которых есть резервные копии, на главной странице
- <span><tagfeature/></span>  Добавлен отказ от ответственности
- <span><tagfix/></span> Исправлено неочищение выбора после выполнения задачи на главной странице
- <span><tagfix/></span> Преобразование различной информации во вкладке конфигураций и функций в текст для улучшения читаемости.
- <span><tagfix/></span> Исправлен сбой на [главной странице][main_page] при фильтрации приложений по поисковому запросу
- <span><tagfix/></span> Исправлена сбой во вкладке [«О приложении»][app_info] когда наличие каталога внешних данных давало ложноположительный результат

**Примечание:** резервные копии данных хранятся в <tt>/sdcard/AppManager</tt>, а резервные копии файлов APK хранятся в <tt>/sdcard/AppManager/apks</tt>. Резервное копирование данных в настоящее время не работает на Android Lollipop.

## v2.5.10 (324)
- <span><tagfeature/></span>  Добавлены операции в один клик (опция [«Операции в один клик»](./guide/one-click-ops-page.md) на [главной странице][main_page]): блокировка трекеров, блокировка компонентов по подписи, блокировка операций приложения
- <span><tagfeature/></span>  Добавлена поддержка внешних файлов APK: теперь вы можете открывать файлы APK из вашего файлового менеджера. Вы можете просматривать сведения о приложении, манифест или сканировать трекеры прямо из него
- <span><tagfeature/></span>  Добавлена опция постоянной фильтрации приложений на [главной странице][main_page].
- <span><tagfeature/></span>  Альтернативный просмотрщик манифестов для установленных файлов APKS
- <span><tagfeature/></span>  Отображение количества трекеров в виде тега во вкладке [«О приложении»][app_info]
- <span><tagfeature/></span>  Добавлена опция выбора всех приложений на [главной странице][main_page] в режиме выделения
- <span><tagfeature/></span>  Добавлены ссылки на исходный код и сообщество
- <span><tagfeature/></span>  Добавлена поддержка установки/обновления файлов APK во вкладке [«О приложении»][app_info] (бета)
- <span><tagfeature/></span>  Добавлена возможность импортировать существующие отключенные компоненты в настройках импорта/экспорта (бета)
- <span><tagfeature/></span>  Добавлено отображение информации о разделенных файлах APK во вкладке [«О приложении»][app_info]
- <span><tagfeature/></span>  Добавлена возможность открыть [Termux](./guide/main-page.md#termux) на [главной странице][main_page] (бета)
- <span><tagfeature/></span>  Начальная поддержка баннера приложения
- <span><tagfix/></span> Исправлено несоответствие включения и выключения во вкладке «О приложении»
- <span><tagfix/></span> Исправлена проблема с постоянным кэшем приложения
- <span><tagfix/></span> Исправлена проблема с прокруткой на странице настроек
- <span><tagfix/></span> Исправлены сбои при переходе на вкладку компонентов для пользователей без root-прав
- <span><tagfix/></span> Исправлен сбой при попытке просмотреть сводку во время сканирования на странице exodus
- <span><tagfix/></span> Исправлены сбои на устройствах, не поддерживающих отображение статистики использования данных
- <span><tagfix/></span> Исправлен сбой при попытке просмотреть манифест разделенного файла APK
- <span><tagfix/></span> Исправлено неправильное отображение имени установщика пакета во вкладке [«О приложении»][app_info]
- <span><tagfix/></span> Исправлено форматирование списка изменений для старых устройств

## v2.5.9 (315)
- <span><tagfeature/></span>  Слияние [«О приложении»][app_info] в виде одной вкладки на странице [«Сведения о приложении»][1]
- <span><tagfeature/></span>  Добавлена опция сброса всех операций приложения
- <span><tagfeature/></span>  Добавлена опция отзыва всех опасных операций/разрешений приложения
- <span><tagfeature/></span>  Выделение трекеров во вкладке компонентов
- <span><tagfeature/></span>  Добавлена опция сохранения манифеста и дампа класса
- <span><tagfeature/></span>  Добавлена возможность предоставлять/отзывать разрешения на разработку
- <span><tagfeature/></span>  Добавлены параметры сортировки для вкладок компонентов, операций приложения и используемых разрешений.
- <span><tagfeature/></span>  Добавлена сортировка приложений по использованию Wi-Fi на странице [«Использование приложений»][6]
- <span><tagfeature/></span>  Добавлена кнопка запуска во вкладке [«О приложении»][app_info]
- <span><tagfeature/></span>  Добавлена опция «Никогда не спрашивать» в диалоговом окне
- <span><tagfeature/></span>  Добавлено долгое нажатие для выбора приложений на [главной странице][main_page].
- <span><tagfeature/></span>  Добавлен список изменений в приложении
- <span><tagfix/></span> Нажатие для выбора приложений в режиме выделения
- <span><tagfix/></span> Улучшен блокировщик компонентов
- <span><tagfix/></span> Улучшена загрузка манифеста для больших приложений
- <span><tagfix/></span> Улучшена скорость загрузки вкладок
- <span><tagfix/></span> Исправлена проверка операций приложений и пользовательских операций приложений для некоторых устройств
- <span><tagfix/></span> Отключено открытие активити для отключенных активити
- <span><tagfix/></span> Загрузка настоящего имени активити для активити, которые используют псевдонимы
- <span><tagfix/></span> Исправлены фоновые цвета
- <span><tagfix/></span> Исправлен сбой при загрузке вкладки служб у пользователей без root-прав
- <span><tagfix/></span> Исправлена неработающая кнопка возврата во время просмотра классов
- <span><tagfix/></span> Изменены цвета значков блоков на цвет акцента.
- <span><tagfix/></span> Удалены переводы до релиза стабильной версии приложения
- <span><tagfix/></span> Кликабельные ссылки в разделе «О приложении»
- <span><tagfix/></span> Исправлены различные утечки памяти.

## v2.5.8 (289)
- <span><tagfeature/></span>  Добавлена [возможность импорта/экспорта правил блокировки](./guide/settings-page.md#импортирование-существующих-правиn).
- <span><tagfeature/></span>  Добавлена возможность [выбора тем](./guide/settings-page.html#тема-приnожения) (светлая/ночная)
- <span><tagfeature/></span>  Добавлен режим, длительность, время подтверждения, время отклонения для операций приложений
- <span><tagfeature/></span>  Выделение работающих служб
- <span><tagfeature/></span>  Выделение компонентов, отключенных не с помощью App Manager
- <span><tagfeature/></span>  Добавлен жест обновления на странице [«Использование приложений»][app_usage]
- <span><tagfeature/></span>  Добавлено отображение процентного соотношения использования экрана с индикатором
- <span><tagfeature/></span>  Разделение страниц «Инструкции» и «О приложении» с полноэкранным диалогом для обеих страниц
- <span><tagfeature/></span>  Закругленное меню переполнения (бета)
- <span><tagfix/></span> Исправлены различные проблемы с операциями приложений для конкретных устройств/SDK
- <span><tagfix/></span> Улучшена стабильность всех приложений
- <span><tagfix/></span> Добавлено разрешение <tt>ACCESS_NETWORK_STATE</tt> для поддержки старых версий операционной системы
- <span><tagfix/></span> Исправлено удаление всех правил IFW при выборе [глобальной блокировки компонентов][5].
- <span><tagfix/></span> Исправлены различные проблемы с поиском

## v2.5.7 (265)
- <span><tagfeature/></span>  Первоначальная поддержка [ADB через TCP](./guide/adb-over-tcp.md) (порт 5555) для пользователей без root-прав
- <span><tagfix/></span> Исправлены правила импорта из [Watt][2] и [Blocker][3].
- <span><tagfix/></span> Отображение приложения Aurora Droid на странице [«О приложении»][app_info] как приоритетное над F-Droid
- <span><tagfix/></span> Улучшена скорость блокировки компонентов
- <span><tagfix/></span> Исправлена проблема с определением режима операции приложения

**Для root-пользователей:** если вы пропустили версию [v2.5.6](#v2-5-6-233), вам может потребоваться применить все правила глобально, применив [глобальную блокировку компонентов][5] в настройках приложения, чтобы они работали корректно

## v2.5.6 (233)
- <span><tagfeature/></span>  [Пакетные операции](./guide/main-page.md#пакетные-операции) на главной странице: очистка данных приложения, отключение запуска в фоновом режиме, отключение/закрытие/удаление приложения (нажмите на значок приложения для выбора)
- <span><tagfeature/></span>  Полная поддержка экспортированных и поврежденных файлов с помощью [Blocker][3].
- <span><tagfeature/></span>  Повторная реализация блокирующих активити, приемников, служб и поставщиков
- <span><tagfix/></span> Удалена зависимость ConstraintLayout, поэтому возможно уменьшение размера приложения
- <span><tagfix/></span> Исправлено предупреждение о дублировании использования приложения на странице [«О приложении»][app_info].
- <span><tagfix/></span> Исправлен сбой при ненахождении значка приложения на странице [«Сведения о приложении»][1].

**Примечание для пользователей root:** чтобы гарантировать, что предыдущие правила блокировки сохраняются с новой реализацией блокировки, это обновление основано на предыдущих правилах, следовательно, увеличивая время загрузки на [главной странице][main_page]. Эта функция будет удалена в следующей версии приложения, но ее все еще можно будет симулировать, применив [глобальную блокировку компонентов][5] в настройках приложения.

## v2.5.5 (215)
- <span><tagfeature/></span>  Добавлен [просмотрщик работающих приложений/процессов](./guide/main-page.md#работающие-приложения) (требует root-прав)
- <span><tagfeature/></span>  Добавлен [просмотрщик деталей использования приложений][6]
- <span><tagfeature/></span>  Добавлена поддержка [Apk Updater](./guide/main-page.md#apk-updater) и [Aurora Store](./guide/app-details-page.md#горизонтаnьная-панеnь-действий)
- <span><tagfeature/></span>  Сохранение измененных значений операций и разрешений приложений в хранилище устройства (в стадии разработки)
- <span><tagfix/></span> Удаление поддержки для пользователей без root-прав
- <span><tagfix/></span> Реструктуризация страницы использования приложений
- <span><tagfix/></span> Добавлено больше ясности, а также улучшена производительность на странице [«Сведения о приложении»][1]

[profile_page]: ./guide/profile-page.md
[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[profiles_page]: ./guide/profiles-page.md
[scanner]: ./guide/scanner-page.md
[3]: https://github.com/lihenggui/blocker
[whats_new]: ./guide/app-details-page.md#горизонтальная-панель-деиствии
[app_info]: ./guide/app-details-page.md#вкладка-«о-приложении»
[5]: ./guide/settings-page.md#гnобаnьная-бnокировка-компонентов
[app_usage]: ./guide/main-page.md#испоnьзование-приnожений
[main_page]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
[termux]: https://github.com/termux/termux-app
