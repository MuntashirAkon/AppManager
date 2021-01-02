/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm.permission;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

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