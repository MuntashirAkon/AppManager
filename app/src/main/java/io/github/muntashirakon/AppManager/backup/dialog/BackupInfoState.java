// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
        BackupInfoState.NONE,
        BackupInfoState.BACKUP_MULTIPLE,
        BackupInfoState.RESTORE_MULTIPLE,
        BackupInfoState.BOTH_MULTIPLE,
        BackupInfoState.BACKUP_SINGLE,
        BackupInfoState.RESTORE_SINGLE,
        BackupInfoState.BOTH_SINGLE,
})
@Retention(RetentionPolicy.SOURCE)
public @interface BackupInfoState {
    /**
     * None of the selected apps have backups nor any of them is installed.
     */
    int NONE = 0;
    /**
     * None of the selected apps have backups but some of them are installed.
     */
    int BACKUP_MULTIPLE = 1;
    /**
     * None of the apps are installed but a few have (base) backups.
     */
    int RESTORE_MULTIPLE = 2;
    /**
     * Some apps are installed and some apps have (base) backups.
     */
    int BOTH_MULTIPLE = 3;
    /**
     * The app is installed but has no backups
     */
    int BACKUP_SINGLE = 4;
    /**
     * The apps is uninstalled but has backups
     */
    int RESTORE_SINGLE = 5;
    /**
     * Some apps are installed and some apps have (base) backups.
     */
    int BOTH_SINGLE = 6;
}
