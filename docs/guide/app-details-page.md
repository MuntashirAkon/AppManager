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
- <code style="background-color: #FF8A80; color: #000">Light red (day)</code> or <code style="background-color: #4F1C14; color: #FFF">very dark red (night)</code> - Components that are disabled outside App Manager have these colors. It should be noted that a component that is marked as disabled does not always mean that it is disabled by the user: It could be disabled by the system as well or marked as disabled in the app manifest. Also, all components of a disabled app are also considered disabled by the system (and by the App Manager as well)
- <code style="background-color: #FF8017; color: #000">Vivid orange (day)</code> or <code style="background-color: #FF8017; color: #FFF">very dark orange (night)</code> - Tracker or ad components
- <code style="background-color: #EA80FC; color: #000">Soft magenta (day)</code> or <code style="background-color: #431C5D; color: #FFF">very dark violet (night)</code> - Currently running services

## App Info Tab
**App Info** tab contains general information about the app such as _app directories_, _data directories_, _split apk info_, _sdk versions_, _install date_, _last update date_, _installer app_, _data usage_, _app size_, _data size_, _cache size_, _number of tracking components_ (some of these information require _Usage Access_ permission). Many actions can also be performed here which are described below.

### Actions in App Info Tab
App Info tab has an horizontally-scrollable action panel where various actions are listed. Some actions are also available beside an info item such as paths and directories. Other actions are also avalable in the menu on the top-right corner. These actions include:
- Launch/install/uninstall/update/enable/disable/force-stop (some operations require root or [ADB][2])
- View app manifest
- View or edit shared preferences - Clicking on a preference opens the [Shared Preferences Editor][3] page (requires root)
- Scan for trackers using &#x03b5;xodus
- Open paths and directories with external app
- Delete app cache (root-only) or app data (root or [ADB][2])
- Export blocking rules
- Backup or restore an app along with its data
- View app info in system settings
- Open in F-Droid or Aurora Droid and Aurora Store (if installed)
- Save or share the apk file as apk, or apks if the app is a bundled app
- See the most honest **What's New** for apk files that needs update containing changes in application components and trackers, permission changes, etc.

## Other Tabs
Other tabs list application components such as activities, broadcast receivers, services,
providers as well as app operations (ie. app ops), permissions, features, configurations,
shared libraries and signatures. Most of these information actually come from the
application manifest. The manifest has two kinds of permission tags, _permission_ and
_uses-permission_, which is why there are two tabs for permissions in this page
(named accordingly).

With root or [ADB][2], any app op or any _dangerous_ permission can be granted or
revoked. With root, any app component (that is activities, broadcast receivers, services
and providers) can also be blocked via intent firewall (for activities, receivers and
services) or (for providers,) using the _pm_ command line tool. There is a toggle
button on the right side of each component item which can be used to block or unblock each
component, and there is a switch button next to each app op or permission item. By default,
when components are blocked using the block buttons, they are not immediately applied. To
apply them, _Apply Rules_ option in the menu has to be used. Another option is to
enable component blocking globally in the app settings. There is also an option to disable
all tracker components in the menu as well as sorting by blocking components. In this latter
case and for app ops and permissions, the changes are applied immediately. Activities that
are marked as _exported_ in the app manifest can be launched, or customizable launcher
icons or shortcuts can be created in the _Activities_ tab. For app ops, there is an
option to disable all dangerous app ops or reset them. For permissions, there is an option
to revoke all dangerous permissions as well. Like components, both app ops and permissions
can be sorted in different ways.

## See also
- [εxodus Page](./exodus-page.md)

[1]: ../tech/AppOps.md
[2]: ./adb-over-tcp.md
[3]: ./shared-pref-editor-page.md
