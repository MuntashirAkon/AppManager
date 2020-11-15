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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";

    private static Map<String, Locale> sLocaleMap;
    private static final Locale sDefaultLocale;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sDefaultLocale = LocaleList.getDefault().get(0);
        } else sDefaultLocale = Locale.getDefault();
    }

    public static Locale updateLanguage(@NonNull Context context) {
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        Locale currentLocale = getLocaleByLanguage(context);
        config.setLocale(currentLocale);
        DisplayMetrics dm = resources.getDisplayMetrics();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
            context.getApplicationContext().createConfigurationContext(config);
        } else {
            resources.updateConfiguration(config, dm);
        }
        return currentLocale;
    }

    public static Locale getLocaleByLanguage(Context context) {
        String language = AppPref.getNewInstance(context).getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        if (sLocaleMap == null) {
            String[] languages = context.getResources().getStringArray(R.array.languages_key);
            sLocaleMap = new HashMap<>(languages.length);
            for (String lang : languages) {
                if (LANG_AUTO.equals(lang)) {
                    sLocaleMap.put(LANG_AUTO, sDefaultLocale);
                } else {
                    String[] langComponents = lang.split("-", 2);
                    if (langComponents.length == 1) {
                        sLocaleMap.put(lang, new Locale(langComponents[0]));
                    } else if (langComponents.length == 2) {
                        sLocaleMap.put(lang, new Locale(langComponents[0], langComponents[1]));
                    } else {
                        Log.d("LangUtils", "Invalid language: " + lang);
                        sLocaleMap.put(LANG_AUTO, sDefaultLocale);
                    }
                }
            }
        }
        Locale locale = sLocaleMap.get(language);
        return locale != null ? locale : sDefaultLocale;
    }

    public static Context attachBaseContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResources(context);
        } else {
            return context;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResources(@NonNull Context context) {
        Resources resources = context.getResources();
        Locale locale = getLocaleByLanguage(context);
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(configuration);
    }

    public static boolean isValidLocale(String languageTag) {
        try {
            Locale locale = new Locale.Builder().setLanguageTag(languageTag).build();
            for (Locale validLocale : Locale.getAvailableLocales()) {
                if (validLocale.equals(locale)) {
                    return true;
                }
            }
        } catch (IllformedLocaleException ignore) {}
        return false;
    }
}
