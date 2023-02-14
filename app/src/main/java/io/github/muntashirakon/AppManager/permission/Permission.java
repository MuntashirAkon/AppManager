// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_POLICY_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;

import android.os.Build;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;

// Copyright (C) 2015 The Android Open Source Project
public class Permission {
    boolean runtime;
    boolean readOnly;

    private final String mName;
    private final int mAppOp;

    private boolean mGranted;
    private boolean mAppOpAllowed;
    @PermissionCompat.PermissionFlags
    private int mFlags;

    public Permission(String name, boolean granted, int appOp, boolean appOpAllowed,
                      @PermissionCompat.PermissionFlags int flags) {
        mName = name;
        mGranted = granted;
        mAppOp = appOp;
        mAppOpAllowed = appOpAllowed;
        mFlags = flags;
        runtime = true;
        readOnly = false;
    }

    public boolean isRuntime() {
        return runtime;
    }

    public boolean isReadOnly() {
        return readOnly || isSystemFixed();
    }

    public String getName() {
        return mName;
    }

    public int getAppOp() {
        return mAppOp;
    }

    @PermissionCompat.PermissionFlags
    public int getFlags() {
        return mFlags;
    }

    public boolean hasAppOp() {
        return mAppOp != AppOpsManagerCompat.OP_NONE;
    }

    /**
     * Does this permission affect app ops.
     *
     * <p>I.e. does this permission have a matching app op or is this a background permission. All
     * background permissions affect the app op if it's assigned foreground permission.
     *
     * @return {@code true} if this permission affects app ops
     */
    public boolean affectsAppOp() {
        return mAppOp != AppOpsManagerCompat.OP_NONE;
    }

    public boolean isGranted() {
        return mGranted;
    }

    public boolean isGrantedIncludingAppOp() {
        return mGranted && !isReviewRequired() && (!affectsAppOp() || isAppOpAllowed());
    }

    public boolean isReviewRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return (mFlags & FLAG_PERMISSION_REVIEW_REQUIRED) != 0;
        } else return false;
    }

    public void resetReviewRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mFlags &= ~FLAG_PERMISSION_REVIEW_REQUIRED;
        }
    }

    public void unsetReviewRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mFlags &= ~FLAG_PERMISSION_REVIEW_REQUIRED;
        }
    }

    public void setGranted(boolean mGranted) {
        this.mGranted = mGranted;
    }

    public boolean isAppOpAllowed() {
        return mAppOpAllowed;
    }

    public boolean isUserFixed() {
        return (mFlags & FLAG_PERMISSION_USER_FIXED) != 0;
    }

    public void setUserFixed(boolean userFixed) {
        if (userFixed) {
            mFlags |= FLAG_PERMISSION_USER_FIXED;
        } else {
            mFlags &= ~FLAG_PERMISSION_USER_FIXED;
        }
    }

    public boolean isSystemFixed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (mFlags & FLAG_PERMISSION_SYSTEM_FIXED) != 0;
        }
        return false;
    }

    public boolean isPolicyFixed() {
        return (mFlags & FLAG_PERMISSION_POLICY_FIXED) != 0;
    }

    public boolean isUserSet() {
        return (mFlags & FLAG_PERMISSION_USER_SET) != 0;
    }

    public boolean isGrantedByDefault() {
        return (mFlags & FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0;
    }

    public void setUserSet(boolean userSet) {
        if (userSet) {
            mFlags |= FLAG_PERMISSION_USER_SET;
        } else {
            mFlags &= ~FLAG_PERMISSION_USER_SET;
        }
    }

    public void setPolicyFixed(boolean policyFixed) {
        if (policyFixed) {
            mFlags |= FLAG_PERMISSION_POLICY_FIXED;
        } else {
            mFlags &= ~FLAG_PERMISSION_POLICY_FIXED;
        }
    }

    public boolean shouldRevokeOnUpgrade() {
        return (mFlags & FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0;
    }

    public void setRevokeOnUpgrade(boolean revokeOnUpgrade) {
        if (revokeOnUpgrade) {
            mFlags |= FLAG_PERMISSION_REVOKE_ON_UPGRADE;
        } else {
            mFlags &= ~FLAG_PERMISSION_REVOKE_ON_UPGRADE;
        }
    }

    public boolean isRevokedCompat() {
        return (mFlags & FLAG_PERMISSION_REVOKED_COMPAT) != 0;
    }

    public void setRevokedCompat(boolean revokedCompat) {
        if (revokedCompat) {
            mFlags |= FLAG_PERMISSION_REVOKED_COMPAT;
        } else {
            mFlags &= ~FLAG_PERMISSION_REVOKED_COMPAT;
        }
    }

    public void setAppOpAllowed(boolean mAppOpAllowed) {
        this.mAppOpAllowed = mAppOpAllowed;
    }
}
