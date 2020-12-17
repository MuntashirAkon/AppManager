---
sidebar: auto
---
# Changelog

![Debug Build](https://github.com/MuntashirAkon/AppManager/workflows/Debug%20Build/badge.svg)
[![GitHub Release](https://img.shields.io/github/v/release/MuntashirAkon/AppManager)](https://github.com/MuntashirAkon/AppManager/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.muntashirakon.AppManager)](https://f-droid.org/packages/io.github.muntashirakon.AppManager)
![GitHub Repo Size](https://img.shields.io/github/repo-size/MuntashirAkon/AppManager)
![GitHub Commit per Week](https://img.shields.io/github/commit-activity/w/MuntashirAkon/AppManager)

Currently supported versions are [v2.5.20](#v2-5-20-375), [v2.5.19](https://github.com/MuntashirAkon/AppManager/releases/tag/pre-v2.5.19), [v2.5.18](https://github.com/MuntashirAkon/AppManager/releases/tag/pre-v2.5.18) and [v2.5.17](#v2-5-17-368). Please update App Manager if you are using a version older than these.

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
Added support for Android 11. Not all things may not work as expected though.

### App Installer Improvements
#### Set install locations
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


## v2.5.12 (341)
- <span><TagFeature/></span>  Added support for splitting data backups into 1GB files to circumvent the limitation of FAT32 file system
- <span><TagFeature/></span>  Added the ability to unblock trackers
- <span><TagFeature/></span>  Added an option to skip signature checks while restoring backups
- <span><TagFeature/></span>  [Termux][termux] support: <code>run-as</code> debuggable app or run new session as app in the [App Info tab][app_info]
- <span><TagFeature/></span>  Display backup app info in the [Main page][main_page]
- <span><TagFeature/></span>  Restoring source files (except apk files) disabled on unsupported architecture
- <span><TagFeature/></span>  Display confirmation dialog before clearing app data
- <span><TagFeature/></span>  Ability to import components disabled using IFW on MAT
- <span><TagFeature/></span>  Include external media and obb directory for backups
- <span><TagFeature/></span>  Allow importing existing rules by other apps or tools
- <span><TagFeature/></span>  Added an option to extract app icon in [App Info tab][app_info]
- <span><TagFix/></span> Display restore and delete backups only for apps with backup
- <span><TagFix/></span> Display progress indicator while taking backups
- <span><TagFix/></span> Display progress indicator while loading app ops
- <span><TagFix/></span> Fixed app not opening in the latest and the only supported Aurora Droid (v1.0.6)
- <span><TagFix/></span> Fixed crash on night mode change while browsing [App Details page][1]
- <span><TagFix/></span> Fixed crash when trying to open external apk file
- <span><TagFix/></span> Fixed NullPointerException when an external data directory is null
- <span><TagFix/></span> Fixed toolbar in full screen dialog
- <span><TagFix/></span> Fixed case insensitive searching
- <span><TagFix/></span> Optimized app theme
- <span><TagFix/></span> Replaced AndroidShell with LibSuperuser
- <span><TagFix/></span> Request external storage permission when saving apk files
- <span><TagFix/></span> Workaround for AppBarLayout bug in Material Design
- <span><TagFix/></span> Update external apk info on install/uninstall events

To use Termux features, make sure you are using Termux v0.96 or later and `allow-external-apps=true` is added in <tt>~/.termux/termux.properties</tt>.

Data backup feature is still considered experimental and please do not rely on it to manage your backups yet.

## v2.5.11 (333)
- <span><TagFeature/></span>  Added experimental support for app data backup. Please test only on apps you don't need. (root only)
- <span><TagFeature/></span>  Added sharing split apk files as apks (can be installed via [SAI][8]).
- <span><TagFeature/></span>  Implemented saving apk files in batch selection mode.
- <span><TagFeature/></span>  Added what's new for apk file that needs an update (when opening external apk files).
- <span><TagFeature/></span>  Added an option to apply 1-click ops to system apps (disabled by default).
- <span><TagFeature/></span>  Added installed app version info in the App Info tab. Clicking the _i_ icon opens the installed [App Info][app_info] tab.
- <span><TagFeature/></span>  New on-demand permissions <tt>READ_EXTERNAL_STORAGE</tt> & <tt>WRITE_EXTERNAL_STORAGE</tt> for app backup support
- <span><TagFeature/></span>  Display apps that are uninstalled but have backups in the main page
- <span><TagFeature/></span>  Added a disclaimer
- <span><TagFix/></span> Fixed selections being not cleared after the task is completed in the main page
- <span><TagFix/></span> Convert various info in the configurations and features tab to text to improve readability
- <span><TagFix/></span> Fix crash in the [Main page][main_page] while filtering apps by search query
- <span><TagFix/></span> Fix crash in the [App Info][app_info] tab when existence of external data directory has false-positive result

**Note:** Backup data are stored at <tt>/sdcard/AppManager</tt> and apk backups are stored at <tt>/sdcard/AppManager/apks</tt>. Data backups are currently not working on Android Lollipop.

## v2.5.10 (324)
- <span><TagFeature/></span>  Added 1-click operations (as [1-Click Ops](./guide/one-click-ops-page.md) in the menu section in the [Main page][main_page]): block (ads and) trackers, component blocking by signatures, app op blocking
- <span><TagFeature/></span>  Added support for external apk: You can now open apk files from your file manager. You can view app details, manifest or scan for trackers directly from there
- <span><TagFeature/></span>  Added persistent apps filtering option in the [Main page][main_page]
- <span><TagFeature/></span>  Alternative manifest viewer for installed apks
- <span><TagFeature/></span>  Display number of trackers as a tag in the [App Info][app_info] tab
- <span><TagFeature/></span>  Added a select all option in the bottom bar in the [Main page][main_page] in selection mode
- <span><TagFeature/></span>  Added links to source code and community
- <span><TagFeature/></span>  Added support for installing/updating apk files in the [App Info][app_info] tab (incomplete)
- <span><TagFeature/></span>  Added an option to import existing disabled components in the Import/Export settings (incomplete)
- <span><TagFeature/></span>  Added split apk information in [App Info][app_info] tab
- <span><TagFeature/></span>  Added an option to open [Termux](./guide/main-page.md#termux) in the [Main page][main_page] (incomplete)
- <span><TagFeature/></span>  Initial support for app banner
- <span><TagFix/></span> Fixed inconsistency of enable and disable in the App Info tab
- <span><TagFix/></span> Fixed issue with persistent app cache
- <span><TagFix/></span> Fixed scrolling issue on settings page
- <span><TagFix/></span> Fixed crashes when switching to the components tabs for non-root users
- <span><TagFix/></span> Fixed crash when trying to view summary while scanning is still in progress in the exodus page
- <span><TagFix/></span> Fixed crashes on devices that does not support data usage
- <span><TagFix/></span> Fixed crash when trying to view manifest of an split apk
- <span><TagFix/></span> Fixed wrong package installer name in the [App Info][app_info] tab
- <span><TagFix/></span> Fixed changelog formatting for old devices

## v2.5.9 (315)
- <span><TagFeature/></span>  Merged [App Info][app_info] as a single tab in [App Details][1]
- <span><TagFeature/></span>  Added option to reset all app ops
- <span><TagFeature/></span>  Added option to revoke all dangerous app ops/permissions
- <span><TagFeature/></span>  Highlight trackers in the component tabs
- <span><TagFeature/></span>  Added option to save manifest and class dump
- <span><TagFeature/></span>  Added the ability to grant/revoke development permissions
- <span><TagFeature/></span>  Added sorting options for components, app ops and uses permissions tabs
- <span><TagFeature/></span>  Added sort by wifi usage in the [App Usage][6] page
- <span><TagFeature/></span>  Added launch button in the [App Info][app_info] tab
- <span><TagFeature/></span>  Added never ask option to usage status prompt
- <span><TagFeature/></span>  Added long click to select apps in the [Main page][main_page]
- <span><TagFeature/></span>  Added changelog within the app
- <span><TagFix/></span> Click to select apps during selection mode
- <span><TagFix/></span> Improved component blocker
- <span><TagFix/></span> Improved manifest loading for large apps
- <span><TagFix/></span> Improved tab loading performance
- <span><TagFix/></span> Fixed app ops checking and custom app ops for some devices
- <span><TagFix/></span> Disabled activity opening for disabled activities
- <span><TagFix/></span> Get real activity name for activities that use activity-alias
- <span><TagFix/></span> Fixed background colors
- <span><TagFix/></span> Fixed crashing when loading the services tab for non-root users
- <span><TagFix/></span> Fixed back button for class viewer which was not working
- <span><TagFix/></span> Changed block icon's colour to accent colour
- <span><TagFix/></span> Removed translation until the app is complete
- <span><TagFix/></span> Made links in the credit section clickable
- <span><TagFix/></span> Fixed various memory leaks

## v2.5.8 (289)
- <span><TagFeature/></span>  Added [import/export capabilities for blocking rules](./guide/settings-page.md#import-export-blocking-rules)
- <span><TagFeature/></span>  Added ability to [select themes](./guide/settings-page.html#app-theme) (night/day)
- <span><TagFeature/></span>  Added mode, duration, accept time, reject time for app ops
- <span><TagFeature/></span>  Highlight running services
- <span><TagFeature/></span>  Highlight disabled components not disabled within App Manager
- <span><TagFeature/></span>  Added swipe to refresh in the [App Usage][6] page
- <span><TagFeature/></span>  Added screen time percentage with indicator
- <span><TagFeature/></span>  Separate instructions and about pages with fullscreen dialog for both
- <span><TagFeature/></span>  Rounded overflow menu (still incomplete)
- <span><TagFix/></span> Fixed various device/SDK specific app ops issues
- <span><TagFix/></span> Stability improvements of the entire apps
- <span><TagFix/></span> Added <tt>ACCESS_NETWORK_STATE</tt> permission to support older operating systems
- <span><TagFix/></span> Fixed deleting all IFW rules when selecting [Global Component Blocking][5]
- <span><TagFix/></span> Fixed various search issues

## v2.5.7 (265)
- <span><TagFeature/></span>  Initial support for [ADB over TCP](./guide/adb-over-tcp.md) (port 5555) for non-root users
- <span><TagFix/></span> Fixed importing rules from [Watt][2] and [Blocker][3]
- <span><TagFix/></span> Display Aurora Droid in [App Info][app_info] page as a first priority over F-Droid
- <span><TagFix/></span> Improved performance for component blocking
- <span><TagFix/></span> Fixed app op mode detection issue

**For root users:** If you've skipped [v2.5.6](#v2-5-6-233), you may need to apply all rules globally by applying [Global Component Blocking][5] in Settings in order for them to work.

## v2.5.6 (233)
- <span><TagFeature/></span>  [Batch operations](./guide/main-page.md#batch-operations) in the main page: clear app data, disable run in background, disable/kill/uninstall apps (click on the app icon to select)
- <span><TagFeature/></span>  Full support of [Blocker][3]'s exported files which was broken due to a bug in Blocker
- <span><TagFeature/></span>  Reimplementation of blocking activities, receivers, services and providers
- <span><TagFix/></span> Removed ConstraintLayout dependency therefore a potential decrease in app size
- <span><TagFix/></span> Fixed duplicate app usage warning in the [App Info][app_info] page
- <span><TagFix/></span> Fixed crash when an app icon is not found in [App Details][1] page

**Note for root users:** In order to ensure that the previous blocking rules are preserved with the new blocking implementation, this update reads from the previous rules consequently increasing the loading time in the [Main page][main_page]. This feature will be removed in the next release but can still be simulated by applying [global component blocking][5] in Settings.

## v2.5.5 (215)
- <span><TagFeature/></span>  Added [Running Apps/Process Viewer](./guide/main-page.md#running-apps) (requires root)
- <span><TagFeature/></span>  Added [Usage Details Viewer][6]
- <span><TagFeature/></span>  Added [Apk Updater](./guide/main-page.md#apk-updater) and [Aurora Store](./guide/app-details-page.md#actions-in-app-info-tab) support
- <span><TagFeature/></span>  Save modified values of app ops and permissions to the disk (on progress)
- <span><TagFix/></span> Uninstall support for non-root users
- <span><TagFix/></span> Restructure app usage
- <span><TagFix/></span> Added more clarity as well as improve performance in the [App Details][1] page

[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[app_info]: ./guide/app-details-page.md#app-info-tab
[5]: ./guide/settings-page.md#global-component-blocking
[6]: ./guide/main-page.md#app-usage
[main_page]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
[termux]: https://github.com/termux/termux-app
