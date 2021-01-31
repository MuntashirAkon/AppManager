---
sidebarDepth: 2
---
# Profile Page
Profile page displays the configurations for a profile. It also offers editing them.

::: tip Notice
When you apply a profile, if some packages do not match the criteria, they will simply be ignored.
:::

::: details Table of Contents
[[toc]]
:::

## Options Menu
The three dots menu on the top-right corner opens the options menu. It contains several options such as—
- **Apply.**  This option can be used to apply the profile. When clicked, a dialog will be displayed where you can select a [profile state](#state). On selecting one of the options, the profile will be applied immediately.
- **Save.** Allows you to save the profile.
  ::: warning Notice
  Changes are never saved automatically. You have to save them manually from here.
  :::
- **Discard.** Discard any modifications made since the last save.
- **Delete.** Clicking on delete will remove the profile immediately without any warning.
- **Duplicate.** This option can be used to duplicate the profile. When clicked, an input box will be displayed where you can set the profile name. If you click "OK", a new profile will be created and the page will be reloaded. The profile will not be saved until you save it manually.
- **Create shortcut.** This option can be used to create a shortcut for the profile. When clicked, there will be two options: _Simple_ and _Advanced_. The latter option allows you to set the [profile state](#state) before applying it while the former option use the default state that was configured when the profile was last saved.

## Apps Tab
Apps tab lists the packages configured under this profile. Packages can be added or removed using the _plus_ button located near the bottom of the screen. Packages can also be removed by long clicking on them (in which case, a popup will be displayed with the only option _delete_).

## Configurations Tab
Configurations tab can be used to configure the selected packages. Description of each item is given below:

### Comment
This is the text that will be displayed in the [profiles page][profiles]. If not set, the current configurations will be displayed instead.

### State
Denotes how certain configured options will behave. For instance, if _disable_ option is turned on, the apps will be disabled if the state is _on_ and will be enabled if the state is _off_. Currently state only support _on_ and _off_ values.

### Users
Select users for which is the profile will be applied. All users are selected by default.

### Components
This behaves the same way as the [Block Components…][block_components] option does in the 1-Click Ops page. However, this only applies for the selected packages. If the [state](#state) is _on_, the components will be blocked, and if the [state](#state) is _off_, the components will be unblocked. The option can be disabled (regardless of the inserted values) by clicking on the _disabled_ button on the input dialog.

_See also: [What are the app components?][what_are_components]_

### App Ops
This behaves the same way as the [Set Mode for App Ops…][set_mode_for_app_ops] option does in the 1-Click Ops page. However, this only applies for the selected packages. If the [state](#state) is _on_, the app ops will be denied (ie. ignored), and if the [state](#state) is _off_, the app ops will be allowed. The option can be disabled (regardless of the inserted values) by clicking on the _disabled_ button on the input dialog.

### Permissions
This option can be used to grant or revoke certain permissions from the selected packages. Like others above, permissions must be separated by spaces. If the [state](#state) is _on_, the permissions will be revoked, and if the [state](#state) is _off_, the permissions will be allowed. The option can be disabled (regardless of the inserted values) by clicking on the _disabled_ button on the input dialog.

### Backup/Restore
This option can be used to take a backup of the selected apps and its data or restore them. There two options available there: _Backup options_ and _backup name_.
- **Backup options.** Same as the [backup options][backup_options] of the backup/restore feature. If not set, the default options will be used.
- **Backup name.** Set a custom name for the backup. If the backup name is set, each time a backup is made, it will be given a unique name with backup-name as the suffix. This behaviour will be fixed in a future release. Leave this field empty for regular or "base" backup (also, make sure not to enable _backup multiple_ in the backup options).

If the [state](#state) is _on_, the packages will be backed up, and if the [state](#state) is _off_, the packages will be restored. The option can be disabled by clicking on the _disabled_ button on the input dialog.

### Export Blocking Rules
This option allows you to export blocking rules.

### Disable
Enabling this option will enable/disable the selected packages depending on the [state](#state). If the [state](#state) is _on_, the packages will be disabled, and if the [state](#state) is _off_, the packages will be enabled.

### Force-stop
Enabling this option will allow the selected packages to be force-stopped.

### Clear Cache
Enabling this option will enable clearing cache for the selected packages.

### Clear Data
Enabling this option will enable clearing data for the selected packages.

### Block Trackers
Enabling this option will block/unblock tracker components from the selected packages depending on the [state](#state). If the [state](#state) is _on_, the trackers will be blocked, and if the [state](#state) is _off_, the trackers will be unblocked.

### Backup APK
Enabling this option will enable APK backup for the selected packages. This is not the same as [backup/restore][backup_restore] as described there.


[profiles]: ./profiles-page.md
[block_components]: ./one-click-ops-page.md#block-components
[what_are_components]: ../faq/app-components.md#what-are-the-app-components
[set_mode_for_app_ops]: ./one-click-ops-page.md#set-mode-for-app-ops
[backup_options]: ./backup-restore.md#backup-options
[backup_restore]: ./backup-restore.md
