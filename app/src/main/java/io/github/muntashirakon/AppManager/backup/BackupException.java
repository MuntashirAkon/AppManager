// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;

public class BackupException extends Throwable {
    @NonNull
    private final String mDetailMessage;

    public BackupException(@NonNull String message) {
        super(message);
        mDetailMessage = message;
    }

    public BackupException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
        mDetailMessage = message;
    }

    @NonNull
    @Override
    public String getMessage() {
        return mDetailMessage;
    }
}
