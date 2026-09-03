// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Describes the outcome of a controller state change
 */
public final class PermissionChangeResult {
    public enum Status {
        SUCCESS,
        UNSUPPORTED,
        FAILURE,
        USER_ACTION_REQUIRED
    }

    @NonNull
    private final Status mStatus;
    @Nullable
    private final String mMessage;
    @Nullable
    private final Throwable mCause;
    @Nullable
    private final PermissionUserAction mUserAction;

    private PermissionChangeResult(@NonNull Status status, @Nullable String message,
                                   @Nullable Throwable cause,
                                   @Nullable PermissionUserAction userAction) {
        mStatus = status;
        mMessage = message;
        mCause = cause;
        mUserAction = userAction;
    }

    @NonNull
    public static PermissionChangeResult success() {
        return new PermissionChangeResult(Status.SUCCESS, null, null, null);
    }

    @NonNull
    public static PermissionChangeResult unsupported(@NonNull String message) {
        return new PermissionChangeResult(Status.UNSUPPORTED, message, null, null);
    }

    @NonNull
    public static PermissionChangeResult failure(@NonNull String message,
                                                 @Nullable Throwable cause) {
        return new PermissionChangeResult(Status.FAILURE, message, cause, null);
    }

    @NonNull
    public static PermissionChangeResult userActionRequired(
            @NonNull String message, @NonNull PermissionUserAction userAction) {
        return new PermissionChangeResult(Status.USER_ACTION_REQUIRED, message, null, userAction);
    }

    @NonNull
    public Status getStatus() {
        return mStatus;
    }

    public boolean isSuccessful() {
        return mStatus == Status.SUCCESS;
    }

    @Nullable
    public String getMessage() {
        return mMessage;
    }

    @Nullable
    public Throwable getCause() {
        return mCause;
    }

    @Nullable
    public PermissionUserAction getUserAction() {
        return mUserAction;
    }
}
