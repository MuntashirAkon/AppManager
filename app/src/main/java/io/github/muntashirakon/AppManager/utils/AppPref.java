package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

public class AppPref {
    private static final String PREF_NAME = "preferences";
    public static final Tuple<String, Boolean> PREF_ROOT_MODE_ENABLED = new Tuple<>("root_mode_enabled", true);
    public static final Tuple<String, Boolean> PREF_GLOBAL_BLOCKING_ENABLED = new Tuple<>("global_blocking_enabled", false);
    public static final Tuple<String, Boolean> PREF_USAGE_ACCESS_ENABLED = new Tuple<>("usage_access_enabled", true);

    @IntDef(value = {
            TYPE_BOOLEAN,
            TYPE_FLOAT,
            TYPE_INTEGER,
            TYPE_LONG,
            TYPE_STRING
    })
    public @interface Type {}
    public static final int TYPE_BOOLEAN = 0;
    public static final int TYPE_FLOAT   = 1;
    public static final int TYPE_INTEGER = 2;
    public static final int TYPE_LONG    = 3;
    public static final int TYPE_STRING  = 4;

    private static AppPref appPref;
    public static AppPref getInstance(Context context) {
        if (appPref == null) {
            appPref = new AppPref(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE));
        }
        return appPref;
    }

    private @NonNull SharedPreferences preferences;
    private @NonNull SharedPreferences.Editor editor;

    @SuppressLint("CommitPrefEdits")
    private AppPref(@NonNull SharedPreferences preferences) {
        this.preferences = preferences;
        editor = preferences.edit();
        init();
    }

    public Object getPref(String key, @Type int type) {
        if (!preferences.contains(key)) init();
        // Default values here doesn't matter as they're initialized already
        try {
            switch (type) {
                case TYPE_BOOLEAN:
                    return preferences.getBoolean(key, false);
                case TYPE_FLOAT:
                    return preferences.getFloat(key, 0.0f);
                case TYPE_INTEGER:
                    return preferences.getInt(key, 0);
                case TYPE_LONG:
                    return preferences.getLong(key, 0);
                case TYPE_STRING:
                    return preferences.getString(key, "");
            }
        } catch (ClassCastException ignore) {}
        return null;
    }

    public void setPref(String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        editor.commit();
    }

    private void init() {
        if (!preferences.contains(PREF_ROOT_MODE_ENABLED.getFirst())) {
            editor.putBoolean(PREF_ROOT_MODE_ENABLED.getFirst(), PREF_ROOT_MODE_ENABLED.getSecond());
        }
        if (!preferences.contains(PREF_GLOBAL_BLOCKING_ENABLED.getFirst())) {
            editor.putBoolean(PREF_GLOBAL_BLOCKING_ENABLED.getFirst(), PREF_GLOBAL_BLOCKING_ENABLED.getSecond());
        }
        if (!preferences.contains(PREF_USAGE_ACCESS_ENABLED.getFirst())) {
            editor.putBoolean(PREF_USAGE_ACCESS_ENABLED.getFirst(), PREF_USAGE_ACCESS_ENABLED.getSecond());
        }
        editor.commit();
    }
}
