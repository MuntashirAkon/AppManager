package net.dongliu.apk.parser.utils;

import java.util.Locale;

/**
 * @author dongliu
 */
public class Locales {

    /**
     * when do localize, any locale will match this
     */
    public static final Locale any = new Locale("", "");

    /**
     * How much the given locale match the expected locale.
     */
    public static int match(Locale locale, Locale targetLocale) {
        if (locale == null) {
            return -1;
        }
        if (locale.getLanguage().equals(targetLocale.getLanguage())) {
            if (locale.getCountry().equals(targetLocale.getCountry())) {
                return 3;
            } else if (targetLocale.getCountry().isEmpty()) {
                return 2;
            } else {
                return 0;
            }
        } else if (targetLocale.getCountry().isEmpty() || targetLocale.getLanguage().isEmpty()) {
            return 1;
        } else {
            return 0;
        }
    }
}
