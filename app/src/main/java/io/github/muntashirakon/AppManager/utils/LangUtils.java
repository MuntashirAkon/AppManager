// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.yariksoffice.lingver.Lingver;

import java.util.IllformedLocaleException;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";
    public static final String LANG_DEFAULT = "en";

    private static ArrayMap<String, Locale> sLocaleMap;

    public static void setAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) sLocaleMap = new ArrayMap<>();
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        String[] locales = context.getResources().getStringArray(R.array.languages_key);
        Locale appDefaultLocale = Locale.forLanguageTag(LANG_DEFAULT);

        for (String locale : locales) {
            conf.setLocale(Locale.forLanguageTag(locale));
            Context ctx = context.createConfigurationContext(conf);
            String langTag = ctx.getString(R.string._lang_tag);

            if (LANG_AUTO.equals(locale)) {
                sLocaleMap.put(LANG_AUTO, LocaleListCompat.getDefault().get(0));
            } else if (LANG_DEFAULT.equals(langTag)) {
                sLocaleMap.put(LANG_DEFAULT, appDefaultLocale);
            } else sLocaleMap.put(locale, ConfigurationCompat.getLocales(conf).get(0));
        }
    }

    @NonNull
    public static ArrayMap<String, Locale> getAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) setAppLanguages(context);
        return sLocaleMap;
    }

    @NonNull
    public static Locale getLocaleByLanguage(@NonNull Context context) {
        String language = AppPref.getLanguage(context);
        getAppLanguages(context);
        Locale locale = sLocaleMap.get(language);
        return locale != null ? locale : LocaleListCompat.getDefault().get(0);
    }

    public static boolean isValidLocale(@NonNull String languageTag) {
        try {
            Locale locale = Locale.forLanguageTag(languageTag);
            for (Locale validLocale : Locale.getAvailableLocales()) {
                if (validLocale.equals(locale)) {
                    return true;
                }
            }
        } catch (IllformedLocaleException ignore) {
        }
        return false;
    }

    @NonNull
    public static String getSeparatorString() {
        if (Lingver.getInstance().getLanguage().equals("fr")) return " : ";
        return ": ";
    }

    @NonNull
    public static ContextWrapper wrap(@NonNull Context context) {
        Resources res = context.getResources();
        Configuration configuration = res.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList.getDefault());
        } else {
            configuration.setLocale(LocaleListCompat.getDefault().get(0));
            DisplayMetrics dm = res.getDisplayMetrics();
            res.updateConfiguration(configuration, dm);
        }
        return new ContextWrapper(context.createConfigurationContext(configuration));
    }
}
