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
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";

    private static Map<String, Locale> sLocaleMap = new HashMap<>();
    private static Locale sDefaultLocale;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sDefaultLocale = LocaleList.getDefault().get(0);
        } else sDefaultLocale = Locale.getDefault();
        sLocaleMap.put("en", Locale.ENGLISH);
        sLocaleMap.put("de", Locale.GERMAN);
        sLocaleMap.put("pt-BR", new Locale("pt", "BR"));
        sLocaleMap.put("ru-RU", new Locale("ru", "RU"));
        sLocaleMap.put("zh-CN", new Locale("zh", "CN"));
    }

    public static Locale updateLanguage(@NonNull Context context) {
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        Locale currentLocale = getLocaleByLanguage();
        config.setLocale(currentLocale);
        DisplayMetrics dm = resources.getDisplayMetrics();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
            context.getApplicationContext().createConfigurationContext(config);
        } else {
            resources.updateConfiguration(config, dm);
        }
        return currentLocale;
    }

    public static Locale getLocaleByLanguage() {
        String language = (String) AppPref.get(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        if (LANG_AUTO.equals(language)) {
            return sDefaultLocale;
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
        Locale locale = getLocaleByLanguage();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(configuration);
    }
}
