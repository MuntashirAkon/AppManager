// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.os.ConfigurationCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class LangUtils {
    public static final String LANG_AUTO = "auto";

    private static Map<String, Locale> sLocaleMap;

    @SuppressLint("AppBundleLocaleChanges") // We don't use Play Store
    private static void loadAppLanguages(@NonNull Context context) {
        if (sLocaleMap == null) sLocaleMap = new LinkedHashMap<>();
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();

        sLocaleMap.put(LANG_AUTO, null);
        for (String locale : parseLocalesConfig(context)) {
            conf.setLocale(Locale.forLanguageTag(locale));
            sLocaleMap.put(locale, ConfigurationCompat.getLocales(conf).get(0));
        }
    }

    @NonNull
    public static Map<String, Locale> getAppLanguages(@NonNull Context context) {
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

    @NonNull
    public static List<String> parseLocalesConfig(@NonNull Context context) {
        List<String> localeNames = new ArrayList<>();

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.locales_config)) {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "locale".equals(parser.getName())) {
                    String localeName = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                    if (localeName != null) {
                        localeNames.add(localeName);
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException ignore) {
        }
        return localeNames;
    }
}
