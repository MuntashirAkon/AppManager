// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;

public class TypefaceUtil {
    public static final String TAG = TypefaceUtil.class.getSimpleName();

    private static final Map<String, Typeface> sOverriddenFonts = new HashMap<>();

    public static void replaceFontsWithSystem(@NonNull Context context) {
        String normalFont = getSystemFontFamily(context);
        if (normalFont == null) {
            Log.i(TAG, "No system font exists. Skip applying font overrides.");
            return;
        }

        try {
            overrideFonts(normalFont);
        } catch (Exception e) {
            Log.w(TAG, "Could not override fonts", e);
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    @SuppressWarnings({"JavaReflectionMemberAccess", "unchecked"})
    public static void restoreFonts() {
        if (sOverriddenFonts.isEmpty()) {
            return;
        }
        try {
            final Field field = Typeface.class.getDeclaredField("sSystemFontMap");
            field.setAccessible(true);
            Map<String, Typeface> allFontsForThisApp = (Map<String, Typeface>) field.get(null);
            if (allFontsForThisApp == null) {
                Log.i(TAG, "No fonts are set for this app. Weird!");
                return;
            }
            for (Map.Entry<String, Typeface> entry : sOverriddenFonts.entrySet()) {
                if (entry.getValue() == null) {
                    // Delete this entry
                    allFontsForThisApp.remove(entry.getKey());
                } else {
                    // Replace
                    allFontsForThisApp.put(entry.getKey(), entry.getValue());
                }
            }
            field.set(null, allFontsForThisApp);
            field.setAccessible(false);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore fonts", e);
        }
    }

    @Nullable
    private static String getSystemFontFamily(@NonNull Context context) {
        ContextThemeWrapper themedContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            themedContext = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight);
        } else {
            themedContext = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault);
        }
        TypedArray ta = themedContext.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault,
                new int[]{android.R.attr.fontFamily});
        String value = ta.getString(0);
        ta.recycle();
        return value;
    }

    @SuppressLint("DiscouragedPrivateApi")
    @SuppressWarnings({"JavaReflectionMemberAccess", "unchecked"})
    private static void overrideFonts(@NonNull String normalFont) throws Exception {
        final Field field = Typeface.class.getDeclaredField("sSystemFontMap");
        field.setAccessible(true);
        Map<String, Typeface> allFontsForThisApp = (Map<String, Typeface>) field.get(null);
        if (allFontsForThisApp == null) {
            Log.i(TAG, "No fonts are set for this app. Weird!");
            return;
        }
        Map<String, Typeface> fontsMap = new HashMap<String, Typeface>() {{
            // Fortunately for us, normalFont is always the basic font.
            // We can find other fonts by checking whether they starts with normalFont- and
            // append the substring to sans-serif
            List<String> fontFamilies = new ArrayList<>(allFontsForThisApp.keySet());
            for (String fontFamily : fontFamilies) {
                if (fontFamily.startsWith(normalFont)) {
                    Typeface typeface = allFontsForThisApp.get(fontFamily);
                    if (fontFamily.equals(normalFont)) {
                        put("sans-serif", typeface);
                    } else {
                        String suffix = fontFamily.substring(normalFont.length());
                        if (suffix.contains("-medium")) {
                            // For some reason, material themes use medium instead of bold for bold
                            // fonts. We need to check if a bold font exist for this font. If there
                            // is one, we'll use that font instead of the medium font.
                            String s = normalFont + suffix.replace("-medium", "-bold");
                            if (fontFamilies.contains(s)) {
                                typeface = allFontsForThisApp.get(s);
                            }
                        }
                        put("sans-serif" + suffix, typeface);
                    }
                }
            }
        }};
        // Store overridden fonts
        if (sOverriddenFonts.isEmpty()) {
            for (String fontFamily : fontsMap.keySet()) {
                sOverriddenFonts.put(fontFamily, allFontsForThisApp.get(fontFamily));
            }
        }
        allFontsForThisApp.putAll(fontsMap);
        field.set(null, allFontsForThisApp);
        field.setAccessible(false);
    }
}
