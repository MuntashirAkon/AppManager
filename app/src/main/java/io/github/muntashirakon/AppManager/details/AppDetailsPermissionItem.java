/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.details;

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
