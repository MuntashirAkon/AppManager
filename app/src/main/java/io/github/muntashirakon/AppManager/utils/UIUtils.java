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
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
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
    public static Spannable getColoredText(@NonNull CharSequence text, int color) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new ForegroundColorSpan(color), 0, spannable.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getPrimaryText(@NonNull Context context, @NonNull CharSequence text) {
        return getColoredText(text, getTextColorPrimary(context));
    }

    @NonNull
    public static Spannable getTitleText(Context context, @NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new AbsoluteSizeSpan(getTitleSize(context)), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return getColoredText(spannable, getTextColorPrimary(context));
    }

    @NonNull
    public static Spannable getSmallerText(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new RelativeSizeSpan(.8f), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getUnderlinedString(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new UnderlineSpan(), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getBoldString(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getItalicString(@NonNull CharSequence text) {
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

    public static int getTextColorPrimary(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.textColorPrimary);
    }

    public static int getTitleSize(@NonNull Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.title_font);
    }

    public static int getSubtitleSize(@NonNull Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.subtitle_font);
    }

    public static int dpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    public static int dpToPx(@NonNull Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static int spToPx(@NonNull Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    @NonNull
    public static MaterialAlertDialogBuilder getDialogWithScrollableTextView(@NonNull FragmentActivity activity, CharSequence text, boolean linkify) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
        MaterialTextView textView = view.findViewById(R.id.content);
        textView.setText(text);
        if (linkify) Linkify.addLinks(textView, Linkify.ALL);
        return new MaterialAlertDialogBuilder(activity).setView(view);
    }

    @NonNull
    public static AlertDialog getProgressDialog(@NonNull FragmentActivity activity) {
        return getProgressDialog(activity, null);
    }

    @NonNull
    public static AlertDialog getProgressDialog(@NonNull FragmentActivity activity, @Nullable CharSequence text) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_progress, null);
        if (text != null) {
            TextView tv = view.findViewById(android.R.id.text1);
            tv.setText(text);
        }
        return new MaterialAlertDialogBuilder(activity)
                .setCancelable(false)
                .setView(view)
                .create();
    }

    @NonNull
    private static Spannable charSequenceToSpannable(@NonNull CharSequence text) {
        if (text instanceof Spannable) {
            return (Spannable) text;
        } else return sSpannableFactory.newSpannable(text);
    }
}
