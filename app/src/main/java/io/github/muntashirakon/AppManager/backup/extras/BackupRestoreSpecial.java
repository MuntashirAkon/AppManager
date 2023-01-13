// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public abstract class BackupRestoreSpecial {
    @NonNull
    protected final Context context;
    protected final ContentResolver cr;

    protected BackupRestoreSpecial(@NonNull Context context) {
        this.context = context;
        this.cr = context.getContentResolver();
    }

    public abstract void backup(@NonNull Writer out) throws IOException;

    public abstract void restore(@NonNull Reader in) throws IOException;
}
