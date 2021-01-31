---
sidebarDepth: 2
---
# Settings Page
Settings can be used to customise the behaviour of the app.

::: details Table of Contents
[[toc]]
:::

## Language
Configure in-app language. App Manager current supports 12 (twelve) languages.

## App Theme
Configure in-app theme.

## Mode of Operation
You can select one of the four options:
- **Auto.** Let AM select the suitable option for you. Although this is the default option, it may cause problems in some devices.
- **Root.** Select root mode. AM will request for root permission if not already granted. But when selected, AM will run in root mode even if you don't allow root. This may cause crashes, therefore, you shouldn't enable it if you haven't granted root.
- **ADB over TCP.** Enable [ADB over TCP][1] mode. AM may hang indefinitely if you haven't enabled ADB over TCP.
- **No-root.** AM runs in no-root mode. AM will perform better if this is enabled but all the root/ADB features will be disabled.

## Usage Access
Turning off this option disables the **App Usage** page as well as _data usage_ and _app storage info_ in the [App Info tab][3]. With this option turned off, App Manager will never ask for _Usage Access_ permission.

## APK Signing
### Signature schemes
Select the [signature schemes](https://source.android.com/security/apksigning) to use. It is recommended that you use at least v1 and v2 signature schemes. Use the _Reset to Default_ button in case you're confused.

## Rules

### Global Component Blocking
Enable component blocking globally. By default, blocking rules are not applied unless they are applied in the [App Details][2] page for any package. Upon enabling this option, all (old and new) rules are applied immediately for all apps without explicitly enabling blocking for any app.

::: warning Notice
Enabling this setting may have some unintended side-effects, such as rules that are not completely removed will get applied. So, proceed with caution. This option should be kept disabled if not required for some reasons.
:::

_See also: [What is global component blocking?][7]_

### Import/Export Blocking Rules
It is possible to import or export blocking rules within App Manager for all apps. There is a choice to export or import only certain rules (components, app ops or permissions) instead of all of them. It is also possible to import blocking rules from [Blocker][4] and [Watt][5]. If it is necessary to export blocking rules for a single app, use the corresponding [App Details][2] page to export rules, or for multiple apps, use [batch operations][6].

_See also: [App Manager: Rules Specification][rules_spec]_

#### Export
Export blocking rules for all apps configured within App Manager. This may include [app components][what_are_components], app ops and permissions based on what options is/are selected in the multichoice options.

#### Import
Import previously exported blocking rules from App Manager. Similar to export, this may include [app components][what_are_components], app ops and permissions based on what options is/are selected in the multi-choice options.

#### Import Existing Rules
Add components disabled by other apps to App Manager. App Manager only keeps track of component disabled within App Manager. If you use other tools to block app components, you can use this tools to import these disabled components. Clicking on this option triggers a search for disabled components and will lists apps with components disabled by user. For safety, all the apps are unselected by default. You can manually select the apps in the list and re-apply the blocking through App Manager.

::: danger Caution
Be careful when using this tool as there can be many false positives. Choose only the apps that you are certain about.
:::

#### Import from Watt
Import configuration files from [Watt][5], each file containing rules for a single package and file name being the name of the package with `.xml` extension.

::: tip
Location of configuration files in Watt: <tt>/sdcard/Android/data/com.tuyafeng.watt/files/ifw</tt>
:::

#### Import from Blocker
Import blocking rules from [Blocker][4], each file containing rules for a single package. These files have a `.json` extension.

### Remove all rules
One-click option to remove all rules configured within App Manager. This will enable all blocked components, app ops will be set to their default values and permissions will be granted.

## Installer
Installer specific options

### Show users in installer
For root/ADB users, a list of users will be displayed before installing the app. The app will be installed only for the specified user (or all users if selected).

### Sign APK
Whether to sign the APK files before installing the app. See [APK signing](#apk-signing) section above to configure signing.

### Install location
Choose APK install location. This can be one of _auto_, _internal only_ and _prefer external_. Depending on Android version, the last option may not always install the app in the external storage.

### Installer App
Select the installer app, useful for some apps which explicitly checks for installer. This only works for root/ADB users.

## Backup/Restore
Settings related to [backup/restore][backup_restore].

### Compression method
Set which compression method to be used during backups. App Manager supports GZip and BZip2 compression methods, GZip being the default compression method. It doesn't affect the restore of an existing backup.

### Backup Options
Customise the backup/restore dialog.

### Backup apps with Android KeyStore
Allow backup of apps that has entries in the Android KeyStore (disabled by default). Some apps (such as Signal) may crash if restored. KeyStore backup also doesn't work from Android 9 but still kept as many apps having KeyStore can be restored without problem.

### Encryption
Set an encryption method for the backups. AM currently supports OpenPGP only.

::: warning Caution
In v2.5.16, App Manager doesn't remember key IDs for a particular backup. You have to remember them yourself. This has been fixed in v2.5.18.
:::

## Device Info
Display Android version, security, CPU, GPU, battery, memory, screen, languages, user info, etc. 

[1]: ./adb-over-tcp.md
[2]: ./app-details-page.md
[3]: ./app-details-page.md#app-info-tab
[4]: https://github.com/lihenggui/blocker
[5]: https://github.com/tuyafeng/Watt
[6]: ./main-page.md#batch-operations
[7]: ../faq/app-components.md#what-is-global-component-blocking
[what_are_components]: ../faq/app-components.md#what-are-the-app-components
[rules_spec]: ../tech/rules-specification.md
[backup_restore]: ./backup-restore.md
