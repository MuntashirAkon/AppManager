<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
---
sidebar: auto
---
# Old Changelog

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
