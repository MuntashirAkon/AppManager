---
sidebar: auto
---
# Changelog

::: details Table of Contents
[[toc]]
:::

## v2.5.11 (333)
- <font color="#09868B">[Feature]</font> Added experimental support for app data backup. Please test only on apps you don't need. (root only)
- <font color="#09868B">[Feature]</font> Added sharing split apk files as apks (can be installed via [SAI][8]).
- <font color="#09868B">[Feature]</font> Implemented saving apk files in batch selection mode.
- <font color="#09868B">[Feature]</font> Added what's new for apk file that needs an update (when opening external apk files).
- <font color="#09868B">[Feature]</font> Added an option to apply 1-click ops to system apps (disabled by default).
- <font color="#09868B">[Feature]</font> Added installed app version info in the App Info tab. Clicking the _i_ icon opens the installed [App Info][4] tab.
- <font color="#09868B">[Feature]</font> New on-demand permissions <tt>READ_EXTERNAL_STORAGE</tt> & <tt>WRITE_EXTERNAL_STORAGE</tt> for app backup support
- <font color="#09868B">[Feature]</font> Display apps that are uninstalled but have backups in the main page
- <font color="#09868B">[Feature]</font> Added a disclaimer
- <font color="red">[Fix]</font> Fixed selections being not cleared after the task is completed in the main page
- <font color="red">[Fix]</font> Convert various info in the configurations and features tab to text to improve readability
- <font color="red">[Fix]</font> Fix crash in the [Main page][7] while filtering apps by search query
- <font color="red">[Fix]</font> Fix crash in the [App Info][4] tab when existence of external data directory has false-positive result

**Note:** Backup data are stored at `/sdcard/AppManager` and apk backups are stored at `/sdcard/AppManager/apks`. Data backups are currently not working on Android Lollipop.

## v2.5.10 (324)
- <font color="#09868B">[Feature]</font> Added 1-click operations (as [1-Click Ops](./guide/one-click-ops-page.md) in the menu section in the [Main page][7]): block (ads and) trackers, component blocking by signatures, app op blocking
- <font color="#09868B">[Feature]</font> Added support for external apk: You can now open apk files from your file manager. You can view app details, manifest or scan for trackers directly from there
- <font color="#09868B">[Feature]</font> Added persistent apps filtering option in the [Main page][7]
- <font color="#09868B">[Feature]</font> Alternative manifest viewer for installed apks
- <font color="#09868B">[Feature]</font> Display number of trackers as a tag in the [App Info][4] tab
- <font color="#09868B">[Feature]</font> Added a select all option in the bottom bar in the [Main page][7] in selection mode
- <font color="#09868B">[Feature]</font> Added links to source code and community
- <font color="#09868B">[Feature]</font> Added support for installing/updating apk files in the [App Info][4] tab (incomplete)
- <font color="#09868B">[Feature]</font> Added an option to import existing disabled components in the Import/Export settings (incomplete)
- <font color="#09868B">[Feature]</font> Added split apk information in [App Info][4] tab
- <font color="#09868B">[Feature]</font> Added an option to open [Termux](./guide/main-page.md#termux) in the [Main page][7] (incomplete)
- <font color="#09868B">[Feature]</font> Initial support for app banner
- <font color="red">[Fix]</font> Fixed inconsistency of enable and disable in the App Info tab
- <font color="red">[Fix]</font> Fixed issue with persistent app cache
- <font color="red">[Fix]</font> Fixed scrolling issue on settings page
- <font color="red">[Fix]</font> Fixed crashes when switching to the components tabs for non-root users
- <font color="red">[Fix]</font> Fixed crash when trying to view summary while scanning is still in progress in the exodus page
- <font color="red">[Fix]</font> Fixed crashes on devices that does not support data usage
- <font color="red">[Fix]</font> Fixed crash when trying to view manifest of an split apk
- <font color="red">[Fix]</font> Fixed wrong package installer name in the [App Info][4] tab
- <font color="red">[Fix]</font> Fixed changelog formatting for old devices

## v2.5.9 (315)
- <font color="#09868B">[Feature]</font> Merged [App Info][4] as a single tab in [App Details][1]
- <font color="#09868B">[Feature]</font> Added option to reset all app ops
- <font color="#09868B">[Feature]</font> Added option to revoke all dangerous app ops/permissions
- <font color="#09868B">[Feature]</font> Highlight trackers in the component tabs
- <font color="#09868B">[Feature]</font> Added option to save manifest and class dump
- <font color="#09868B">[Feature]</font> Added the ability to grant/revoke development permissions
- <font color="#09868B">[Feature]</font> Added sorting options for components, app ops and uses permissions tabs
- <font color="#09868B">[Feature]</font> Added sort by wifi usage in the [App Usage][6] page
- <font color="#09868B">[Feature]</font> Added launch button in the [App Info][4] tab
- <font color="#09868B">[Feature]</font> Added never ask option to usage status prompt
- <font color="#09868B">[Feature]</font> Added long click to select apps in the [Main page][7]
- <font color="#09868B">[Feature]</font> Added changelog within the app
- <font color="red">[Fix]</font> Click to select apps during selection mode
- <font color="red">[Fix]</font> Improved component blocker
- <font color="red">[Fix]</font> Improved manifest loading for large apps
- <font color="red">[Fix]</font> Improved tab loading performance
- <font color="red">[Fix]</font> Fixed app ops checking and custom app ops for some devices
- <font color="red">[Fix]</font> Disabled activity opening for disabled activities
- <font color="red">[Fix]</font> Get real activity name for activities that use activity-alias
- <font color="red">[Fix]</font> Fixed background colors
- <font color="red">[Fix]</font> Fixed crashing when loading the services tab for non-root users
- <font color="red">[Fix]</font> Fixed back button for class viewer which was not working
- <font color="red">[Fix]</font> Changed block icon's colour to accent colour
- <font color="red">[Fix]</font> Removed translation until the app is complete
- <font color="red">[Fix]</font> Made links in the credit section clickable
- <font color="red">[Fix]</font> Fixed various memory leaks

## v2.5.8 (289)
- <font color="#09868B">[Feature]</font> Added [import/export capabilities for blocking rules](./guide/settings-page.md#import-export-blocking-rules)
- <font color="#09868B">[Feature]</font> Added ability to [select themes](./guide/settings-page.html#app-theme) (night/day)
- <font color="#09868B">[Feature]</font> Added mode, duration, accept time, reject time for app ops
- <font color="#09868B">[Feature]</font> Highlight running services
- <font color="#09868B">[Feature]</font> Highlight disabled components not disabled within App Manager
- <font color="#09868B">[Feature]</font> Added swipe to refresh in the [App Usage][6] page
- <font color="#09868B">[Feature]</font> Added screen time percentage with indicator
- <font color="#09868B">[Feature]</font> Separate instructions and about pages with fullscreen dialog for both
- <font color="#09868B">[Feature]</font> Rounded overflow menu (still incomplete)
- <font color="red">[Fix]</font> Fixed various device/SDK specific app ops issues
- <font color="red">[Fix]</font> Stability improvements of the entire apps
- <font color="red">[Fix]</font> Added <tt>ACCESS_NETWORK_STATE</tt> permission to support older operating systems
- <font color="red">[Fix]</font> Fixed deleting all IFW rules when selecting [Global Component Blocking][5]
- <font color="red">[Fix]</font> Fixed various search issues

## v2.5.7 (265)
- <font color="#09868B">[Feature]</font> Initial support for [ADB over TCP](./guide/adb-over-tcp.md) (port 5555) for non-root users
- <font color="red">[Fix]</font> Fixed importing rules from [Watt][2] and [Blocker][3]
- <font color="red">[Fix]</font> Display Aurora Droid in [App Info][4] page as a first priority over F-Droid
- <font color="red">[Fix]</font> Improved performance for component blocking
- <font color="red">[Fix]</font> Fixed app op mode detection issue

**For root users:** If you've skipped [v2.5.6](#v2-5-6-233), you may need to apply all rules globally by applying [Global Component Blocking][5] in Settings in order for them to work.

## v2.5.6 (233)
- <font color="#09868B">[Feature]</font> [Batch operations](./guide/main-page.md#batch-operations) in the main page: clear app data, disable run in background, disable/kill/uninstall apps (click on the app icon to select)
- <font color="#09868B">[Feature]</font> Full support of [Blocker][3]'s exported files which was broken due to a bug in Blocker
- <font color="#09868B">[Feature]</font> Reimplementation of blocking activities, receivers, services and providers
- <font color="red">[Fix]</font> Removed ConstraintLayout dependency therefore a potential decrease in app size
- <font color="red">[Fix]</font> Fixed duplicate app usage warning in the [App Info][4] page
- <font color="red">[Fix]</font> Fixed crash when an app icon is not found in [App Details][1] page

**Note for root users:** In order to ensure that the previous blocking rules are preserved with the new blocking implementation, this update reads from the previous rules consequently increasing the loading time in the [Main page][7]. This feature will be removed in the next release but can still be simulated by applying [global component blocking][5] in Settings.

## v2.5.5 (215)
- <font color="#09868B">[Feature]</font> Added [Running Apps/Process Viewer](./guide/main-page.md#running-apps) (requires root)
- <font color="#09868B">[Feature]</font> Added [Usage Details Viewer][6]
- <font color="#09868B">[Feature]</font> Added [Apk Updater](./guide/main-page.md#apk-updater) and [Aurora Store](./guide/app-details-page.md#actions-in-app-info-tab) support
- <font color="#09868B">[Feature]</font> Save modified values of app ops and permissions to the disk (on progress)
- <font color="red">[Fix]</font> Uninstall support for non-root users
- <font color="red">[Fix]</font> Restructure app usage
- <font color="red">[Fix]</font> Added more clarity as well as improve performance in the [App Details][1] page

[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: ./guide/app-details-page.md#app-info-tab
[5]: ./guide/settings-page.md#global-component-blocking
[6]: ./guide/main-page.md#app-usage
[7]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
