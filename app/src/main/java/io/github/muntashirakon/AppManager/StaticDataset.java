// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.debloat.SuggestionObject;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class StaticDataset {
    private static String[] sTrackerCodeSignatures;
    private static String[] sTrackerNames;
    private static List<DebloatObject> sDebloatObjects;

    public static final String ARMEABI_V7A = "armeabi_v7a";
    public static final String ARM64_V8A = "arm64_v8a";
    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";

    public static Map<String, String> ALL_ABIS = new HashMap<>();

    static {
        ALL_ABIS.put(ARMEABI_V7A, VMRuntime.ABI_ARMEABI_V7A);
        ALL_ABIS.put(ARM64_V8A, VMRuntime.ABI_ARM64_V8A);
        ALL_ABIS.put(X86, VMRuntime.ABI_X86);
        ALL_ABIS.put(X86_64, VMRuntime.ABI_X86_64);
    }

    public static final String LDPI = "ldpi";
    public static final String MDPI = "mdpi";
    public static final String TVDPI = "tvdpi";
    public static final String HDPI = "hdpi";
    public static final String XHDPI = "xhdpi";
    public static final String XXHDPI = "xxhdpi";
    public static final String XXXHDPI = "xxxhdpi";

    public static final ArrayMap<String, Integer> DENSITY_NAME_TO_DENSITY = new ArrayMap<String, Integer>(8) {
        {
            put(LDPI, DisplayMetrics.DENSITY_LOW);
            put(MDPI, DisplayMetrics.DENSITY_MEDIUM);
            put(TVDPI, DisplayMetrics.DENSITY_TV);
            put(HDPI, DisplayMetrics.DENSITY_HIGH);
            put(XHDPI, DisplayMetrics.DENSITY_XHIGH);
            put(XXHDPI, DisplayMetrics.DENSITY_XXHIGH);
            put(XXXHDPI, DisplayMetrics.DENSITY_XXXHIGH);
        }
    };
    public static final int DEVICE_DENSITY;

    static {
        DEVICE_DENSITY = Resources.getSystem().getDisplayMetrics().densityDpi;
    }

    public static final Map<String, Integer> LOCALE_RANKING = new HashMap<>();

    static {
        LocaleListCompat localeList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
        for (int i = 0; i < localeList.size(); i++) {
            LOCALE_RANKING.put(Objects.requireNonNull(localeList.get(i)).getLanguage(), i);
        }
    }

    public static String[] getTrackerCodeSignatures() {
        if (sTrackerCodeSignatures == null) {
            sTrackerCodeSignatures = ContextUtils.getContext().getResources().getStringArray(R.array.tracker_signatures);
        }
        return sTrackerCodeSignatures;
    }

    public static String[] getTrackerNames() {
        if (sTrackerNames == null) {
            sTrackerNames = ContextUtils.getContext().getResources().getStringArray(R.array.tracker_names);
        }
        return sTrackerNames;
    }

    @WorkerThread
    public static List<DebloatObject> getDebloatObjects() {
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(ContextUtils.getContext(), new Gson());
        }
        return sDebloatObjects;
    }

    @WorkerThread
    public static List<DebloatObject> getDebloatObjectsWithInstalledInfo(@NonNull Context context) {
        AppDb appDb = new AppDb();
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(context, new Gson());
        }
        for (DebloatObject debloatObject : sDebloatObjects) {
            debloatObject.fillInstallInfo(context, appDb);
        }
        return sDebloatObjects;
    }

    @NonNull
    @WorkerThread
    private static List<DebloatObject> loadDebloatObjects(@NonNull Context context, @NonNull Gson gson) {
        HashMap<String, List<SuggestionObject>> idSuggestionObjectsMap = loadSuggestions(context, gson);
        String jsonContent = FileUtils.getContentFromAssets(context, "debloat.json");
        try {
            List<DebloatObject> debloatObjects = Arrays.asList(gson.fromJson(jsonContent, DebloatObject[].class));
            for (DebloatObject debloatObject : debloatObjects) {
                List<SuggestionObject> suggestionObjects = idSuggestionObjectsMap.get(debloatObject.getSuggestionId());
                debloatObject.setSuggestions(suggestionObjects);
            }
            return debloatObjects;
        } catch (Throwable e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @NonNull
    @WorkerThread
    private static HashMap<String, List<SuggestionObject>> loadSuggestions(@NonNull Context context, @NonNull Gson gson) {
        String jsonContent = FileUtils.getContentFromAssets(context, "suggestions.json");
        HashMap<String, List<SuggestionObject>> idSuggestionObjectsMap = new HashMap<>();
        try {
            SuggestionObject[] suggestionObjects = gson.fromJson(jsonContent, SuggestionObject[].class);
            if (suggestionObjects != null) {
                for (SuggestionObject suggestionObject : suggestionObjects) {
                    List<SuggestionObject> objects = idSuggestionObjectsMap.get(suggestionObject.suggestionId);
                    if (objects == null) {
                        objects = new ArrayList<>();
                        idSuggestionObjectsMap.put(suggestionObject.suggestionId, objects);
                    }
                    objects.add(suggestionObject);
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return idSuggestionObjectsMap;
    }
}
