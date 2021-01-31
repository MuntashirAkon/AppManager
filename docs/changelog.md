---
sidebar: auto
---
# Changelog

![Debug Build](https://github.com/MuntashirAkon/AppManager/workflows/Debug%20Build/badge.svg)
[![GitHub Release](https://img.shields.io/github/v/release/MuntashirAkon/AppManager)](https://github.com/MuntashirAkon/AppManager/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.muntashirakon.AppManager)](https://f-droid.org/packages/io.github.muntashirakon.AppManager)
![GitHub Repo Size](https://img.shields.io/github/repo-size/MuntashirAkon/AppManager)
![GitHub Commit per Week](https://img.shields.io/github/commit-activity/w/MuntashirAkon/AppManager)

Currently supported versions are [v2.5.23](https://github.com/MuntashirAkon/AppManager/releases/tag/pre-v2.5.23) and [v2.5.20](#v2-5-20-375). Please update App Manager if you are using a version older than these.

<small>[Click to see old changelog.](./changelog_old.md)</small>

::: details Table of Contents
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

[profile_page]: ./guide/profile-page.md
[profiles_page]: ./guide/profiles-page.md

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

[scanner]: ./guide/scanner-page.md
[whats_new]: ./guide/app-details-page.md#horizontal-action-panel

## v2.5.13 (348)
### Bundled App (Split APK)
Bundled app formats such as **apks** and **xapk** are now supported. You can install these apps using the regular install buttons. For root and adb users, apps are installed using shell, and for non-root users, the platform default method is used.

**Known Limitations:**
- Currently _all_ splits apks are installed. But this behaviour is going to change in the next release. If you only need a few splits instead of all, extract the **apks** or **xapk** file, and then, create a new zip file with your desired split apks and replace the **zip** extension with **apks**. Now, open it with AM.
- There is no progress dialog to display the installation progress.

### Direct Install Support
You can now install **apk**, **apks** or **xapk** directly from your favourite browser or file manager. For apps that need updates, a **What's New** dialog is displayed showing the changes in the new version.

**Known Limitations:**
- Downgrade is not yet possible.
- There is no progress dialog to display the installation progress. If you cannot interact with the current page, wait until the installation is finished.

### Remove All Blocking Rules
In the Settings page, a new option is added which can be used to remove all blocking rules configured within App Manager.

### App Ops
- App Ops are now generated using a technique similar to AppOpsX. This should decrease the loading time significantly in the App Ops tab.
- In the App Ops tab, a menu item is added which can be used to list only active app ops without including the default app ops. This preferences is saved in the shared preferences.

**Kown Limitations:** Often the App Ops tab may not be responsive. If that's the case, restart AM.

### Enhanced ADB Support
ADB shell commands are now executed using a technique similar to AppOpsX (This is the _free_ alternative of Shizuku.). This should dramatically increase the execution time.

**Known Limitations:** AM can often crash or become not responsive. If that's the case, restart AM.

### Filtering in Main Page
Add an option to filter apps that has at least one activity.

### Apk Backup/Sharing
Apk files are now saved as `app name_version.extension` instead of `package.name.extension`.

### Batch Ops
- Added a foreground service to run batch operations. The result of the operation is displayed in a notification. If an operation has failed for some packages, clicking on the notification will open a dialog box listing the failed packages. There is also a **Try Again** button on the bottom which can be used to perform the operation again for the failed packages.
- Replaced Linux _kill_ with **force-stop**.

### Translations
Added German and Portuguese (Brazilian) translations.

**Known Limitations:** Not all translations are verified yet.

### App Data Backup
Install app only for the current user at the time of restoring backups. Support for split apks is also added.

_Data backup feature is now considered unstable. If you encounter any problem, please report to me without hesitation._

[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[app_info]: ./guide/app-details-page.md#app-info-tab
[5]: ./guide/settings-page.md#global-component-blocking
[6]: ./guide/main-page.md#app-usage
[main_page]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
[termux]: https://github.com/termux/termux-app
