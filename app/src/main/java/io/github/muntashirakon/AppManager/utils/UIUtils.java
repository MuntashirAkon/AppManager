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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;

public class UIUtils {
    static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

    @NonNull
    public static Spannable getHighlightedText(@NonNull String text, @NonNull String constraint,
                                               int color) {
        Spannable spannable = sSpannableFactory.newSpannable(text);
        int start = text.toLowerCase(Locale.ROOT).indexOf(constraint);
        int end = start + constraint.length();
        spannable.setSpan(new BackgroundColorSpan(color), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getBiggerText(@NonNull Spannable spannable) {
        spannable.setSpan(new RelativeSizeSpan(1.2f), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getUnderlinedString(@NonNull Spannable spannable) {
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getBoldString(@NonNull String text) {
        Spannable ss = sSpannableFactory.newSpannable(text);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, ss.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    @NonNull
    public static Spannable getItalicString(@NonNull String text) {
        Spannable ss = sSpannableFactory.newSpannable(text);
        ss.setSpan(new StyleSpan(Typeface.ITALIC), 0, ss.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    @TargetApi(29)
    public static int getSystemColor(@NonNull Context context, int resAttrColor) { // Ex. android.R.attr.colorPrimary
        // Get accent color
        TypedValue typedValue = new TypedValue();
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context,
                android.R.style.Theme_DeviceDefault_DayNight);
        contextThemeWrapper.getTheme().resolveAttribute(resAttrColor, typedValue, true);
        return typedValue.data;
    }

    public static int getThemeColor(@NonNull Context context, int resAttrColor) { // Ex. android.R.attr.colorPrimary
        // Get accent color
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(resAttrColor, typedValue, true);
        return typedValue.data;
    }

    public static int getAccentColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.colorAccent);
    }

    public static int getPrimaryColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.colorPrimary);
    }

    public static int dpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
