// SPDX-License-Identifier: GPL-3.0-or-later

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
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

public class UIUtils {
    static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

    @NonNull
    public static Spannable getHighlightedText(@NonNull String text, @NonNull String constraint,
                                               int color) {
        Spannable spannable = sSpannableFactory.newSpannable(text);
        int start = text.toLowerCase(Locale.ROOT).indexOf(constraint);
        if (start == -1) return spannable;
        int end = start + constraint.length();
        if (end > text.length()) return spannable;
        spannable.setSpan(new BackgroundColorSpan(color), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getColoredText(@NonNull CharSequence text, int color) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new ForegroundColorSpan(color), 0, spannable.length(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getMonospacedText(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new TypefaceSpan("monospace"), 0, spannable.length(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getPrimaryText(@NonNull Context context, @NonNull CharSequence text) {
        return getColoredText(text, getTextColorPrimary(context));
    }

    @NonNull
    public static Spannable getPrimaryText(@NonNull Context context, @StringRes int strRes) {
        return getPrimaryText(context, context.getText(strRes));
    }

    @NonNull
    public static Spannable getStyledKeyValue(@NonNull Context context, @StringRes int keyRes, CharSequence value) {
        return getStyledKeyValue(context, context.getText(keyRes), value);
    }

    @NonNull
    public static Spannable getStyledKeyValue(@NonNull Context context, CharSequence key, CharSequence value) {
        return getStyledKeyValue(context, key, value, LangUtils.getSeparatorString());
    }

    @NonNull
    public static Spannable getStyledKeyValue(@NonNull Context context,
                                              CharSequence key,
                                              CharSequence value,
                                              CharSequence separator) {
        return new SpannableStringBuilder(getPrimaryText(context, new SpannableStringBuilder(key).append(separator)))
                .append(value);
    }

    @NonNull
    public static Spannable getSecondaryText(@NonNull Context context, @NonNull CharSequence text) {
        return getColoredText(text, getTextColorSecondary(context));
    }

    @NonNull
    public static Spannable getTitleText(Context context, @NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new AbsoluteSizeSpan(getTitleSize(context)), 0, spannable.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return getColoredText(spannable, getTextColorPrimary(context));
    }

    @NonNull
    public static Spannable getTitleText(Context context, @StringRes int strRes) {
        return getTitleText(context, context.getText(strRes));
    }

    @NonNull
    public static Spannable getSmallerText(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new RelativeSizeSpan(.8f), 0, spannable.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getUnderlinedString(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new UnderlineSpan(), 0, spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getBoldString(@NonNull CharSequence text) {
        Spannable spannable = charSequenceToSpannable(text);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    public static Spannable getItalicString(@NonNull CharSequence text) {
        Spannable ss = sSpannableFactory.newSpannable(text);
        ss.setSpan(new StyleSpan(Typeface.ITALIC), 0, ss.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
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

    @Px
    public static int dpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    @Px
    public static int dpToPx(@NonNull Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Px
    public static int spToPx(@NonNull Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    @Dimension
    public static int pxToDp(@NonNull Context context, int pixel) {
        return (int) ((float) pixel / context.getResources().getDisplayMetrics().density);
    }

    @UiThread
    @NonNull
    public static View getDialogTitle(@NonNull FragmentActivity activity, @NonNull CharSequence title,
                                      @Nullable Drawable drawable, @Nullable CharSequence subtitle) {
        return new DialogTitleBuilder(activity).setTitle(title).setSubtitle(subtitle).setStartIcon(drawable).build();
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
    public static AlertDialog getProgressDialog(@NonNull FragmentActivity activity, @StringRes int text) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_progress, null);
        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(text);
        return new MaterialAlertDialogBuilder(activity)
                .setCancelable(false)
                .setView(view)
                .create();
    }

    @NonNull
    public static SearchView setupSearchView(@NonNull ActionBar actionBar,
                                             @Nullable SearchView.OnQueryTextListener queryTextListener) {
        SearchView searchView = new SearchView(actionBar.getThemedContext());
        searchView.setId(R.id.action_search);
        searchView.setOnQueryTextListener(queryTextListener);
        // Set layout params
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.END;
        actionBar.setCustomView(searchView, layoutParams);
        return searchView;
    }

    @NonNull
    public static AdvancedSearchView setupAdvancedSearchView(@NonNull ActionBar actionBar,
                                                             @Nullable AdvancedSearchView.OnQueryTextListener queryTextListener) {
        AdvancedSearchView searchView = new AdvancedSearchView(actionBar.getThemedContext());
        searchView.setId(R.id.action_search);
        searchView.setOnQueryTextListener(queryTextListener);
        // Set layout params
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.END;
        actionBar.setCustomView(searchView, layoutParams);
        return searchView;
    }

    @UiThread
    public static void displayShortToast(@StringRes int res) {
        Toast.makeText(AppManager.getContext(), res, Toast.LENGTH_SHORT).show();
    }

    @UiThread
    public static void displayShortToast(@StringRes int res, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getString(res, args), Toast.LENGTH_SHORT).show();
    }

    @UiThread
    public static void displayLongToast(CharSequence message) {
        Toast.makeText(AppManager.getContext(), message, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public static void displayLongToast(@StringRes int res) {
        Toast.makeText(AppManager.getContext(), res, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public static void displayLongToast(@StringRes int res, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getString(res, args), Toast.LENGTH_LONG).show();
    }

    @UiThread
    public static void displayLongToastPl(@PluralsRes int res, int count, Object... args) {
        Context appContext = AppManager.getContext();
        Toast.makeText(appContext, appContext.getResources().getQuantityString(res, count, args), Toast.LENGTH_LONG).show();
    }

    @NonNull
    public static Spannable charSequenceToSpannable(@NonNull CharSequence text) {
        if (text instanceof Spannable) {
            return (Spannable) text;
        } else return sSpannableFactory.newSpannable(text);
    }
}
