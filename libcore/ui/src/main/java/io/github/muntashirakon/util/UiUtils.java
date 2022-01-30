// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Px;

public class UiUtils {
    @Px
    public static int dpToPx(@NonNull Context context, @Dimension int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    @Px
    public static int dpToPx(@NonNull Context context, @Dimension float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Px
    public static int spToPx(@NonNull Context context, @Dimension float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    @Dimension
    public static int pxToDp(@NonNull Context context, @Px int pixel) {
        return (int) ((float) pixel / context.getResources().getDisplayMetrics().density);
    }
}
