// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Px;

import java.util.Locale;

import io.github.muntashirakon.text.style.ListSpan;

public final class UiUtils {
    private UiUtils() {
    }

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

    public static void hideKeyboard(@NonNull View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /**
     * Get a well-formatted list from the given list of CharSequences. Example:
     * <pre>
     * ["A single-line list-item", "A multi-line\nlist-item"]
     * </pre>
     * The above will be translated as follows:
     * <pre>
     * 1  A single-line list-item
     * 2  A multi-line
     *    list-item
     * </pre>
     *
     * @param List List of CharSequences
     * @return Formatted list
     */
    @NonNull
    public static <T extends CharSequence> Spanned getOrderedList(@NonNull Iterable<T> List) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        Locale locale = Locale.getDefault();
        Spannable spannable;
        int j = 0;
        for (CharSequence charSequence : List) {
            int len = charSequence.length();
            spannable = new SpannableString(charSequence);
            int finish = spannable.toString().indexOf("\n");
            spannable.setSpan(new ListSpan(40, 30, ++j, locale), 0, (finish == -1 ? len : finish),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (finish != -1) {
                spannable.setSpan(new LeadingMarginSpan.Standard(40 + 30), finish + 1, len,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            spannableStringBuilder.append(spannable).append("\n");
        }
        return spannableStringBuilder;
    }
}
