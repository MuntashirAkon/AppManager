---
sidebarDepth: 2
---

# 应用详情页
**应用程序详细信息** 页面由11(十一) 标签组成。 它基本上描述了一个应用可以包含应用清单中所有属性的几乎每一点信息， 权限， [应用操作][1]，签名信息等。

::: details 目录
[[toc]]
:::

## 颜色代码含义
此页面使用的背景颜色列表及其意义：
- 停用的应用：<code style="background-color: #FF0000; color: #000">浅红（亮色模式下）</code>或<code style="background-color: #790D0D; color: #FFF">深红（暗色模式下）</code>. 在应用管理器中被阻止的组件也被标记为红色的
- 停用的应用：<code style="background-color: #FF8A80; color: #000">浅红（亮色模式下）</code>或<code style="background-color: #4F1C14; color: #FFF">深红（暗色模式下）</code>. 应该注意的是，被标记为已禁用的组件并不总是意味着用户已禁用它：它可以被系统禁用或在应用清单中被禁用。 此外，被禁用的应用的所有组件都被系统视为禁用(App Manager也被禁用)
- 停用的应用：<code style="background-color: #FF8017; color: #000">浅红（亮色模式下）</code>或<code style="background-color: #FF8017; color: #FFF">深红（暗色模式下）</code>
- <code style="background-color: #EA80FC; color: #000">软洋红（日）</code>或<code style="background-color: #431C5D; color: #FFF">非常深的紫罗兰色（夜）</code> - 当前正在运行的服务。

## 应用详情标签
**应用程序信息** 标签包含有关应用程序的一般信息。 它还列出了可以在这个选项卡中执行的许多行动。 完整的描述如下：

### 基本信息
以下列表与应用信息选项卡中列出的顺序相同。
- **应用程序图标。** 应用程序图标，如果应用程序没有图标，系统默认图标将被显示。
- **应用标签。** 应用程序标签或应用程序名称。
- **版本。** 应用程序版本分为两个部分。 第一部分叫做 _版本名称_， 这个部分的格式各不相同，但它常常由多个整数组成，用点分隔。 第二部分叫做 _版本代码_ ，它在第一个括号中被关闭。 版本代码是一个整数，通常用于区分应用版本(因为版本名称通常无法被机器读取)。 一般来说，新版本的应用程序的版本代码比旧版本更高。 例如，如果 `123` and `125` 是一个应用程序的两个版本代码， 我们可以说，后者比前者更新得更多，因为后者的版本代码更高。 对于依赖平台的应用程序(移动、制表、桌面等等)，这些版本号可能会误导，因为它们对每个平台使用前缀。
- **标签。** (也称为标签云) 标签包含应用程序的基本、简洁和最有用的信息。 标签包含 _Tracker 信息_ (例如) Tracker数目， _应用程序类型_ (用户应用程序或系统应用程序，以及该应用程序是否是系统应用程序的更新版本)， _拆分apk 信息_ (例如) 拆分数量， _可调试_ (应用程序是调试版本)， _只测试_ (应用程序只是测试应用程序)， _大堆_ (应用程序已要求大堆大小), _已停止_ (应用程序已强制停止)， _已禁用_ (该应用已禁用) 和 _无代码_ (该应用没有与此相关联的代码)。 包含_test only_和_debuggable_的重要性在于，具有这些属性的应用程序可以执行额外的任务，或者这些应用程序可以在没有root的情况下`以 的方式运行，如果这些应用程序存储了任何私人信息，则会导致潜在的安全问题。 _大堆_ 表示需要时应用程序将被分配更多的内存 (RAM)。  虽然这对大多数情况来说可能并不有害，但是任何要求大堆的可疑应用都应当受到认真对待。
- **水平动作面板** 这是一个动作面板，包含有关应用的各种动作。 [请参阅下面](#横向操作面板) 查看可用操作的完整列表。
- **路径 & 目录** 包含各种有关应用程序路径的信息，包括 _应用目录_ (apk 文件存储位置)， _数据目录_ (内部, 受保护的和外部存储目录)， _拆分apk 目录_ (与分离名称一起)， 和 _本机的 JNI 库_ (如果存在)。 JNI 库被用于调用本地代码，通常是在 C/C++ 中写的。 使用本机库可以让应用运行更快，或者帮助应用使用在大多数游戏中使用 Java 以外的语言编写的第三对等库。 您也可以通过点击每个项目右侧的启动图标打开这些目录，使用您最喜欢的文件管理器(条件是他们支持它并拥有必要的权限)。
- **自上次启动以来的数据使用情况** 一个相当简单的解释性选项。 但应注意的是，由于某些问题，结果往往会产生误导，纯属错误。 如果更新后的设备未被授予 _使用访问_ 权限，这个部分仍然是隐藏的。
- **存储 & 缓存** 显示关于应用程序大小(apk 文件)、数据和缓存的信息。 在较旧的设备中，还会显示外部数据的大小、缓存、媒体和obb文件夹。 如果更新后的设备未被授予 _使用访问_ 权限，这个部分仍然是隐藏的。
- **更多信息** 显示其他信息，如：
  - **SDK** 显示与 Android SDK 有关的信息。 有两个可用的值(一个用于较旧的设备)： _最大（Max）_ 表示目标SDK 和 _最小（Min）_ 表示最小SDK (较旧的设备无法使用后者)。 最佳做法是使用平台目前支持的最大SDK应用程序。 SDK 也被称为 **API 等级（API Level）**。
      _另见： [Android 版本历史记录][wiki_android_versions]_
  - **标记（Flags）** 构建应用程序时使用的应用程序标记。 要获得完整的标记列表及其用途，请访问 [官方文档][app_flags]。
  - **安装日期** 应用程序首次被安装的日期。
  - **更新日期** 应用程序最后的更新日期。 如果应用程序尚未更新，则与 _日期安装_ 相同。
  - **安装程序** 安装目标程序的应用。 并非所有应用都提供软件包管理器用来注册安装程序的信息。 因此，这个值不应被视为是理所当然的。
  - **用户 ID** Android 系统为应用程序设置的唯一用户ID。 对于共享的应用，相同的用户ID被分配到具有相同的 _共享用户ID_ 的多个应用程序。
  - **共享用户ID** 适用于共用的应用程序。 虽然名为 ID，但这实际上是一个字符串值。 共享应用程序必须具有相同的 [签名](#签名页)。
  - **主活动** 应用程序的主要切入点。 只有当应用程序有 [个活动](#activities) 且其中任一活动都是从启动器中开启时才可见。 右侧还有启动按钮，可用于启动此活动。

### 横向操作面板
Horizontal Action Panel, as described in the previous section, consists of various app-related actions, such as —
- **Launch.** Application that has a launchable [activity](#activities) can be launched using this button.
- **Disable.** Disable an app. This button is not displayed for already disabled apps or to users who do not have root or [ADB][2]. If you disable an app, the app will not be displayed in your Launcher app. Shortcuts for the app will also be removed. If you disable an user app, you can only enable them via App Manager or any other tool that supports it. There isn't any option in Android Settings to enable a disabled user app.
- **Uninstall.** Uninstall an app.
- **Enable.** Enable an app. This button is not displayed for already enabled apps or to users who do not have root or [ADB][2].
- **Force Stop.** Force-stop an app. When you force stop an app, the app will not be able to run in background unless you explicitly open it first. However, this is not always true.
- **Clear Data.** Clear data from an app. This includes any information stored in the internal and often the external directories, including accounts (if set by the app), cache, etc. Clearing data from App Manager, for example, removes all the rules (the blocking is not removed though) saved within the app. Which is why you should always take backups of your rules. This button is not displayed to users who do not have root or [ADB][2].
- **Clear Cache.** Clear app cache only. There is not any Android-way to clear app cache. Therefore, it needs root permission to clear cache from the app's internal storage.
- **Install.** Install an apk opened using any third-party app. This button is only displayed for an external apk that hasn't been installed.
- **What's New.** This button is displayed for an apk that has higher version code than the installed one. Clicking on this button displays a dialog consisting of differences in a version control manner. The information it displays include _version_, _trackers_, _permissions_, _components_, _signatures_ (checksum changes), _features_, _shared libraries_ and _sdk_.
- **Update.** Displayed for an apk that has the higher version code than the installed one.
- **Manifest.** Clicking on this button displays the app's manifest file in a separate page. The manifest file can be wrapped or unwrapped using the corresponding toggle button (on the top-right side) or can be saved to you shared storage using the save button.
- **εxodus.** Clicking on this button displays the app's tracker information. At first, it scans the app to extract a list of classes. Then the class list is matched with a number of tracking signatures. After that, a scan summary is displayed in an alert dialog. If you accidentally close this dialog box, you can see it again using the corresponding option in the menu. If the app has tracker classes, they will be displayed as a list within this page. _See also: [εxodus page][exodus_page]_
- **Shared Prefs.** Clicking on this button displays a list of shared preferences used by the app. Clicking on a preference item in the list opens the [Shared Preferences Editor page][3]. This option is only visible to the root users.
- **Databases.** Clicking on this button displays a list of databases used by the app. This needs more improvements and a database editor which might be added in future. This option is only visible to the root users.
- **Aurora.** Opens the app in _Aurora Droid_. The option is only visible if _Aurora Droid_ is installed.
- **F-Droid.** Opens the app in _F-Droid_. This option is only visible if _F-Droid_ is installed and _Aurora Droid_ is not installed.
- **Store.** Opens the app in _Aurora Store_. The option is only visible if _Aurora Store_ is installed.

### 选项菜单
Options menu is located in the top-right corner of the page. A complete description of the options present there are given below:
- **Share.** Share button can be used to share the apk file or _apks_ file (if the app is has multiple splits) can be imported into [SAI][sai]. You can share it with your favourite file manager to save the file in your shared storage.
- **Refresh.** Refreshes the App Info tab.
- **View in Settings.** Opens the app in Android Settings.
- **Backup/Restore.** Opens the backup/restore dialog.
- **Export Blocking Rules.** Export rules configured for this app within App Manager.
- **Open in Termux.** Opens the app in Termux. This actually runs `su - user_id` where `user_id` denotes the app's kernel user ID (described in the [General Information section](#基本信息)). This option is only visible to the root users. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Run in Termux.** Open the app using `run-as package_name` in Termux. This is only applicable for debuggable app and works for non-root users as well. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Extract Icon.** Extract and save the app's icon in your desired location.

### Termux
By default, Termux does not allow running commands from third-party applications. To enable this option, you have to add `allow-external-apps=true` in <tt>~/.termux/termux.properties</tt> and make sure that you are running Termux v0.96 or later.

::: tip Info
Enabling this option does not weaken your Termux' security. The third-party apps still need to request the user to allow running arbitrary commands in Termux like any other dangerous permissions.
:::

## 组件页
**Activities**, **Services**, **Receivers** (originally _broadcast receivers_) and **Providers** (originally _Content Providers_) are together called the application components. This is because they share similar features in many ways. For example, they all have a _name_ and a _label_. Application components are the building blocks of any application, and most of these have to declared in the application manifest. Application manifest is a file where application specific metadata are stored. The Android operating system learns what to do with an app by reading the metadata. [Colors](#颜色代码含义) used in these tabs are explained above.

::: details Table of Contents
- [Activities](#activities)
- [Services](#服务)
- [Receivers](#接收器)
- [Providers](#内容提供程序)
- [Additional Features for Rooted Phones](#additional-features-for-rooted-phones)
:::

### Activities
**Activities** are windows or pages that you can browse (for instance _Main page_ and _App Details page_ are two separate activities). In other words, an activity is a user interface (UI) component. Each activity can have multiple UI components known as _widgets_ or _fragments_, and similarly, each of these latter components can have multiple of them nested or on top of each other. But an activity is a _master_ component: There cannot be two nested activities. An application author can also choose to open external files within an activity using a method called _intent filters_. When you try to open a file using your file manager, either your file manager or system scans for intent filters to decide which activities can open that particular file and offers you to open the file with these activities (therefore, it is nothing to do with the application itself). There are other intent filters as well.

Activities which are _exportable_ can usually be opened by any third-party apps (some activities require permissions, if that is the case, only an app having those permissions can open them). In the _Activities_ tab, the name of the activity (on top of each list item) is actually a button. It is enabled for the _exportable_ activities and disabled for others. You can use this to open the activity directly using App Manager.

::: warning Notice
If you are not able to open any activity, chances are it has certain dependencies which are not met, e.g., you cannot open  _App Details Activity_ because it requires that you at least supply a package name. These dependencies cannot always be inferred programmatically. Therefore, you cannot open them using App Manager.
:::

You can also create shortcut for these _exportable_ activites (using the dedicated button), and if you want, you can edit the shortcut as well using the _Edit Shortcut_ button.

::: danger Caution
If you uninstall App Manager, the shortcuts created by App Manager will be lost.
:::

### 服务
Unlike [activities](#activities) which users can see, **Services** handle background tasks. If you're, for example, downloading a video from the internet using your phone's Internet browser, the Internet browser is using a background service to download the content.

When you close an activity, it usually gets destroyed immediately (depending on many factors such as how much free memory your phone has). But services can be run for indefinite periods if desired. If more services are running in background, you phone will get slower due to shortage of memory and/or processing power, and your phone's battery can be drained more quickly. Newer Android versions have a battery optimization feature enabled by default for all apps. With this feature enabled, the system can randomly terminate any service.

By the way, both activities and services are run in the same looper called the main [looper][looper], which means the services are not really run in the background. It's the application authors job to ensure that. How do application communicate with services? They use [broadcast receivers](#接收器).

### 接收器
**Receivers** (also called _broadcast receivers_) can be used to trigger execution of certain tasks for certain events. These components are called broadcast receivers because they are executed as soon as a broadcast message is received. These broadcast messages are sent using a method called intent. Intent is a special feature for Android which can be used to open applications, activities, services and send broadcast messages. Therefore, like [activities](#activities), broadcast receivers use intent filters to receive only the desired broadcast message(s). Broadcast messages can be sent by either system or the app itself. When a broadcast message is sent, the corresponding receivers are awaken by the system so that they can execute tasks. For example, if you have low memory, your phone may freeze or experience lags for a moment after you enable mobile data or connect to the Wifi. Ever wondered why? This is because broadcast receivers that can receive `android.net.conn.CONNECTIVITY_CHANGE` are awaken by the system as soon as you enable data connection. Since many apps use this intent filter, all of these apps are awaken almost immediately by the system which causes the freeze or lags. That being said, receivers can be used for inter-process communication (IPC), i.e., it helps you communicate between different apps (provided you have the necessary permissions) or even different components of a single app.

### 内容提供程序
**Providers** (also called _content providers_) are used for data management. For example, when you save an apk file or export rules in App Manager, it uses a content provider called `androidx.core.content.FileProvider` to save the apk or export the rules. There are other content providers or even custom ones to manage various content-related tasks such as database management, tracking, searching, etc. Each content provider has a field called _Authority_ which is unique to that particular app in the entire Android eco-system just like the package name.

### Additional Features for Rooted Phones
Unlike non-root users who are just spectators in these tabs, root users can perform various operations. On the right-most side of each component item, there is a “block” icon (which becomes a “unblock/restore” icon when the component is being blocked). This icon can be used to toggle blocking status of that particular component. If you do not have [Global Component Blocking][settings_gcb] enabled or haven't applied blocking for the app before, you have to apply the changes using the **Apply rules** option in the top-right menu. You can also remove already applied rules using the same option (which would be read as **Remove rules** this time). You also have the ability to sort the component list to display blocked or tracker components on top of the list using the **Sort** option in the same menu. You can also disable all ad and tracker components using the **Block tracker** option in the menu.

_See also:_
- _[εxodus Page](./exodus-page.md)_
- _[FAQ: App Components][faq_ac]_

## 权限页
**App Ops**, **Uses Permissions** and **Permissions** tabs are related to permissions. In Android communication between apps or processes which do not have the same identity (known as _shared id_) often require permission(s). These permissions are managed by the permission controller. Some permissions are considered _normal_ permissions which are granted automatically if they appear in the application manifest, but _dangerous_ and _development_ permissions require confirmation from the user. [Colors](#颜色代码含义) used in these tabs are explained above.

::: details Table of Contents
- [App Ops](#app-ops)
- [Uses Permissions](#使用权限)
- [Permissions](#权限)
:::

### App Ops
**App Ops** stands for **Application Operations**. Since Android 4.3, _App Ops_ are used by Android system to control most of the application permissions. Each app op has a unique number associated with them which are closed inside first brackets in the App Ops tab. They also have private and optionally a public name. Some app ops are associated with _permissions_ as well. The dangerousness of an app op is decided based on the associated permission, and other informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#权限). Other information may include the following:
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

### 使用权限
**Uses Permissions** are the permissions used by the application. This is named so because they are declared in the manifest using `uses-permission` tags. Informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#权限).

**Root and [ADB][2] users** can grant or revoke the _dangerous_ and _development_ permissions using the toggle button on the right side of each permission item. They can also revoke dangerous permissions all at once using the corresponding option in the menu. Only these two types of permissions can be revoked because Android doesn't allow modifiying _normal_ permissions (which most of them are). The only alternative is to edit the app manifest and remove these permissions from there.

::: tip Info
Since dangerous permissions are revoked by default by the system, revoking all dangerous permissions is the same as resetting all permissions.
:::

Users can sort the permissions by permission name (in ascending order) or choose to display denied or dangerous permissions at first using the corresponding options in the menu.

### 权限
**Permissions** are usually custom permissions defined by the app itself. It could contain regular permissions as well, mostly in old applications. Here's a complete description of each item that is displayed there:
- **Name.** Each permission has a unique name like `android.permission.INTERNET` but multiple app can request the permission.
- **Icon.** Each permission can have a custom icon. The other permission tabs do not have any icon because they do not contain any icon in the app manifest.
- **Description.** This optional field describes the permission. If there isn't any description associated with the permission, the field is not displayed.
- **Flags.** (Uses ⚑ symbol or **Protection Level** name) This describes various permission flags such as _normal_, _development_, _dangerous_, _instant_, _granted_, _revoked_, _signature_, _privileged_, etc.
- **Package Name.** Denotes the package name associated with the permission, i.e., the package that defined the permission.
- **Group.** The group name associated with the permission (if any). Newer Androids do not seem to use group names which is why you'll usually see `android.permission-group.UNDEFINED` or no group name at all.

## 签名页
**Signatures** are actually called signing info. An application is signed by one or more singing certificates by the application developers before publishing it. The integrity of an application (whether the app is from the actual developer and isn't modified by other people) can be checked using the signing info; because when an app is modified by a third-party unauthorized person, the app cannot be signed using the original certificate(s) again because the signing info are kept private by the actual developer. _How do you verify these signatures?_ Using checksums. Checksums are generated from the certificates themselves. If the developer supplies the checksums, you can match the checksums using the different checksums generated in the **Signatures** tab. For example, if you have downloaded App Manager from Github, Telegram Channel or IzzyOnDroid's repo, you can verify whether the app is actually released by me by simply matching the following _sha256_ checksum with the one displayed in this tab:
```
320c0c0fe8cef873f2b554cb88c837f1512589dcced50c5b25c43c04596760ab
```

There are three types of checksums displayed there: _md5_, _sha1_ and _sha256_.

::: danger Caution
It is recommended that you verify signing info using only _sha256_ checksums or all three of them. DO NOT rely on _md5_ or _sha1_ checksums only as they are known to generate the same results for multiple certificates.
:::

## 其他
Other tabs list android manifest components such as features, configurations, shared libraries, and signatures. A complete description about these tabs will be available soon.

[1]: ../tech/AppOps.md
[2]: ./adb-over-tcp.md
[3]: ./shared-pref-editor-page.md
[looper]: https://stackoverflow.com/questions/7597742
[settings_gcb]: ./settings.md#默认开启-禁用组件-选项
[faq_ac]: ../faq/app-components.md
[app_flags]: https://developer.android.com/reference/android/content/pm/ApplicationInfo#flags
[wiki_android_versions]: https://en.wikipedia.org/wiki/Android_version_history#Overview
[exodus_page]: ./exodus-page.md
[sai]: https://github.com/Aefyr/SAI
