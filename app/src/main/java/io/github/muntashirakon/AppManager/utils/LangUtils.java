// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;

import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
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
                sLocaleMap.put(LANG_AUTO, null);
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
    public static Locale getFromPreference(@NonNull Context context) {
        String language = AppPref.getLanguage(context);
        getAppLanguages(context);
        Locale locale = sLocaleMap.get(language);
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

    @NonNull
    public static ContextWrapper wrapSystem(@NonNull Context context) {
        Resources res = Resources.getSystem();
        Configuration configuration = res.getConfiguration();
        return new ContextWrapper(context.createConfigurationContext(configuration));
    }

    public static Locale applyLocaleToActivity(Activity activity) {
        Locale locale = applyLocale(activity);
        // Update title
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0);
            if (info.labelRes != 0) {
                activity.setTitle(info.labelRes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Update menu
        activity.invalidateOptionsMenu();
        return locale;
    }

    public static Locale applyLocale(Context context) {
        return applyLocale(context, LangUtils.getFromPreference(context));
    }

    private static Locale applyLocale(@NonNull Context context, @NonNull Locale locale) {
        updateResources(context, locale);
        Context appContext = context.getApplicationContext();
        if (appContext != context) {
            updateResources(appContext, locale);
        }
        return locale;
    }

    private static void updateResources(@NonNull Context context, @NonNull Locale locale) {
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        //noinspection deprecation
        Locale current = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? conf.getLocales().get(0) : conf.locale;

        if (current == locale) {
            return;
        }

        conf = new Configuration(conf);
        // Set locale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setLocaleApi24(conf, locale);
        } else {
            conf.setLocale(locale);
        }
        // Reset layout direction from the preferences
        switch (AppPref.getLayoutOrientation()) {
            case View.LAYOUT_DIRECTION_RTL:
                conf.setLayoutDirection(Locale.forLanguageTag("ar"));
                break;
            case View.LAYOUT_DIRECTION_LTR:
                conf.setLayoutDirection(Locale.ENGLISH);
        }
        //noinspection deprecation
        res.updateConfiguration(conf, res.getDisplayMetrics());
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private static void setLocaleApi24(@NonNull Configuration config, @NonNull Locale locale) {
        LocaleList defaultLocales = LocaleList.getDefault();
        LinkedHashSet<Locale> locales = new LinkedHashSet<>(defaultLocales.size() + 1);
        // Bring the target locale to the front of the list
        // There's a hidden API, but it's not currently used here.
        locales.add(locale);
        for (int i = 0; i < defaultLocales.size(); ++i) {
            locales.add(defaultLocales.get(i));
        }
        config.setLocales(new LocaleList(locales.toArray(new Locale[0])));
    }
}
