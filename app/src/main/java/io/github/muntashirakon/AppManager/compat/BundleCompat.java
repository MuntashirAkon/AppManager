// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class BundleCompat {
    @SuppressWarnings("deprecation")
    @Nullable
    public static <T extends Parcelable> T getParcelable(@NonNull Bundle bundle, @Nullable String key, @NonNull Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, clazz);
        }
        return bundle.getParcelable(key);
    }
}
