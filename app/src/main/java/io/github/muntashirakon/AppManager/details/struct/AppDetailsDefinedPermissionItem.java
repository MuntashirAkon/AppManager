// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;

public class AppDetailsDefinedPermissionItem extends AppDetailsItem<PermissionInfo> {
    public final boolean isExternal;

    public AppDetailsDefinedPermissionItem(@NonNull PermissionInfo permissionInfo, boolean isExternal) {
        super(permissionInfo);
        this.isExternal = isExternal;
    }
}
