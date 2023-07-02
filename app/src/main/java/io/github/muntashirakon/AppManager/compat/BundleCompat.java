// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class BundleCompat {
    @Nullable
    public static <T extends Parcelable> T getParcelable(@NonNull Bundle bundle, @Nullable String key, @NonNull Class<T> clazz) {
        return androidx.core.os.BundleCompat.getParcelable(bundle, key, clazz);
    }
}
