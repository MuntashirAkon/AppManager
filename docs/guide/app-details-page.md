---
sidebarDepth: 2
---
# App Details Page 
**App Details** page consists of 11 (eleven) tabs. It basically describes almost every bit of information an app can have including all attributes from app manifest, permissions, [app operations][1], signing info, etc.

::: details Table of Contents
[[toc]]
:::

## Color Codes
List of background colors used in this page and their meaning:
- <code style="background-color: #FF0000; color: #000">Red (day)</code> or <code style="background-color: #790D0D; color: #FFF">dark red (night)</code> - Any app op or permission that has the dangerous flag is marked as red. Components that are blocked within App Manager are also marked as red
- <code style="background-color: #FF8A80; color: #000">Light red (day)</code> or <code style="background-color: #4F1C14; color: #FFF">very dark red (night)</code> - Components that are disabled outside App Manager have these colors. It should be noted that a component that is marked as disabled does not always mean that it is disabled by the user: It could be disabled by the system as well or marked as disabled in the app manifest. Also, all components of a disabled app are considered disabled by the system (and by App Manager as well)
- <code style="background-color: #FF8017; color: #000">Vivid orange (day)</code> or <code style="background-color: #FF8017; color: #FFF">very dark orange (night)</code> - Ad or tracker components
- <code style="background-color: #EA80FC; color: #000">Soft magenta (day)</code> or <code style="background-color: #431C5D; color: #FFF">very dark violet (night)</code> - Currently running services

## App Info Tab
**App Info** tab contains general information about an app. It also lists many actions that can be performed within this tab. A complete description is given below:

### General Information
The list below is in the same order as listed in the App Info tab.
- **App Icon.** The application icon, if an app doesn't have a icon, the system default icon is displayed.
- **App Label.** The application label or application name.
- **Version.** Application version is divided into two parts. The first part is called _version name_, the format of this part varies but it often consists of multiple integers separated by dots. The second part is called _version code_ and it is closed under first brackets. Version code is an integer which is usually used to differentiate between app versions (as version name can often be unreadable by a machine). In general, new version of an app has higher version code than the old ones. For instance, if `123` and `125` are two version codes of an app, we can say that the latter is more updated than the former because the version code of the latter is higher. For applications that depend on platforms (mobile, tabs, desktops, etc.), these version numbers can be misleading as they use prefixes for each platform.
- **Tags.** (Also known as tag clouds) Tags include the basic, concise and most useful info of an app. Tags contain _tracker info_ (i.e., number of tracker components), _app type_ (user app or system app and whether the app is an updated version of the system app), _split apk info_ (i.e., number of splits), _debuggable_ (the app is a debug version), _test only_ (the app is a test only app), _large heap_ (the app has requested a large heap size), _stopped_ (the app is force stopped), _disabled_ (the app is disabled) and _no code_ (the app doesn't have any code associated with it). The importance of including _test only_ and _debuggable_ is that app with these properties can do additional tasks or these apps can be `run-as` without root which can cause potential security problems if these apps store any private information. _large heap_ denotes that the app will be allocated a higher amount of memory (RAM) if needed. While this may not be harmful for most cases, any suspicious apps requesting large heap should be taken seriously.
- **Horizontal Action Panel.** This is a action panel containing various actions regarding the app. See [below](#horizontal-action-panel) for a complete list of actions available there.
- **Paths & Directories.** Contains various information regarding application paths including _app directory_ (where the apk files are stored), _data directories_ (internal, device protected and externals), _split apk directories_ (along with the split names), and _native JNI library_ (if present). JNI libraries are used to invoke native codes usually written in C/C++. Use of native library can make the app run faster or help an app use third-pary libraries written using languages other than Java like in most games. You can also open these directories using your favourite file managers (provided they support it and have necessary permissions) by clicking on the launch icon on the right-hand side of each item.
- **Data Usage Since Last Boot.** A rather self explanatory option. But beware that due to some issues, the results might often be misleading and simply wrong. This part remains hidden if _Usage Access_ permission is not granted in newer devices.
- **Storage & Cache.** Displays information regarding the size of the app (apk files), data and cache. In older devices, size of external data, cache, media and obb folders are also displayed. This part remains hidden if _Usage Access_ permission is not granted in newer devices.
- **More Info.** Displays other information such as
  * **SDK.** Displays information related to the Android SDK. There are two (one in old devices) values: _Max_ denotes the target SDK and _Min_ denotes the minimum SDK (the latter is not available in old devices). It is best practice to use apps with maximum SDK that the platform currently supports. SDK is also known as **API Level**.
    _See also: [Android Version History][wiki_android_versions]_
  * **Flags.** The application flags used at the time of building the app. For a complete list of flags and what they do, visit the [official documentation][app_flags].
  * **Date Installed.** The date when the app was first installed.
  * **Date Updated.** The date when the app was last updated. This is the same as _Date Installed_ if the app hasn't been updated.
  * **Installer App.** The app that installed the app. Not all app supply the information used by the package manager to register the installer app. Therefore, this value should not be take for granted.
  * **User ID.** The unique user ID set by the Android system to the app. For shared applications, same user ID is assigned to multiple applications that have the same _Shared User ID_.
  * **Shared User ID.** Applicable for applications that are shared together. Although it says ID, this is actually a string value. The shared application must have the same [signatures](#signatures-tab).
  * **Main Activity.** The main entry point to the app. This is only visible if the app has [activities](#activities) and any of those are openable from the Launcher. There's also launch button on the right-hand side which can be used to launch this activity.

### Horizontal Action Panel
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


### Options Menu
Options menu is located in the top-right corner of the page. A complete description of the options present there are given below:
- **Share.** Share button can be used to share the apk file or _apks_ file (if the app is has multiple splits) can be imported into [SAI][sai]. You can share it with your favourite file manager to save the file in your shared storage.
- **Refresh.** Refreshes the App Info tab.
- **View in Settings.** Opens the app in Android Settings.
- **Backup/Restore.** Opens the backup/restore dialog.
- **Export Blocking Rules.** Export rules configured for this app within App Manager.
- **Open in Termux.** Opens the app in Termux. This actually runs `su - user_id` where `user_id` denotes the app's kernel user ID (described in the [General Information section](#general-information)). This option is only visible to the root users. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Run in Termux.** Open the app using `run-as package_name` in Termux. This is only applicable for debuggable app and works for non-root users as well. See [Termux](#termux) section below to learn how to configure Termux to run commands from third-party applications.
- **Extract Icon.** Extract and save the app's icon in your desired location.

### Termux
By default, Termux does not allow running commands from third-party applications. To enable this option, you have to add `allow-external-apps=true` in <tt>~/.termux/termux.properties</tt> and make sure that you are running Termux v0.96 or later.

::: tip Info
Enabling this option does not weaken your Termux' security. The third-party apps still need to request the user to allow running arbitrary commands in Termux like any other dangerous permissions.
:::

## Component Tabs
**Activities**, **Services**, **Receivers** (originally _broadcast receivers_) and **Providers** (originally _Content Providers_) are together called the application components. This is because they share similar features in many ways. For example, they all have a _name_ and a _label_. Application components are the building blocks of any application, and most of these have to declared in the application manifest. Application manifest is a file where application specific metadata are stored. The Android operating system learns what to do with an app by reading the metadata. [Colors](#color-codes) used in these tabs are explained above.

::: details Table of Contents
- [Activities](#activities)
- [Services](#services)
- [Receivers](#receivers)
- [Providers](#providers)
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

### Services
Unlike [activities](#activities) which users can see, **Services** handle background tasks. If you're, for example, downloading a video from the internet using your phone's Internet browser, the Internet browser is using a background service to download the content.

When you close an activity, it usually gets destroyed immediately (depending on many factors such as how much free memory your phone has). But services can be run for indefinite periods if desired. If more services are running in background, you phone will get slower due to shortage of memory and/or processing power, and your phone's battery can be drained more quickly. Newer Android versions have a battery optimization feature enabled by default for all apps. With this feature enabled, the system can randomly terminate any service.

By the way, both activities and services are run in the same looper called the main [looper][looper], which means the services are not really run in the background. It's the application authors job to ensure that. How do application communicate with services? They use [broadcast receivers](#receivers).

### Receivers
**Receivers** (also called _broadcast receivers_) can be used to trigger execution of certain tasks for certain events. These components are called broadcast receivers because they are executed as soon as a broadcast message is received. These broadcast messages are sent using a method called intent. Intent is a special feature for Android which can be used to open applications, activities, services and send broadcast messages. Therefore, like [activities](#activities), broadcast receivers use intent filters to receive only the desired broadcast message(s). Broadcast messages can be sent by either system or the app itself. When a broadcast message is sent, the corresponding receivers are awaken by the system so that they can execute tasks. For example, if you have low memory, your phone may freeze or experience lags for a moment after you enable mobile data or connect to the Wifi. Ever wondered why? This is because broadcast receivers that can receive `android.net.conn.CONNECTIVITY_CHANGE` are awaken by the system as soon as you enable data connection. Since many apps use this intent filter, all of these apps are awaken almost immediately by the system which causes the freeze or lags. That being said, receivers can be used for inter-process communication (IPC), i.e., it helps you communicate between different apps (provided you have the necessary permissions) or even different components of a single app.

### Providers
**Providers** (also called _content providers_) are used for data management. For example, when you save an apk file or export rules in App Manager, it uses a content provider called `androidx.core.content.FileProvider` to save the apk or export the rules. There are other content providers or even custom ones to manage various content-related tasks such as database management, tracking, searching, etc. Each content provider has a field called _Authority_ which is unique to that particular app in the entire Android eco-system just like the package name.

### Additional Features for Rooted Phones
Unlike non-root users who are just spectators in these tabs, root users can perform various operations. On the right-most side of each component item, there is a “block” icon (which becomes a “unblock/restore” icon when the component is being blocked). This icon can be used to toggle blocking status of that particular component. If you do not have [Global Component Blocking][settings_gcb] enabled or haven't applied blocking for the app before, you have to apply the changes using the **Apply rules** option in the top-right menu. You can also remove already applied rules using the same option (which would be read as **Remove rules** this time). You also have the ability to sort the component list to display blocked or tracker components on top of the list using the **Sort** option in the same menu. You can also disable all ad and tracker components using the **Block tracker** option in the menu.

_See also:_
- _[εxodus Page](./exodus-page.md)_
- _[FAQ: App Components][faq_ac]_

## Permission Tabs
**App Ops**, **Uses Permissions** and **Permissions** tabs are related to permissions. In Android communication between apps or processes which do not have the same identity (known as _shared id_) often require permission(s). These permissions are managed by the permission controller. Some permissions are considered _normal_ permissions which are granted automatically if they appear in the application manifest, but _dangerous_ and _development_ permissions require confirmation from the user. [Colors](#color-codes) used in these tabs are explained above.

::: details Table of Contents
- [App Ops](#app-ops)
- [Uses Permissions](#uses-permissions)
- [Permissions](#permissions)
:::

### App Ops
**App Ops** stands for **Application Operations**. Since Android 4.3, _App Ops_ are used by Android system to control most of the application permissions. Each app op has a unique number associated with them which are closed inside first brackets in the App Ops tab. They also have private and optionally a public name. Some app ops are associated with _permissions_ as well. The dangerousness of an app op is decided based on the associated permission, and other informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#permissions). Other information may include the following:
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

### Uses Permissions
**Uses Permissions** are the permissions used by the application. This is named so because they are declared in the manifest using `uses-permission` tags. Informations such as _flags_, _permission name_, _permission description_, _package name_, _group_ are taken from the associated [permission](#permissions).

**Root and [ADB][2] users** can grant or revoke the _dangerous_ and _development_ permissions using the toggle button on the right side of each permission item. They can also revoke dangerous permissions all at once using the corresponding option in the menu. Only these two types of permissions can be revoked because Android doesn't allow modifiying _normal_ permissions (which most of them are). The only alternative is to edit the app manifest and remove these permissions from there.

::: tip Info
Since dangerous permissions are revoked by default by the system, revoking all dangerous permissions is the same as resetting all permissions.
:::

Users can sort the permissions by permission name (in ascending order) or choose to display denied or dangerous permissions at first using the corresponding options in the menu. 

### Permissions
**Permissions** are usually custom permissions defined by the app itself. It could contain regular permissions as well, mostly in old applications. Here's a complete description of each item that is displayed there:
- **Name.** Each permission has a unique name like `android.permission.INTERNET` but multiple app can request the permission.
- **Icon.** Each permission can have a custom icon. The other permission tabs do not have any icon because they do not contain any icon in the app manifest.
- **Description.** This optional field describes the permission. If there isn't any description associated with the permission, the field is not displayed.
- **Flags.** (Uses ⚑ symbol or **Protection Level** name) This describes various permission flags such as _normal_, _development_, _dangerous_, _instant_, _granted_, _revoked_, _signature_, _privileged_, etc.
- **Package Name.** Denotes the package name associated with the permission, i.e., the package that defined the permission.
- **Group.** The group name associated with the permission (if any). Newer Androids do not seem to use group names which is why you'll usually see `android.permission-group.UNDEFINED` or no group name at all.

## Signatures Tab
**Signatures** are actually called signing info. An application is signed by one or more singing certificates by the application developers before publishing it. The integrity of an application (whether the app is from the actual developer and isn't modified by other people) can be checked using the signing info; because when an app is modified by a third-party unauthorized person, the app cannot be signed using the original certificate(s) again because the signing info are kept private by the actual developer. _How do you verify these signatures?_ Using checksums. Checksums are generated from the certificates themselves. If the developer supplies the checksums, you can match the checksums using the different checksums generated in the **Signatures** tab. For example, if you have downloaded App Manager from Github, Telegram Channel or IzzyOnDroid's repo, you can verify whether the app is actually released by me by simply matching the following _sha256_ checksum with the one displayed in this tab:
```
320c0c0fe8cef873f2b554cb88c837f1512589dcced50c5b25c43c04596760ab
```

There are three types of checksums displayed there: _md5_, _sha1_ and _sha256_.

::: danger Caution
It is recommended that you verify signing info using only _sha256_ checksums or all three of them. DO NOT rely on _md5_ or _sha1_ checksums only as they are known to generate the same results for multiple certificates.
:::

## Other Tabs
Other tabs list android manifest components such as features, configurations, shared libraries, and signatures. A complete description about these tabs will be available soon.

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
