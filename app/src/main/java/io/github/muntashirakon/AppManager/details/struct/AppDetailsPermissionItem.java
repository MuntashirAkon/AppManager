// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;

/**
 * Stores individual app details item
 */
public class AppDetailsPermissionItem extends AppDetailsItem {
    public boolean isDangerous = false;
    public boolean isGranted = false;
    public int flags = 0;
    public int appOp = AppOpsManager.OP_NONE;

    public AppDetailsPermissionItem(@NonNull PermissionInfo object) {
        super(object);
    }

    public AppDetailsPermissionItem(@NonNull AppDetailsPermissionItem object) {
        super(object.vanillaItem);
        name = object.name;
        isDangerous = object.isDangerous;
        isGranted = object.isGranted;
        flags = object.flags;
        appOp = object.appOp;
    }
}
