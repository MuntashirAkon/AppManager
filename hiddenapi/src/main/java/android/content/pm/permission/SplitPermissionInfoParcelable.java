// SPDX-License-Identifier: Apache-2.0

package android.content.pm.permission;

import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;

import misc.utils.HiddenUtil;

/**
 * Parcelable version of {@link android.permission.PermissionManager.SplitPermissionInfo}
 */
public class SplitPermissionInfoParcelable implements Parcelable {
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
        HiddenUtil.throwUOE(splitPermission, newPermissions, targetSdk);
    }

    /**
     * The permission that is split.
     */
    @NonNull
    public String getSplitPermission() {
        return HiddenUtil.throwUOE();
    }

    /**
     * The permissions that are added.
     */
    @NonNull
    public List<String> getNewPermissions() {
        return HiddenUtil.throwUOE();
    }

    /**
     * The target API level when the permission was split.
     */
    @IntRange(from = 0)
    public int getTargetSdk() {
        return HiddenUtil.throwUOE();
    }
}