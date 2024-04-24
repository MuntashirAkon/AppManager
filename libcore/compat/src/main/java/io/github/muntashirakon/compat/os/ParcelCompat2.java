// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.os;

import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;

import androidx.annotation.NonNull;

public final class ParcelCompat2 {
    @NonNull
    public static Parcel obtain(@NonNull IBinder binder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Parcel.obtain(binder);
        }
        return Parcel.obtain();
    }
}
