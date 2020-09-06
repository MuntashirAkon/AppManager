---
sidebarDepth: 2
---

# Страница «О приложении»
Страница **О приложении** состоит из 11 (одиннадцати) вкладок. В основном она описывает почти каждый бит информации, которую может иметь приложение, включая все атрибуты из манифеста приложения, разрешения, [операции приложения][1], информация о подписи и т. д.

::: details Таблица содержания
[[toc]]
:::

## Цветовые коды
Список фоновых цветов, используемых на этой странице, и их значение:
- <code style="background-color: #FF0000; color: #000">Красный (светлая тема)</code> или <code style="background-color: #790D0D; color: #FFF">темно-красный (темная тема)</code> - любая операция или разрешение приложения с пометкой «Опасно» помечается красным. Компоненты, заблокированные в App Manager, также помечаются красным.
- <code style="background-color: #FF8A80; color: #000">Light red (day)</code> or <code style="background-color: #4F1C14; color: #FFF">very dark red (night)</code> - Components that are disabled outside App Manager have these colors. It should be noted that a component that is marked as disabled does not always mean that it is disabled by the user: It could be disabled by the system as well or marked as disabled in the app manifest. Also, all components of a disabled app are considered disabled by the system (and by App Manager as well)
- <code style="background-color: #FF8017; color: #000">Vivid orange (day)</code> or <code style="background-color: #FF8017; color: #FFF">very dark orange (night)</code> - Tracker or ad components
- <code style="background-color: #EA80FC; color: #000">Soft magenta (day)</code> or <code style="background-color: #431C5D; color: #FFF">very dark violet (night)</code> - Currently running services

## Вкладка «О приложении»
Вкладка **О приложении** содержит общую информацию о приложении. Здесь также перечислены многие действия, которые можно выполнить на этой вкладке. Полное описание приведено ниже:

### Общая информация
Список ниже находится в том же порядке, что и на вкладке «О приложении».
- **Значок приложения.** Значок приложения, если приложение не имеет значка, отображается значок системы по умолчанию.
- **Метка приложения.** Метка приложения или название приложения.
- **Версия.** Версия приложения разделена на две части. The first part is called _version name_, the format of this part varies but it often consists of multiple integers separated by dots. The second part is called _version code_ and it is closed under first brackets. Version code is an integer which is usually used to differentiate between app versions (as version name can often be unreadable by a machine). Как правило, новая версия приложения имеет более высокий код версии, чем старая. For instance, if `123` and `125` are two version codes of an app, we can say that the latter is more updated than the former because the version code of the latter is higher. For applications that depend on platforms (mobile, tabs, desktops, etc.), these version numbers can be misleading as they use prefixes for each platform.
- **Теги (также известные как облака тегов).** Теги включают в себя основную, краткую и наиболее полезную информацию о приложении. Tags contain _tracker info_ (i.e., number of trackers), _app type_ (user app or system app and whether the app is an updated version of the system app), _split apk info_ (i.e., number of splits), _debuggable_ (the app is a debug version), _test only_ (the app is a test only app), _large heap_ (the app has requested a large heap size), _stopped_ (the app is force stopped), _disabled_ (the app is disabled) and _no code_ (the app doesn't have any code associated with it). The importance of including _test only_ and _debuggable_ is that app with these properties can do additional tasks or these apps can be `run-as` without root which can cause potential security problems if these apps store any private information. _large heap_ denotes that the app will be allocated a higher amount of memory (RAM) if needed. While this may not be harmful for most cases, any suspicious apps requesting large heap should be taken seriously.
- **Horizontal Action Panel.** This is a action panel containing various actions regarding the app. See [below](#горизонтаnьная-панеnь-действий) for a complete list of actions available there.
- **Paths & Directories.** Contains various information regarding application paths including _app directory_ (where the apk files are stored), _data directories_ (internal, device protected and externals), _split apk directories_ (along with the split names), and _native JNI library_ (if present). Библиотеки JNI используются для вызова собственных кодов, обычно написанных на языке C/C ++. Использование собственной библиотеки может ускорить работу приложения или помочь приложению использовать сторонние библиотеки, написанные на языках, отличных от Java, как в большинстве игр. Вы также можете открывать эти каталоги с помощью ваших файловых менеджеров (при условии, что они их поддерживают и имеют необходимые разрешения), нажав на значок запуска с правой стороны каждого элемента.
- **Использование данных с момента последнего включения устройства.** Опция, которая не требует пояснений. Но имейте в виду, что из-за некоторых проблем результаты часто могут вводить в заблуждение и быть просто неверными. Эта часть остается скрытой, если не предоставить разрешение _Доступ к использованию_ на новых устройствах.
- **Хранилище и кэш.** Отображает информацию о размере приложения (файлы APK), данных и кэша. В старых устройствах также отображается размер папок с внешними данными, кэшем, медиа и obb. Эта часть остается скрытой, если не представить разрешение _Доступ к использованию_ на новых устройствах.
- **Подробнее.** Отображает прочую информацию, такую ​​как
  * **SDK.** Отображает информацию, относящуюся к Android SDK. There are two (one in old devices) values: _Max_ denotes the target SDK and _Min_ denotes the minimum SDK (the latter is not available in old devices). Лучше всего использовать приложения с максимальным набором SDK, которых в настоящее время поддерживает платформа. SDK также известен как **уровень API**. _Смотрите также: [история версий Android][wiki_android_versions]_
  * **Флаги.** Флаги приложения используются во время создания приложения. Чтобы увидеть полный список флагов и их назначение, ознакомьтесь с [официальной документацией][app_flags].
  * **Дата установки.** Дата, когда приложение было впервые установлено.
  * **Дата обновления.** Дата, когда приложение было последний раз обновлено. Это то же самое, что и _дата установки_, если приложение не обновлялось.
  * **Установщик приложения.** Приложение, установившее приложение. Не все приложения предоставляют информацию, используемую диспетчером пакетов для регистрации приложения-установщика. Следовательно, это значение не следует воспринимать как должное.
  * **Идентификатор пользователя.** Уникальный идентификатор пользователя, установленный системой Android для приложения. For shared applications, same user ID is assigned to multiple applications that have the same _Shared User ID_.
  * **Shared User ID.** Applicable for applications that are shared together. Although it says ID, this is actually a string value. The shared application must have the same [signatures](#вкnадка-«подписи»).
  * **Main Activity.** The main entry point to the app. This is only visible if the app has [activities](#активити) and any of those are openable from the Launcher. There's also launch button on the right-hand side which can be used to launch this activity.

### Горизонтальная панель действий
Горизонтальная панель действий, как описано в предыдущем разделе, состоит из различных действий, связанных с приложением, таких как —
- **Запуск.** Приложение с [активити](#активити), поддерживающего запуск, можно запустить с помощью этой кнопки.
- **Отключить.** Отключение приложения. Эта кнопка не отображается для уже отключенных приложений или для пользователей, у которых нет root-прав или [ADB][2]. If you disable an app, the app will not be displayed in your Launcher app. Shortcuts for the app will also be removed. If you disable an user app, you can only enable them via App Manager or any other tool that supports it. There isn't any option in Android Settings to enable a disabled user app.
- **Uninstall.** Uninstall an app.
- **Включить.** Включение приложение. Эта кнопка не отображается для уже включенных приложений или для пользователей, у которых нет root-прав или [ADB][2].
- **Принудительное закрытие.** Принудительное закрытие приложения. Если вы принудительно остановите приложение, оно не будет работать в фоновом режиме, до того, как вы не откроете его повторно. Однако это не всегда так.
- **Очистка данных.** Очистка данных приложения. В число данных приложения входит любая информация, хранящаяся во внутреннем и внешнем каталоге, включая учетные записи (если они настроены в приложении), кэш и т. д. Например, при очистке данных из App Manager удаляются все правила (однако блокировка не отключается), сохраненные в приложении. Вот почему вы всегда должны делать резервные копии своих правил. Эта кнопка не отображается для пользователей, у которых нет root-прав или [ADB][2].
- **Очистка кэша.** Исключительно очистка кэша приложения. There is not any Android-way to clear app cache. Следовательно, для очистки кэша из внутренней памяти приложения требуется разрешение root-доступа.
- **Установка.** Установка APK, открытого с помощью любого стороннего приложения. Эта кнопка отображается только для внешних файлов APK, которые еще не были установлены.
- **Что нового.** Эта кнопка отображается для файла APK с более высоким кодом версии, чем установленный. При нажатии на эту кнопку отображается диалоговое окно с описанием различий в способах управления версиями. Информация, которую он отображает, включает в себя: _версия_, _трекеры_, _разрешения_, _компоненты_, _подписи_ (изменения контрольной суммы), _функциии_, _общие библиотеки_ и _SDK_.
- **Обновление.** Отображается для файлов APK с более высоким кодом версии, чем установленная.
- **Манифест.** При нажатии на эту показывается манифест файла приложения на отдельной странице. The manifest file can be wrapped or unwrapped using the corresponding toggle button (on the top-right side) or can be saved to you shared storage using the save button.
- **εxodus.** При нажатии на эту кнопку показывается информация о трекерах приложения. Сначала он сканирует приложение, чтобы извлечь список классов. Затем список классов сопоставляется с числом подписей трекинга. После этого сводка сканирования отображается в диалоговом окне предупреждения. If you accidentally close this dialog box, you can see it again using the corresponding option in the menu. If the app has tracker classes, they will be displayed as a list within this page. _See also: [εxodus page][exodus_page]_
- **Shared Prefs.** Clicking on this button displays a list of shared preferences used by the app. Clicking on a preference item in the list opens the [Shared Preferences Editor page][3]. This option is only visible to the root users.
- **Databases.** Clicking on this button displays a list of databases used by the app. This needs more improvements and a database editor which might be added in future. This option is only visible to the root users.
- **Aurora.** Opens the app in _Aurora Droid_. The option is only visible if _Aurora Droid_ is installed.
- **F-Droid.** Opens the app in _F-Droid_. This option is only visible if _F-Droid_ is installed and _Aurora Droid_ is not installed.
- **Store.** Opens the app in _Aurora Store_. The option is only visible if _Aurora Store_ is installed.


### Меню опций
Меню опций находится в правом верхнем углу страницы. Полное описание имеющихся опций приведено ниже:
- **Share.** Share button can be used to share the apk file or _apks_ file (if the app is has multiple splits) can be imported into [SAI][sai]. Вы можете поделиться им с помощью вашего любимого файлового менеджера, чтобы сохранить файл в общем хранилище.
- **Обновить.** Обновляет вкладку «О приложении».
- **Посмотреть в настройках.** Открывает приложение в настройках Android.
- **Резервное копирование/восстановление.** Открывает диалоговое окно резервного копирования/восстановления.
- **Экспорт правил блокировки.** Экспортирует правила, настроенные для этого приложения с помощью App Manager.
- **Открыть в Termux.** Открывает приложение в Termux. This actually runs `su - user_id` where `user_id` denotes the app's kernel user ID (described in the [General Information section](#общая-информация)). Эта опция видна только root-пользователям. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Запустить в Termux.** Открывает приложение, используя команду `run-as package_name` в Termux. Это применимо только для отлаживаемых приложений и также работает для пользователей без root-прав. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Извлечь значок.** Извлечение и сохранение значка приложения в желаемом месте.

### Termux
По умолчанию Termux не позволяет запускать команды из сторонних приложений. Чтобы включить эту опцию, вам нужно добавить `allow-external-apps=true` в <tt>~/.termux/termux.properties</tt> и убедиться, что вы используете Termux версии 0.96 или новее.

::: tip Подсказка
Включение этой опции не ослабляет безопасность вашего Termux. The third-party apps still need to request the user to allow running arbitrary commands in Termux like any other dangerous permissions.
:::

## Вкладка «Компоненты»
**Активити**, **Службы**, **Приемники** (изначально _радиовещательные приемники_) и **Поставщики** (изначально _поставщики контента_) вместе называются компонентами приложения. Это потому, что у них есть много общих черт. Например, у всех есть _имя_ и _метка_. Компоненты приложения являются строительными блоками любого приложения, и большинство из них должны быть объявлены в манифесте приложения. Манифест приложения – это файл, в котором хранятся метаданные приложения. Операционная система Android узнает, что делать с приложением, читая метаданные. [Цвета](#цветовые-коды) используемые в этих вкладках объяснены выше.

::: details Таблица содержания
- [Активити](#активити)
- [Службы](#службы)
- [Приемники](#приемники)
- [Поставщики](#поставщики)
- [Дополнительные функции для телефонов с root-доступом](#additional-features-for-rooted-phones)
:::

### Активити
**Activities** are windows or pages that you can browse (for instance _Main page_ and _App Details page_ are two separate activities). In other words, an activity is a user interface (UI) component. Each activity can have multiple UI components known as _widgets_ or _fragments_, and similarly, each of these latter components can have multiple of them nested or on top of each other. But an activity is a _master_ component: There cannot be two nested activities. An application author can also choose to open external files within an activity using a method called _intent filters_. When you try to open a file using your file manager, either your file manager or system scans for intent filters to decide which activities can open that particular file and offers you to open the file with these activities (therefore, it is nothing to do with the application itself). There are other intent filters as well.

Activities which are _exportable_ can usually be opened by any third-party apps (some activities require permissions, if that is the case, only an app having those permissions can open them). In the _Activities_ tab, the name of the activity (on top of each list item) is actually a button. It is enabled for the _exportable_ activities and disabled for others. You can use this to open the activity directly using App Manager.

::: warning Notice
If you are not able to open any activity, chances are it has certain dependencies which are not met, e.g., you cannot open  _App Details Activity_ because it requires that you at least supply a package name. These dependencies cannot always be inferred programmatically. Therefore, you cannot open them using App Manager.
:::

You can also create shortcut for these _exportable_ activites (using the dedicated button), and if you want, you can edit the shortcut as well using the _Edit Shortcut_ button.

::: danger Caution
If you uninstall App Manager, the shortcuts created by App Manager will be lost.
:::

### Службы
В отличии от [активити](#активити), которых видят пользователи, **службы** обрабатывают фоновые задачи. If you're, for example, downloading a video from the internet using your phone's Internet browser, the Internet browser is using a background service to download the content.

When you close an activity, it usually gets destroyed immediately (depending on many factors such as how much free memory your phone has). But services can be run for indefinite periods if desired. If more services are running in background, you phone will get slower due to shortage of memory and/or processing power, and your phone's battery can be drained more quickly. В более новых версиях Android по умолчанию включена функция оптимизации заряда батареи для всех приложений. Если эта функция включена, система может случайным образом прекратить работу любой службы.

Кстати, и активити, и службы выполняются в одном и том же лупере, который называется основным [лупером][looper], что означает, что службы на самом деле не работают в фоновом режиме. It's the application authors job to ensure that. Как приложение взаимодействует со службами? Они используют [радиовещательные приемники](#приемники).

### Приемники
**Приемники** (также известные как _радиовещательные приемники_) может использоваться для переключения выполнения определенных задач при определенных событиях. Эти компоненты называются широковещательными приемниками, потому что они выполняются сразу после получения широковещательного сообщения. Эти широковещательные сообщения отправляются с использованием метода, называемого намерением. Намерение – это специальная функция Android, которую можно использовать для открытия приложений, активити, служб и отправки широковещательных сообщений. Поэтому, так же, как и [активити](#активити), широковещательные приемники используют фильтры намерений, чтобы получать только желаемые широковещательное сообщения. Широковещательные сообщения могут быть отправлены системой или самим приложением. Когда отправляется широковещательное сообщение, соответствующие приемники активируются системой, чтобы они могли выполнять задачи. Например, если у вас мало памяти, ваш телефон может тормозить или зависать на мгновение после того, как вы включите мобильные данные или подключитесь к Wi-Fi. Вы когда-нибудь задумывались, почему? Это потому, что широковещательные приемники, которые могут принимать `android.net.conn.CONNECTIVITY_CHANGE` просыпаются системой, как только вы включаете передачу данных. Поскольку многие приложения используют этот фильтр намерений, все эти приложения почти сразу же пробуждаются системой, что приводит к зависанию или задержкам. That being said, receivers can be used for inter-process communication (IPC), i.e., it helps you communicate between different apps (provided you have the necessary permissions) or even different components of a single app.

### Поставщики
**Поставщики** (также известные как _поставщики контента_) используются для управления данными. Например, когда вы сохраняете файл APK или правила экспорта в App Manager, оно использует поставщика контента с именем `androidx.core.content.FileProvider` для сохранения файла APK или экспорта правил. There are other content providers or even custom ones to manage various content-related tasks such as database management, tracking, searching, etc. Each content provider has a field called _Authority_ which is unique to that particular app in the entire Android eco-system just like the package name.

### Additional Features for Rooted Phones
Unlike non-root users who are just spectators in these tabs, root users can perform various operations. On the right-most side of each component item, there is a “block” icon (which becomes a “unblock/restore” icon when the component is being blocked). This icon can be used to toggle blocking status of that particular component. If you do not have [Global Component Blocking][settings_gcb] enabled or haven't applied blocking for the app before, you have to apply the changes using the **Apply rules** option in the top-right menu. You can also remove already applied rules using the same option (which would be read as **Remove rules** this time). You also have the ability to sort the component list to display blocked or tracker components on top of the list using the **Sort** option in the same menu. You can also disable all ad and tracker components using the **Block tracker** option in the menu.

_Смотрите также:_
- _[Страница εxodus](./exodus-page.md)_
- _[FAQ: Компоненты приложения][faq_ac]_

## Вкладки разрешений
Вкладки **Операции приложения**, **Используемые разрешения** и **Разрешения** связаны с разрешениями. In Android communication between apps or processes which do not have the same identity (known as _shared id_) often require permission(s). These permissions are managed by the permission controller. Some permissions are considered _normal_ permissions which are granted automatically if they appear in the application manifest, but _dangerous_ and _development_ permissions require confirmation from the user. [Colors](#цветовые-коды) used in these tabs are explained above.

::: details Таблица содержания
- [Операции приложения](#операции-приложений)
- [Используемые разрешения](#используемые-разрешения)
- [Разрешения](#разрешения)
:::

### Операции приложения
**App Ops** stands for **Application Operations**. Начиная с Android 4.3, _операции приложений_ используются системой Android для управления большинством разрешений приложений. Each app op has a unique number associated with them which are closed inside first brackets in the App Ops tab. They also have private and optionally a public name. Some app ops are associated with _permissions_ as well. The dangerousness of an app op is decided based on the associated permission, and other informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#разрешения). Other information may include the following:
- **Mode.** It describes the current authorization status which can be _allow_, _deny_ (a rather misonomer, it simply means error), _ignore_ (it actually means deny), _default_ (inferred from a list of defaults set internally by the vendor), _foreground_ (in newer Androids, it means the app op can only be used when the app is running in foreground), and some custom modes set by the vendors (MIUI uses _ask_ and other modes with just numbers without any associated names).
- **Duration.** The amount of time this app op has been used (there can be negative durations whose use cases are currently not known to me).
- **Accept Time.** The last time the app op was accepted.
- **Reject Time.** The last time the app op was rejected.

::: tip Info
Contents of this tab are only visible to the root and [ADB][2] users.
:::

There is a toggle button next to each app op item which can be used to allow or deny (ignore) the app op. You can also reset your changes using the _Reset to default_ option or deny all dangerous app ops using the corresponding option in the menu. You can also sort them in ascending order by app op names and the associated unique numbers (or values). You can also list the denied app ops first using the corresponding sorting option.

::: warning
Denying an app op may cause the app to misbehave. Use the _reset to default_ option if that is the case.
:::

_See also: [Technical Info: App Ops][1]_

### Используемые разрешения
**Используемые разрешения** – разрешения, используемые приложением. Этот раздел назван так, потому что они объявлены в манифесте с использованием тегов `uses-permission`. Informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#разрешения).

**Root and [ADB][2] users** can grant or revoke the _dangerous_ and _development_ permissions using the toggle button on the right side of each permission item. They can also revoke dangerous permissions all at once using the corresponding option in the menu. Only these two types of permissions can be revoked because Android doesn't allow modifiying _normal_ permissions (which most of them are). The only alternative is to edit the app manifest and remove these permissions from there.

::: tip Info
Since dangerous permissions are revoked by default by the system, revoking all dangerous permissions is the same as resetting all permissions.
:::

Users can sort the permissions by permission name (in ascending order) or choose to display denied or dangerous permissions at first using the corresponding options in the menu.

### Разрешения
**Permissions** are usually custom permissions defined by the app itself. It could contain regular permissions as well, mostly in old applications. Here's a complete description of each item that is displayed there:
- **Name.** Each permission has a unique name like `android.permission.INTERNET` but multiple app can request the permission.
- **Icon.** Each permission can have a custom icon. The other permission tabs do not have any icon because they do not contain any icon in the app manifest.
- **Description.** This optional field describes the permission. Если с разрешением не связано какое-либо описание, поле не отображается.
- **Флаги.** (Используют символ ⚑ или имя **уровня защиты**) Описывают различные флаги разрешений, такие как _обычное_, _разработка_, _опасное_, _мгновенное_, _предоставленное_, _отозванное_, _подпись_, _привилегированное_, и т. д.
- **Имя пакета.** Обозначает имя пакета, связанного с разрешением, т. е пакет, который определил разрешение.
- **Группа.** Имя группы, связанное с разрешением (если есть). Новые Android-устройства, похоже, не используют имена групп, поэтому вы обычно видите имя группы `android.permission-group.UNDEFINED` или вообще не видите.

## Вкладка «Подписи»
**Подписи** на самом деле называется информацией о подписи. Приложение подписывается одним или несколькими сертификатами разработчиками приложения перед его публикацией. The integrity of an application (whether the app is from the actual developer and isn't modified by other people) can be checked using the signing info; because when an app is modified by a third-party unauthorized person, the app cannot be signed using the original certificate(s) again because the signing info are kept private by the actual developer. _Как вы проверяете эти подписи?_ Используя контрольные суммы. Контрольные суммы генерируются из самих сертификатов. Если разработчик предоставляет контрольные суммы, вы можете сопоставить контрольные суммы, используя разные контрольные суммы, сгенерированные во вкладке **Подписи**. For example, if you have downloaded App Manager from Github, Telegram Channel or IzzyOnDroid's repo, you can verify whether the app is actually released by me by simply matching the following _sha256_ checksum with the one displayed in this tab:
```
320c0c0fe8cef873f2b554cb88c837f1512589dcced50c5b25c43c04596760ab
```

Здесь отображаются контрольные суммы трех типов: _md5_, _sha1_ и _sha256_.

::: danger Предупреждение
Рекомендуется проверять информацию о подписи, используя только контрольные суммы _sha256_ или все три из них. НЕ полагайтесь на контрольные суммы _md5_ или _sha1_ только потому, что они, как известно, генерируют одинаковые результаты для нескольких сертификатов.
:::

## Другие вкладки
На других вкладках перечислены компоненты манифеста Android, такие как функции, конфигурации, общие библиотеки и подписи. Полное описание этих вкладок будет скоро доступно.

[1]: ../tech/AppOps.md
[2]: ./adb-over-tcp.md
[3]: ./shared-pref-editor-page.md
[looper]: https://stackoverflow.com/questions/7597742
[settings_gcb]: ./settings.md#global-component-blocking
[faq_ac]: ../faq/app-components.md
[app_flags]: https://developer.android.com/reference/android/content/pm/ApplicationInfo#flags
[wiki_android_versions]: https://en.wikipedia.org/wiki/Android_version_history#Overview
[exodus_page]: ./exodus-page.md
[sai]: https://github.com/Aefyr/SAI
