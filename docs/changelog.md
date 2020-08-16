---
sidebar: auto
---
# Changelog

::: details Table of Contents
[[toc]]
:::

## v2.5.11 (333)
- <TagFeature/> Added experimental support for app data backup. Please test only on apps you don't need. (root only)
- <TagFeature/> Added sharing split apk files as apks (can be installed via [SAI][8]).
- <TagFeature/> Implemented saving apk files in batch selection mode.
- <TagFeature/> Added what's new for apk file that needs an update (when opening external apk files).
- <TagFeature/> Added an option to apply 1-click ops to system apps (disabled by default).
- <TagFeature/> Added installed app version info in the App Info tab. Clicking the _i_ icon opens the installed [App Info][4] tab.
- <TagFeature/> New on-demand permissions <tt>READ_EXTERNAL_STORAGE</tt> & <tt>WRITE_EXTERNAL_STORAGE</tt> for app backup support
- <TagFeature/> Display apps that are uninstalled but have backups in the main page
- <TagFeature/> Added a disclaimer
- <TagFix/> Fixed selections being not cleared after the task is completed in the main page
- <TagFix/> Convert various info in the configurations and features tab to text to improve readability
- <TagFix/> Fix crash in the [Main page][7] while filtering apps by search query
- <TagFix/> Fix crash in the [App Info][4] tab when existence of external data directory has false-positive result

**Note:** Backup data are stored at `/sdcard/AppManager` and apk backups are stored at `/sdcard/AppManager/apks`. Data backups are currently not working on Android Lollipop.

## v2.5.10 (324)
- <TagFeature/> Added 1-click operations (as [1-Click Ops](./guide/one-click-ops-page.md) in the menu section in the [Main page][7]): block (ads and) trackers, component blocking by signatures, app op blocking
- <TagFeature/> Added support for external apk: You can now open apk files from your file manager. You can view app details, manifest or scan for trackers directly from there
- <TagFeature/> Added persistent apps filtering option in the [Main page][7]
- <TagFeature/> Alternative manifest viewer for installed apks
- <TagFeature/> Display number of trackers as a tag in the [App Info][4] tab
- <TagFeature/> Added a select all option in the bottom bar in the [Main page][7] in selection mode
- <TagFeature/> Added links to source code and community
- <TagFeature/> Added support for installing/updating apk files in the [App Info][4] tab (incomplete)
- <TagFeature/> Added an option to import existing disabled components in the Import/Export settings (incomplete)
- <TagFeature/> Added split apk information in [App Info][4] tab
- <TagFeature/> Added an option to open [Termux](./guide/main-page.md#termux) in the [Main page][7] (incomplete)
- <TagFeature/> Initial support for app banner
- <TagFix/> Fixed inconsistency of enable and disable in the App Info tab
- <TagFix/> Fixed issue with persistent app cache
- <TagFix/> Fixed scrolling issue on settings page
- <TagFix/> Fixed crashes when switching to the components tabs for non-root users
- <TagFix/> Fixed crash when trying to view summary while scanning is still in progress in the exodus page
- <TagFix/> Fixed crashes on devices that does not support data usage
- <TagFix/> Fixed crash when trying to view manifest of an split apk
- <TagFix/> Fixed wrong package installer name in the [App Info][4] tab
- <TagFix/> Fixed changelog formatting for old devices

## v2.5.9 (315)
- <TagFeature/> Merged [App Info][4] as a single tab in [App Details][1]
- <TagFeature/> Added option to reset all app ops
- <TagFeature/> Added option to revoke all dangerous app ops/permissions
- <TagFeature/> Highlight trackers in the component tabs
- <TagFeature/> Added option to save manifest and class dump
- <TagFeature/> Added the ability to grant/revoke development permissions
- <TagFeature/> Added sorting options for components, app ops and uses permissions tabs
- <TagFeature/> Added sort by wifi usage in the [App Usage][6] page
- <TagFeature/> Added launch button in the [App Info][4] tab
- <TagFeature/> Added never ask option to usage status prompt
- <TagFeature/> Added long click to select apps in the [Main page][7]
- <TagFeature/> Added changelog within the app
- <TagFix/> Click to select apps during selection mode
- <TagFix/> Improved component blocker
- <TagFix/> Improved manifest loading for large apps
- <TagFix/> Improved tab loading performance
- <TagFix/> Fixed app ops checking and custom app ops for some devices
- <TagFix/> Disabled activity opening for disabled activities
- <TagFix/> Get real activity name for activities that use activity-alias
- <TagFix/> Fixed background colors
- <TagFix/> Fixed crashing when loading the services tab for non-root users
- <TagFix/> Fixed back button for class viewer which was not working
- <TagFix/> Changed block icon's colour to accent colour
- <TagFix/> Removed translation until the app is complete
- <TagFix/> Made links in the credit section clickable
- <TagFix/> Fixed various memory leaks

## v2.5.8 (289)
- <TagFeature/> Added [import/export capabilities for blocking rules](./guide/settings-page.md#import-export-blocking-rules)
- <TagFeature/> Added ability to [select themes](./guide/settings-page.html#app-theme) (night/day)
- <TagFeature/> Added mode, duration, accept time, reject time for app ops
- <TagFeature/> Highlight running services
- <TagFeature/> Highlight disabled components not disabled within App Manager
- <TagFeature/> Added swipe to refresh in the [App Usage][6] page
- <TagFeature/> Added screen time percentage with indicator
- <TagFeature/> Separate instructions and about pages with fullscreen dialog for both
- <TagFeature/> Rounded overflow menu (still incomplete)
- <TagFix/> Fixed various device/SDK specific app ops issues
- <TagFix/> Stability improvements of the entire apps
- <TagFix/> Added <tt>ACCESS_NETWORK_STATE</tt> permission to support older operating systems
- <TagFix/> Fixed deleting all IFW rules when selecting [Global Component Blocking][5]
- <TagFix/> Fixed various search issues

## v2.5.7 (265)
- <TagFeature/> Initial support for [ADB over TCP](./guide/adb-over-tcp.md) (port 5555) for non-root users
- <TagFix/> Fixed importing rules from [Watt][2] and [Blocker][3]
- <TagFix/> Display Aurora Droid in [App Info][4] page as a first priority over F-Droid
- <TagFix/> Improved performance for component blocking
- <TagFix/> Fixed app op mode detection issue

**For root users:** If you've skipped [v2.5.6](#v2-5-6-233), you may need to apply all rules globally by applying [Global Component Blocking][5] in Settings in order for them to work.

## v2.5.6 (233)
- <TagFeature/> [Batch operations](./guide/main-page.md#batch-operations) in the main page: clear app data, disable run in background, disable/kill/uninstall apps (click on the app icon to select)
- <TagFeature/> Full support of [Blocker][3]'s exported files which was broken due to a bug in Blocker
- <TagFeature/> Reimplementation of blocking activities, receivers, services and providers
- <TagFix/> Removed ConstraintLayout dependency therefore a potential decrease in app size
- <TagFix/> Fixed duplicate app usage warning in the [App Info][4] page
- <TagFix/> Fixed crash when an app icon is not found in [App Details][1] page

**Note for root users:** In order to ensure that the previous blocking rules are preserved with the new blocking implementation, this update reads from the previous rules consequently increasing the loading time in the [Main page][7]. This feature will be removed in the next release but can still be simulated by applying [global component blocking][5] in Settings.

## v2.5.5 (215)
- <TagFeature/> Added [Running Apps/Process Viewer](./guide/main-page.md#running-apps) (requires root)
- <TagFeature/> Added [Usage Details Viewer][6]
- <TagFeature/> Added [Apk Updater](./guide/main-page.md#apk-updater) and [Aurora Store](./guide/app-details-page.md#actions-in-app-info-tab) support
- <TagFeature/> Save modified values of app ops and permissions to the disk (on progress)
- <TagFix/> Uninstall support for non-root users
- <TagFix/> Restructure app usage
- <TagFix/> Added more clarity as well as improve performance in the [App Details][1] page

[1]: ./guide/app-details-page.md
[2]: https://github.com/tuyafeng/Watt
[3]: https://github.com/lihenggui/blocker
[4]: ./guide/app-details-page.md#app-info-tab
[5]: ./guide/settings-page.md#global-component-blocking
[6]: ./guide/main-page.md#app-usage
[7]: ./guide/main-page.md
[8]: https://github.com/Aefyr/SAI
