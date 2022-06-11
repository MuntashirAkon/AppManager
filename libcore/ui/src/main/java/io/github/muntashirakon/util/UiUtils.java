// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.internal.ViewUtils;

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

    /**
     * Wrapper around {@link androidx.core.view.OnApplyWindowInsetsListener} which also passes the
     * initial padding/margin set on the view. Used with {@link #doOnApplyWindowInsets(View,
     * OnApplyWindowInsetsListener)}.
     */
    public interface OnApplyWindowInsetsListener {

        /**
         * When {@link View#setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener) set} on a
         * View, this listener method will be called instead of the view's own {@link
         * View#onApplyWindowInsets(WindowInsets)} method. The {@code initial*} is the view's
         * original padding/margin which can be updated and will be applied to the view automatically. This
         * method should return a new {@link WindowInsetsCompat} with any insets consumed.
         */
        WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets, @NonNull Rect initialPadding,
                                               @Nullable Rect initialMargin);
    }

    public static void applyWindowInsetsAsPaddingNoTop(View v) {
        doOnApplyWindowInsets(v, (view, insets, initialPadding, initialMargin) -> {
            if (!ViewCompat.getFitsSystemWindows(view)) {
                // Do not add padding if fitsSystemWindows is false
                return insets;
            }
            int top = initialPadding.top;
            int bottom = initialPadding.bottom + insets.getSystemWindowInsetBottom();

            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int systemWindowInsetLeft = insets.getSystemWindowInsetLeft();
            int systemWindowInsetRight = insets.getSystemWindowInsetRight();
            int start;
            int end;
            if (isRtl) {
                start = initialPadding.right + systemWindowInsetRight;
                end = initialPadding.left + systemWindowInsetLeft;
            } else {
                start = initialPadding.left + systemWindowInsetLeft;
                end = initialPadding.right + systemWindowInsetRight;
            }
            ViewCompat.setPaddingRelative(view, start, top, end, bottom);
            return insets;
        });
    }

    public static void applyWindowInsetsAsMargin(View v) {
        applyWindowInsetsAsMargin(v, true, true);
    }

    public static void applyWindowInsetsAsMargin(View v, boolean bottomMargin, boolean topMargin) {
        doOnApplyWindowInsets(v, (view, insets, initialPadding, initialMargin) -> {
            if (initialMargin == null || !ViewCompat.getFitsSystemWindows(view)) {
                // Do not add padding if fitsSystemWindows is false
                return insets;
            }
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
                return insets;
            }
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;

            if (topMargin) {
                marginLayoutParams.topMargin = initialMargin.top + insets.getSystemWindowInsetTop();
            }
            if (bottomMargin) {
                marginLayoutParams.bottomMargin = initialMargin.bottom + insets.getSystemWindowInsetBottom();
            }
            marginLayoutParams.leftMargin = initialMargin.left + insets.getSystemWindowInsetLeft();
            marginLayoutParams.rightMargin = initialMargin.right + insets.getSystemWindowInsetRight();

            view.setLayoutParams(marginLayoutParams);
            return insets;
        });
    }

    /**
     * Wrapper around {@link androidx.core.view.OnApplyWindowInsetsListener} that records the initial
     * margin of the view and requests that insets are applied when attached.
     */
    @SuppressLint("RestrictedApi")
    public static void doOnApplyWindowInsets(@NonNull View view, @NonNull OnApplyWindowInsetsListener listener) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        Rect initialMargins;
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            // Create a snapshot of the view's margin state.
            initialMargins = new Rect(marginLayoutParams.leftMargin, marginLayoutParams.topMargin,
                    marginLayoutParams.rightMargin, marginLayoutParams.bottomMargin);
        } else initialMargins = null;
        Rect initialPadding = new Rect(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                view.getPaddingBottom());
        // Set an actual OnApplyWindowInsetsListener which proxies to the given callback, also passing
        // in the original margin state.
        ViewCompat.setOnApplyWindowInsetsListener(view, (view1, insets) ->
                listener.onApplyWindowInsets(view1, insets, initialPadding, initialMargins));
        // Request some insets
        ViewUtils.requestApplyInsetsWhenAttached(view);
    }
}
