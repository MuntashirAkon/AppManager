// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.Context;

import androidx.annotation.NonNull;

public final class BackupRestoreSpecials {
    @NonNull
    public static BackupRestoreSpecial getCallLogs(@NonNull Context context) {
        return new BackupRestoreCallLogs(context);
    }

    @NonNull
    public static BackupRestoreSpecial getContacts(@NonNull Context context) {
        return new BackupRestoreContacts(context);
    }

    @NonNull
    public static BackupRestoreSpecial getMessages(@NonNull Context context) {
        return new BackupRestoreMessages(context);
    }
}
