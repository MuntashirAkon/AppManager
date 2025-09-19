// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import androidx.annotation.IntDef;

@IntDef({BackupCategories.CAT_SRC, BackupCategories.CAT_INT_CE, BackupCategories.CAT_INT_DE,
        BackupCategories.CAT_EXT, BackupCategories.CAT_OBB, BackupCategories.CAT_UNK})
public @interface BackupCategories {
    int CAT_SRC = 0;
    int CAT_INT_CE = 1;
    int CAT_INT_DE = 2;
    int CAT_EXT = 3;
    int CAT_OBB = 4;
    int CAT_UNK = 5;
}
