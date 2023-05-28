// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;

import java.util.IllformedLocaleException;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";
    public static final String LANG_DEFAULT = "en";

    private static ArrayMap<String, Locale> sLocaleMap;

    @SuppressLint("AppBundleLocaleChanges") // We don't use Play Store
    private static void loadAppLanguages(@NonNull Context context) {
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
                sLocaleMap.put(LANG_AUTO, null);
            } else if (LANG_DEFAULT.equals(langTag)) {
                sLocaleMap.put(LANG_DEFAULT, appDefaultLocale);
            } else sLocaleMap.put(locale, ConfigurationCompat.getLocales(conf).get(0));
        }
    }

    @NonNull
    public static ArrayMap<String, Locale> getAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) loadAppLanguages(context);
        return sLocaleMap;
    }

    @NonNull
    public static Locale getFromPreference(@NonNull Context context) {
        String language = Prefs.Appearance.getLanguage(context);
        Locale locale = getAppLanguages(context).get(language);
        if (locale != null) {
            return locale;
        }
        // Load from system configuration
        Configuration conf = Resources.getSystem().getConfiguration();
        //noinspection deprecation
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? conf.getLocales().get(0) : conf.locale;
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
        if (Locale.getDefault().getLanguage().equals(new Locale("fr").getLanguage())) {
            return " : ";
        }
        return ": ";
    }
}
