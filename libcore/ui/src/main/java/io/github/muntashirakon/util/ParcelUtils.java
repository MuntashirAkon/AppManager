// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.os.Parcel;

import androidx.annotation.NonNull;

public class ParcelUtils {
    public static void writeBoolean(boolean b, @NonNull Parcel dest) {
        dest.writeInt(b ? 1 : 0);
    }

    public static boolean readBoolean(@NonNull Parcel in) {
        return in.readInt() != 0;
    }
}
