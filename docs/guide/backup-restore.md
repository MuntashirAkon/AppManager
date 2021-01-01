---
sidebarDepth: 2
---
# Backup/Restore
App Manager has a modern, advanced and easy-to-use backup/restore system implemented from the scratch. This is probably the only app that has the ability to restore not only the app or its data but also permissions and rules that you've configured within App Manager. You can also choose to backup an app multiple times (with custom names) or for all users.

::: tip Notice
Backup/Restore is completely different from **Backup APK** which is also a part of the batch operations.
:::

::: details Table of Contents
[[toc]]
:::

## Location
Backup/restore is a part of [batch operations][batch_ops]. It is also located inside the [options menu][app_info_menu] in the [App Info tab][app_info]. Clicking on **Backup/Restore** opens the **Backup Options**. The backups are currently located at <tt>/sdcard/AppManager</tt>.

::: tip Note
If one or more selected apps don't have any backup, the **Restore** and **Delete Backup** options will not be displayed.
:::

## Backup Options
Backup options (internally known as backup flags) let you customise the backups on the fly. However, the customisations will not be remembered for the future backups. If you want customise this dialog, use [Backup Options][settings_bo] in the [Settings page][settings].

A complete description of backup options is given below:
- **Source.** Whether to backup or restore the entire source directory. When you install an app, the APK files are stored inside the <tt>/data/app/</tt> along with any native libraries as well as some other files such as the ODEX and VDEX files. This directory is called **source directory** or **code path**. You can further customise this using the **APK only** option.
- **APK only.** When you enable **Source** option, the whole source directory is backed up or restored. Enabling this along with **Source** will only backup or restore the APK files and skip backing up the native libraries or ODEX and VDEX files.
- **Data.** Whether to back up the internal data directories. These directories are located at <tt>/data/user/<user_id></tt> and (for Android N or later) <tt>/data/user_de/<user_id></tt>.
- **External data.** Whether to back up data directories located in the internal memory as well as SD Card (if exists). External data directories often contain non-essential app data or media files (instead of using the dedicated media folder) and may increase the backup size. But it might be essential for some apps. Although it isn't checked by default (as it might dramatically increase the size of the backups), you will need to check it in order to ensure a smooth restore of your backups.
- **OBB and media.** Whether to back up or restore the OBB and the media directories located in the external storage or the SD Card. This is useful for games and some graphical software which actually use these folders.
- **Exclude cache.** Android apps have multiple cache directories located at every data directories (both internal and external). There are two types of cache: **cache** and **code cache**. Enabling this option excludes both cache directories from all the data directories. It is generally advised to exclude cache directories since most apps don't clear the cache (for some reason, the only way an app can clear its cache is by deleting the entire cache directory) and usually handled by the OS itself. Apps such as Telegram may use very large cache (depending on the storage space) which may dramatically increase your backup size. When it is enabled, AM also ignores backup from the **no_backup** directories.
- **Extras.** Whether to back up or restore app permissions, enabled by default. Note that, blocking rules are applied _after_ applying permissions. So, if a permission is also available in the blocking rules, it will be overwritten (i.e., the one from the blocking rules will be used).
- **Rules.** This option lets you back up blocking rules configured within App Manager. This might come in handy if you have customised permissions or block some components using App Manager as they will also be backed up or restored when you enable this option.
- **Backup Multiple.** Whether this is a multiple backup. By default backups are saved using their user ID. Enabling this option allows you to create additional backups. These backups use the current date-time as the default backup name but you can also specify custom backup name using the input field displayed when you click on the **Backup** button.
- **All users.** Backup or restore for all users instead of only the current user. _This option is obsolete and will be replaced with a more suitable option in future._
- **Skip signature checks.** When taking a backup, checksum of every file (as well as the signing certificate(s) of the base APK file) is generated and stored in the `checksums.txt` file. When you restore the backup, the checksums are generated again and are matched with the checksums stored in the said file. Enabling this option will disable the signature checks. This option is applied only when you restore a backup. During backup, the checksums are generated regardless of this option.
  ::: warning Caution
  You should always disable this option to ensure that you're backups are not modified by any third-party applications. But checksum verification only works if you enable encryption.
  :::
  ::: tip Notice
  App Manager doesn't yet support restoring Storage Access Framework (SAF) rules. You have to enable them manually after restoring a backup.
  :::

## Backup
Backup respects all the backup options except **Skip signature checks**. If base backups (i.e., backups that don't have the **Backup Multiple** option) already exist, you will get a warning as the backups will be overwritten. If **Backup Multiple** is set, you have an option to input the backup name or you can leave it blank to use the current date-time. 

## Restore
Restore respects all the backup options and will fail if **Source** option is set but the backup doesn't contain any source backups or in other cases, if the app isn't installed. When restoring backups for multiple packages, you can only restore the base backups (see [backup](#backup) section for an explanation). However, when restoring backups for a single package, you have the option to select which backup to restore. If **All users** option is set, AM will restore the selected backup for all users in the latter case but in the former case, it will restore base backups for the respective users.

## Delete Backup
Delete backup only respects **All users** option and when it is selected, only the base backups for all users will be deleted with a prompt. When deleting backups for a single package, another dialog will be displayed where you can select the backups to delete.

## Encryption
App Manager currently supports OpenPGP encryption. To enable it, you need to install an OpenPGP provider such as [OpenKeychain][open_keychain]. To configure OpenPGP provider, go to the [Settings page][settings].

[batch_ops]: ./main-page.md#batch-operations
[app_info]: ./app-details-page.md#app-info-tab
[app_info_menu]: ./app-details-page.md#options-menu
[settings]: ./settings-page.md
[settings_bo]: ./settings-page.md#backup-options
[open_keychain]: https://openkeychain.org
