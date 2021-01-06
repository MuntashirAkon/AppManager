package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;

class BackupException extends Throwable {
    @NonNull
    private final String detailMessage;

    public BackupException(@NonNull String message) {
        super(message);
        this.detailMessage = message;
    }

    public BackupException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
        detailMessage = message;
    }

    @NonNull
    @Override
    public String getMessage() {
        return detailMessage;
    }
}
