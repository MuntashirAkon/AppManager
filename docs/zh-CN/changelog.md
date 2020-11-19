---
sidebar: auto
---

# 更新日志

![Debug Build](https://github.com/MuntashirAkon/AppManager/workflows/Debug%20Build/badge.svg) [![GitHub 发布](https://img.shields.io/github/v/release/MuntashirAkon/AppManager)](https://github.com/MuntashirAkon/AppManager/releases/latest) [![F-Droid](https://img.shields.io/f-droid/v/io.github.muntashirakon.AppManager)](https://f-droid.org/packages/io.github.muntashirakon.AppManager) ![GitHub Repo Size](https://img.shields.io/github/repo-size/MuntashirAkon/AppManager) ![GitHub Commit per Week](https://img.shields.io/github/commit-activity/w/MuntashirAkon/AppManager)

Currently supported versions are [v2.5.13](#v2-5-13-348), [v2.5.12](#v2-5-12-341), [v2.5.11](#v2-5-11-333) and [v2.5.10](#v2-5-10-324). Please update App Manager if you are using a version older than these.

::: details Table of Contents
[[toc]]
:::

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

### Apk 备份/分享
Apk 文件现在被保存为 `应用程序名称_version.extension` 而不是 `package.name.extension`

### 批量操作
- Added a foreground service to run batch operations. The result of the operation is displayed in a notification. If an operation has failed for some packages, clicking on the notification will open a dialog box listing the failed packages. There is also a **Try Again** button on the bottom which can be used to perform the operation again for the failed packages.
- Replaced Linux _kill_ with **force-stop**.

### Translations
Added German and Portuguese (Brazilian) translations.

**Known Limitations:** Not all translations are verified yet.

### App Data Backup
Install app only for the current user at the time of restoring backups. Support for split apks is also added.

_Data backup feature is now considered unstable. If you encounter any problem, please report to me without hesitation._


## v2.5.12 (341)
- <span><tagfeature/></span>  Added support for splitting data backups into 1GB files to circumvent the limitation of FAT32 file system
- <span><tagfeature/></span>  Added the ability to unblock trackers
- <span><tagfeature/></span>  Added an option to skip signature checks while restoring backups
- <span><tagfeature/></span>  [Termux][termux] support: <code>run-as</code> debuggable app or run new session as app in the [App Info tab][app_info]
- <span><tagfeature/></span>  Display backup app info in the [Main page][main_page]
- <span><tagfeature/></span>  Restoring source files (except apk files) disabled on unsupported architecture
- <span><tagfeature/></span>  Display confirmation dialog before clearing app data
- <span><tagfeature/></span>  Ability to import components disabled using IFW on MAT
- <span><tagfeature/></span>  Include external media and obb directory for backups
- <span><tagfeature/></span>  Allow importing existing rules by other apps or tools
- <span><tagfeature/></span>  Added an option to extract app icon in [App Info tab][app_info]
- <span><tagfix/></span> Display restore and delete backups only for apps with backup
- <span><tagfix/></span> Display progress indicator while taking backups
- <span><tagfix/></span> Display progress indicator while loading app ops
- <span><tagfix/></span> Fixed app not opening in the latest and the only supported Aurora Droid (v1.0.6)
- <span><tagfix/></span> Fixed crash on night mode change while browsing [App Details page][1]
- <span><tagfix/></span> Fixed crash when trying to open external apk file
- <span><tagfix/></span> Fixed NullPointerException when an external data directory is null
- <span><tagfix/></span> Fixed toolbar in full screen dialog
- <span><tagfix/></span> Fixed case insensitive searching
- <span><tagfix/></span> Optimized app theme
- <span><tagfix/></span> Replaced AndroidShell with LibSuperuser
- <span><tagfix/></span> Request external storage permission when saving apk files
- <span><tagfix/></span> Workaround for AppBarLayout bug in Material Design
- <span><tagfix/></span> Update external apk info on install/uninstall events

To use Termux features, make sure you are using Termux v0.96 or later and `allow-external-apps=true` is added in <tt>~/.termux/termux.properties</tt>.

Data backup feature is still considered experimental and please do not rely on it to manage your backups yet.

## v2.5.11 (333)
- <span><tagfeature/></span>  Added experimental support for app data backup. Please test only on apps you don't need. (root only)
- <span><tagfeature/></span>  Added sharing split apk files as apks (can be installed via [SAI][8]).
- <span><tagfeature/></span>  Implemented saving apk files in batch selection mode.
- <span><tagfeature/></span>  Added what's new for apk file that needs an update (when opening external apk files).
- <span><tagfeature/></span>  Added an option to apply 1-click ops to system apps (disabled by default).
- <span><tagfeature/></span>  Added installed app version info in the App Info tab. Clicking the _i_ icon opens the installed [App Info][app_info] tab.
- <span><tagfeature/></span>  New on-demand permissions <tt>READ_EXTERNAL_STORAGE</tt> & <tt>WRITE_EXTERNAL_STORAGE</tt> for app backup support
- <span><tagfeature/></span>  Display apps that are uninstalled but have backups in the main page
- <span><tagfeature/></span>  Added a disclaimer
- <span><tagfix/></span> Fixed selections being not cleared after the task is completed in the main page
- <span><tagfix/></span> Convert various info in the configurations and features tab to text to improve readability
- <span><tagfix/></span> Fix crash in the [Main page][main_page] while filtering apps by search query
- <span><tagfix/></span> Fix crash in the [App Info][app_info] tab when existence of external data directory has false-positive result

**Note:** Backup data are stored at <tt>/sdcard/AppManager</tt> and apk backups are stored at <tt>/sdcard/AppManager/apks</tt>. Data backups are currently not working on Android Lollipop.

## v2.5.10 (324)
- <span><tagfeature/></span>  Added 1-click operations (as [1-Click Ops](./guide/one-click-ops-page.md) in the menu section in the [Main page][main_page]): block (ads and) trackers, component blocking by signatures, app op blocking
- <span><tagfeature/></span>  Added support for external apk: You can now open apk files from your file manager. You can view app details, manifest or scan for trackers directly from there
- <span><tagfeature/></span>  Added persistent apps filtering option in the [Main page][main_page]
- <span><tagfeature/></span>  Alternative manifest viewer for installed apks
- <span><tagfeature/></span>  Display number of trackers as a tag in the [App Info][app_info] tab
- <span><tagfeature/></span>  Added a select all option in the bottom bar in the [Main page][main_page] in selection mode
- <span><tagfeature/></span>  Added links to source code and community
- <span><tagfeature/></span>  Added support for installing/updating apk files in the [App Info][app_info] tab (incomplete)
- <span><tagfeature/></span>  Added an option to import existing disabled components in the Import/Export settings (incomplete)
- <span><tagfeature/></span>  Added split apk information in [App Info][app_info] tab
- <span><tagfeature/></span>  Added an option to open [Termux](./guide/main-page.md#termux) in the [Main page][main_page] (incomplete)
- <span><tagfeature/></span>  Initial support for app banner
- <span><tagfix/></span> Fixed inconsistency of enable and disable in the App Info tab
- <span><tagfix/></span> Fixed issue with persistent app cache
- <span><tagfix/></span> Fixed scrolling issue on settings page
- <span><tagfix/></span> Fixed crashes when switching to the components tabs for non-root users
- <span><tagfix/></span> Fixed crash when trying to view summary while scanning is still in progress in the exodus page
- <span><tagfix/></span> Fixed crashes on devices that does not support data usage
- <span><tagfix/></span> Fixed crash when trying to view manifest of an split apk
- <span><tagfix/></span> Fixed wrong package installer name in the [App Info][app_info] tab
- <span><tagfix/></span> Fixed changelog formatting for old devices

## v2.5.9 (315)
- <span><tagfeature/></span>  Merged [App Info][app_info] as a single tab in [App Details][1]
- <span><tagfeature/></span>  Added option to reset all app ops
- <span><tagfeature/></span>  Added option to revoke all dangerous app ops/permissions
- <span><tagfeature/></span>  Highlight trackers in the component tabs
- <span><tagfeature/></span>  Added option to save manifest and class dump
- <span><tagfeature/></span>  Added the ability to grant/revoke development permissions
- <span><tagfeature/></span>  Added sorting options for components, app ops and uses permissions tabs
- <span><tagfeature/></span>  Added sort by wifi usage in the [App Usage][6] page
- <span><tagfeature/></span>  Added launch button in the [App Info][app_info] tab
- <span><tagfeature/></span>  Added never ask option to usage status prompt
- <span><tagfeature/></span>  Added long click to select apps in the [Main page][main_page]
- <span><tagfeature/></span>  Added changelog within the app
- <span><tagfix/></span> Click to select apps during selection mode
- <span><tagfix/></span> Improved component blocker
- <span><tagfix/></span> Improved manifest loading for large apps
- <span><tagfix/></span> Improved tab loading performance
- <span><tagfix/></span> Fixed app ops checking and custom app ops for some devices
- <span><tagfix/></span> Disabled activity opening for disabled activities
- <span><tagfix/></span> Get real activity name for activities that use activity-alias
- <span><tagfix/></span> Fixed background colors
- <span><tagfix/></span> Fixed crashing when loading the services tab for non-root users
- <span><tagfix/></span> Fixed back button for class viewer which was not working
- <span><tagfix/></span> Changed block icon's colour to accent colour
- <span><tagfix/></span> Removed translation until the app is complete
- <span><tagfix/></span> Made links in the credit section clickable
- <span><tagfix/></span> Fixed various memory leaks

## v2.5.8 (289)
- <span><tagfeature/></span>  Added [import/export capabilities for blocking rules](./guide/settings-page.md#import-export-blocking-rules)
- <span><tagfeature/></span>  Added ability to [select themes](./guide/settings-page.html#app-theme) (night/day)
- <span><tagfeature/></span>  Added mode, duration, accept time, reject time for app ops
- <span><tagfeature/></span>  Highlight running services
- <span><tagfeature/></span>  Highlight disabled components not disabled within App Manager
- <span><tagfeature/></span>  Added swipe to refresh in the [App Usage][6] page
- <span><tagfeature/></span>  Added screen time percentage with indicator
- <span><tagfeature/></span>  Separate instructions and about pages with fullscreen dialog for both
- <span><tagfeature/></span>  Rounded overflow menu (still incomplete)
- <span><tagfix/></span> Fixed various device/SDK specific app ops issues
- <span><tagfix/></span> Stability improvements of the entire apps
- <span><tagfix/></span> Added <tt>ACCESS_NETWORK_STATE</tt> permission to support older operating systems
- <span><tagfix/></span> Fixed deleting all IFW rules when selecting [Global Component Blocking][5]
- <span><tagfix/></span> Fixed various search issues

## v2.5.7 (265)
- <span><tagfeature/></span>  Initial support for [ADB over TCP](./guide/adb-over-tcp.md) (port 5555) for non-root users
- <span><tagfix/></span> Fixed importing rules from [Watt][2] and [Blocker][3]
- <span><tagfix/></span> Display Aurora Droid in [App Info][app_info] page as a first priority over F-Droid
- <span><tagfix/></span> Improved performance for component blocking
- <span><tagfix/></span> Fixed app op mode detection issue

**For root users:** If you've skipped [v2.5.6](#v2-5-6-233), you may need to apply all rules globally by applying [Global Component Blocking][5] in Settings in order for them to work.

## v2.5.6 (233)
- <span><tagfeature/></span>  [Batch operations](./guide/main-page.md#batch-operations) in the main page: clear app data, disable run in background, disable/kill/uninstall apps (click on the app icon to select)
- <span><tagfeature/></span>  Full support of [Blocker][3]'s exported files which was broken due to a bug in Blocker
- <span><tagfeature/></span>  Reimplementation of blocking activities, receivers, services and providers
- <span><tagfix/></span> Removed ConstraintLayout dependency therefore a potential decrease in app size
- <span><tagfix/></span> Fixed duplicate app usage warning in the [App Info][app_info] page
- <span><tagfix/></span> Fixed crash when an app icon is not found in [App Details][1] page

**Note for root users:** In order to ensure that the previous blocking rules are preserved with the new blocking implementation, this update reads from the previous rules consequently increasing the loading time in the [Main page][main_page]. This feature will be removed in the next release but can still be simulated by applying [global component blocking][5] in Settings.

## v2.5.5 (215)
- <span><tagfeature/></span>  Added [Running Apps/Process Viewer](./guide/main-page.md#running-apps) (requires root)
- <span><tagfeature/></span>  Added [Usage Details Viewer][6]
- <span><tagfeature/></span>  Added [Apk Updater](./guide/main-page.md#apk-updater) and [Aurora Store](./guide/app-details-page.md#actions-in-app-info-tab) support
- <span><tagfeature/></span>  Save modified values of app ops and permissions to the disk (on progress)
- <span><tagfix/></span> Uninstall support for non-root users
- <span><tagfix/></span> Restructure app usage
- <span><tagfix/></span> Added more clarity as well as improve performance in the [App Details][1] page

[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[app_info]: ./guide/app-details-page.md#app-info-tab
[5]: ./guide/settings-page.md#global-component-blocking
[6]: ./guide/main-page.md#app-usage
[main_page]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
[termux]: https://github.com/termux/termux-app
