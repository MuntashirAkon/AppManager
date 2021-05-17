// SPDX-License-Identifier: Apache-2.0

package android.content.pm.permission;

import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;

/**
 * Parcelable version of {@link android.permission.PermissionManager.SplitPermissionInfo}
 */
public class SplitPermissionInfoParcelable implements Parcelable {
    /**
     * The permission that is split.
     */
    @NonNull
    private final String mSplitPermission;

    /**
     * The permissions that are added.
     */
    @NonNull
    private final List<String> mNewPermissions;

    /**
     * The target API level when the permission was split.
     */
    @IntRange(from = 0)
    private final int mTargetSdk;

    private void onConstructed() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new SplitPermissionInfoParcelable.
     *
     * @param splitPermission The permission that is split.
     * @param newPermissions  The permissions that are added.
     * @param targetSdk       The target API level when the permission was split.
     */
    public SplitPermissionInfoParcelable(
            @NonNull String splitPermission,
            @NonNull List<String> newPermissions,
            @IntRange(from = 0) int targetSdk) {
        throw new UnsupportedOperationException();
    }

    /**
     * The permission that is split.
     */
    public @NonNull String getSplitPermission() {
        throw new UnsupportedOperationException();
    }

    /**
     * The permissions that are added.
     */
    public @NonNull
    List<String> getNewPermissions() {
        throw new UnsupportedOperationException();
    }

    /**
     * The target API level when the permission was split.
     */
    public @IntRange(from = 0)
    int getTargetSdk() {
        throw new UnsupportedOperationException();
    }
}