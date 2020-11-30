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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;

import java.util.IllformedLocaleException;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import io.github.muntashirakon.AppManager.R;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";
    public static final String LANG_DEFAULT = "en";

    private static ArrayMap<String, Locale> sLocaleMap;
    private static final Locale sDefaultLocale;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sDefaultLocale = LocaleList.getDefault().get(0);
        } else sDefaultLocale = Locale.getDefault();
    }

    public static void setAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) sLocaleMap = new ArrayMap<>();
        DisplayMetrics metrics = new DisplayMetrics();
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        String[] locales = context.getResources().getStringArray(R.array.languages_key);
        Locale appDefaultLocale = Locale.forLanguageTag(LANG_DEFAULT);

        for (String locale : locales) {
            conf.locale = Locale.forLanguageTag(locale);
            Resources langRes = new Resources(context.getAssets(), metrics, conf);
            String langTag = langRes.getString(R.string._lang_tag);

            if (LANG_AUTO.equals(locale)) {
                sLocaleMap.put(LANG_AUTO, sDefaultLocale);
            } else if (LANG_DEFAULT.equals(langTag)) {
                sLocaleMap.put(LANG_DEFAULT, appDefaultLocale);
            } else sLocaleMap.put(locale, conf.locale);
        }
    }

    @NonNull
    public static ArrayMap<String, Locale> getAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) setAppLanguages(context);
        return sLocaleMap;
    }

    @NonNull
    public static Locale getLocaleByLanguage(@NonNull Context context) {
        String language = AppPref.getNewInstance(context).getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        getAppLanguages(context);
        Locale locale = sLocaleMap.get(language);
        return locale != null ? locale : sDefaultLocale;
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
}
