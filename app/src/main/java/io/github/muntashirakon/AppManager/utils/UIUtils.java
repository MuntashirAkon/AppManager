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
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.widget.Toast;
import androidx.annotation.*;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.AppManager;
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
    public static Spannable getSecondaryText(@NonNull Context context, @NonNull CharSequence text) {
        return getColoredText(text, getTextColorSecondary(context));
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

    public static int getTextColorSecondary(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.textColorSecondary);
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

    @UiThread
    @NonNull
    public static View getDialogTitle(@NonNull FragmentActivity activity, @NonNull CharSequence title,
                                      @Nullable Drawable drawable, @Nullable CharSequence subtitle) {
        View appLabelWithVersionView = activity.getLayoutInflater().inflate(R.layout.dialog_title_with_icon, null);
        ImageView iv = appLabelWithVersionView.findViewById(R.id.icon);
        if (drawable != null) {
            iv.setImageDrawable(drawable);
        } else {
            iv.setVisibility(View.GONE);
        }
        SpannableStringBuilder fullTitle = new SpannableStringBuilder(getBoldString(getTitleText(activity, title)));
        if (subtitle != null) {
            fullTitle.append("\n").append(getSmallerText(getSecondaryText(activity, subtitle)));
        }
        ((TextView) appLabelWithVersionView.findViewById(R.id.title)).setText(fullTitle);
        return appLabelWithVersionView;
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
    public static SearchView setupSearchView(@NonNull Context context, @NonNull ActionBar actionBar,
                                             @Nullable SearchView.OnQueryTextListener queryTextListener) {
        SearchView searchView = new SearchView(actionBar.getThemedContext());
        searchView.setOnQueryTextListener(queryTextListener);
        searchView.setQueryHint(context.getString(R.string.search));
        // Set images
        ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button)).setColorFilter(getAccentColor(context));
        ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn)).setColorFilter(getAccentColor(context));
        // Set layout params
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.END;
        actionBar.setCustomView(searchView, layoutParams);
        return searchView;
    }

    public static void displayShortToast(@StringRes int res) {
        Toast.makeText(AppManager.getContext(), res, Toast.LENGTH_SHORT).show();
    }

    public static void displayShortToast(@StringRes int res, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getString(res, args), Toast.LENGTH_SHORT).show();
    }

    public static void displayLongToast(@StringRes int res) {
        Toast.makeText(AppManager.getContext(), res, Toast.LENGTH_LONG).show();
    }

    public static void displayLongToast(@StringRes int res, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getString(res, args), Toast.LENGTH_LONG).show();
    }

    public static void displayLongToastPl(@PluralsRes int res, int count, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getResources().getQuantityString(res, count, args), Toast.LENGTH_LONG).show();
    }

    @NonNull
    private static Spannable charSequenceToSpannable(@NonNull CharSequence text) {
        if (text instanceof Spannable) {
            return (Spannable) text;
        } else return sSpannableFactory.newSpannable(text);
    }
}
