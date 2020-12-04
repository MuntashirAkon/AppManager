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
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import io.github.muntashirakon.AppManager.AppManager;

public class StoragePermission {
    @NonNull
    public static StoragePermission init(@NonNull ActivityResultCaller caller) {
        return new StoragePermission(caller);
    }

    public interface StoragePermissionCallback {
        void onResult(boolean granted);
    }

    private BetterActivityResult<String, Boolean> storagePerm;
    @RequiresApi(api = Build.VERSION_CODES.R)
    private BetterActivityResult<Intent, ActivityResult> storagePermApi30;

    private StoragePermission(@NonNull ActivityResultCaller caller) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storagePermApi30 = BetterActivityResult.registerForActivityResult(caller, new ActivityResultContracts.StartActivityForResult());
        } else {
            storagePerm = BetterActivityResult.registerForActivityResult(caller, new ActivityResultContracts.RequestPermission());
        }
    }

    public void request(@Nullable StoragePermissionCallback callback) {
        if (PermissionUtils.hasStoragePermission(AppManager.getContext())) {
            callback.onResult(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storagePermApi30.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    result -> callback.onResult(PermissionUtils.hasStoragePermission(AppManager.getContext())));
        } else {
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    result -> callback.onResult(PermissionUtils.hasStoragePermission(AppManager.getContext())));
        }
    }

    public void request() {
        request(null);
    }

}
