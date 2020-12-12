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

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public final class PermissionUtils {
    public static final String TERMUX_PERM_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    public static final String PERMISSION_GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";

    public static boolean hasDumpPermission() {
        Context context = AppManager.getContext();
        if (hasPermission(context, Manifest.permission.DUMP)) {
            Runner.Result result = RunnerUtils.grantPermission(context.getPackageName(), Manifest.permission.DUMP, Users.getCurrentUserHandle());
            return result.isSuccessful();
        } else return false;
    }

    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasTermuxPermission(Context context) {
        return hasPermission(context, TERMUX_PERM_RUN_COMMAND);
    }

    public static boolean hasAppOpsPermission(Context context) {
        return hasPermission(context, PERMISSION_GET_APP_OPS_STATS);
    }

    public static boolean hasPermission(Context context, String permissionName) {
        return ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED;
    }
}
