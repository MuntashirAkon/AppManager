// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;
import io.github.muntashirakon.AppManager.debloat.DebloaterListOptions;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.fm.FmListOptions;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.settings.Ops;

public class AppPref {
    private static final String PREF_NAME = "preferences";

    private static final int PREF_SKIP = 5;

    /**
     * Preference keys. It's necessary to do things manually as the shared prefs in Android is
     * literary unusable.
     * <br/>
     * Keep these in sync with {@link #getDefaultValue(PrefKey)}.
     */
    @Keep
    public enum PrefKey {
        PREF_APP_OP_SHOW_DEFAULT_BOOL,
        PREF_APP_OP_SORT_ORDER_INT,
        PREF_APP_THEME_INT,
        PREF_APP_THEME_CUSTOM_INT,
        // This is just a placeholder to prevent crash
        PREF_APP_THEME_PURE_BLACK_BOOL,
        // We store this in plain text because if the attackers attack us, they can also attack the other apps
        PREF_AUTHORIZATION_KEY_STR,

        PREF_BACKUP_ANDROID_KEYSTORE_BOOL,
        PREF_BACKUP_COMPRESSION_METHOD_STR,
        PREF_BACKUP_FLAGS_INT,
        PREF_BACKUP_VOLUME_STR,

        PREF_COMPONENTS_SORT_ORDER_INT,
        PREF_CONCURRENCY_THREAD_COUNT_INT,
        PREF_CUSTOM_LOCALE_STR,

        PREF_DISPLAY_CHANGELOG_BOOL,
        PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG,

        PREF_DEBLOATER_FILTER_FLAGS_INT,

        PREF_ENABLE_KILL_FOR_SYSTEM_BOOL,
        PREF_ENABLE_AUTO_LOCK_BOOL,
        PREF_ENABLE_PERSISTENT_SESSION_BOOL,
        PREF_ENABLE_SCREEN_LOCK_BOOL,
        PREF_ENABLED_FEATURES_INT,
        PREF_ENCRYPTION_STR,

        PREF_FREEZE_TYPE_INT,
        PREF_FM_DISPLAY_IN_LAUNCHER_BOOL,
        PREF_FM_HOME_STR,
        PREF_FM_LAST_PATH_STR,
        PREF_FM_OPTIONS_INT,
        PREF_FM_REMEMBER_LAST_PATH_BOOL,
        PREF_FM_SORT_ORDER_INT,
        PREF_FM_SORT_REVERSE_BOOL,

        PREF_GLOBAL_BLOCKING_ENABLED_BOOL,
        PREF_DEFAULT_BLOCKING_METHOD_STR,

        PREF_INSTALLER_BLOCK_TRACKERS_BOOL,
        PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL,
        PREF_INSTALLER_DISPLAY_CHANGES_BOOL,
        PREF_INSTALLER_FORCE_DEX_OPT_BOOL,
        PREF_INSTALLER_INSTALL_LOCATION_INT,
        PREF_INSTALLER_INSTALLER_APP_STR,
        PREF_INSTALLER_SIGN_APK_BOOL,

        PREF_LAST_VERSION_CODE_LONG,
        PREF_LAYOUT_ORIENTATION_INT,

        PREF_LOG_VIEWER_BUFFER_INT,
        PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT,
        PREF_LOG_VIEWER_DISPLAY_LIMIT_INT,
        PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL,
        PREF_LOG_VIEWER_FILTER_PATTERN_STR,
        PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL,
        PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL,
        PREF_LOG_VIEWER_WRITE_PERIOD_INT,

        PREF_MAIN_WINDOW_FILTER_FLAGS_INT,
        PREF_MAIN_WINDOW_FILTER_PROFILE_STR,
        PREF_MAIN_WINDOW_SORT_ORDER_INT,
        PREF_MAIN_WINDOW_SORT_REVERSE_BOOL,

        PREF_MODE_OF_OPS_STR,
        PREF_OPEN_PGP_PACKAGE_STR,
        PREF_OPEN_PGP_USER_ID_STR,
        PREF_PERMISSIONS_SORT_ORDER_INT,

        PREF_RUNNING_APPS_FILTER_FLAGS_INT,
        PREF_RUNNING_APPS_SORT_ORDER_INT,

        PREF_SAVED_APK_FORMAT_STR,
        PREF_SELECTED_USERS_STR,
        PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL,
        PREF_SIGNATURE_SCHEMES_INT,
        PREF_SHOW_DISCLAIMER_BOOL,

        PREF_TIPS_PREFS_INT,

        PREF_VIRUS_TOTAL_API_KEY_STR,
        PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL,

        PREF_ZIP_ALIGN_BOOL,
        ;

        private static final String[] sKeys = new String[values().length];
        @Type
        private static final int[] sTypes = new int[values().length];
        private static final List<PrefKey> sPrefKeyList = Arrays.asList(values());

        static {
            String keyStr;
            int typeSeparator;
            PrefKey[] keyValues = values();
            for (int i = 0; i < keyValues.length; ++i) {
                keyStr = keyValues[i].name();
                typeSeparator = keyStr.lastIndexOf('_');
                sKeys[i] = keyStr.substring(PREF_SKIP, typeSeparator).toLowerCase(Locale.ROOT);
                sTypes[i] = inferType(keyStr.substring(typeSeparator + 1));
            }
        }

        public static int indexOf(PrefKey key) {
            return sPrefKeyList.indexOf(key);
        }

        public static int indexOf(String key) {
            return ArrayUtils.indexOf(sKeys, key);
        }

        @Type
        private static int inferType(@NonNull String typeName) {
            switch (typeName) {
                case "BOOL":
                    return TYPE_BOOLEAN;
                case "FLOAT":
                    return TYPE_FLOAT;
                case "INT":
                    return TYPE_INTEGER;
                case "LONG":
                    return TYPE_LONG;
                case "STR":
                    return TYPE_STRING;
                default:
                    throw new IllegalArgumentException("Unsupported type.");
            }
        }
    }

    @IntDef(value = {
            TYPE_BOOLEAN,
            TYPE_FLOAT,
            TYPE_INTEGER,
            TYPE_LONG,
            TYPE_STRING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    public static final int TYPE_BOOLEAN = 0;
    public static final int TYPE_FLOAT = 1;
    public static final int TYPE_INTEGER = 2;
    public static final int TYPE_LONG = 3;
    public static final int TYPE_STRING = 4;

    @SuppressLint("StaticFieldLeak")
    private static AppPref sAppPref;

    @NonNull
    public static AppPref getInstance() {
        if (sAppPref == null) {
            sAppPref = new AppPref(ContextUtils.getContext());
        }
        return sAppPref;
    }

    @NonNull
    public static AppPref getNewInstance(@NonNull Context context) {
        return new AppPref(context);
    }

    @NonNull
    public static Object get(PrefKey key) {
        int index = PrefKey.indexOf(key);
        AppPref appPref = getInstance();
        switch (PrefKey.sTypes[index]) {
            case TYPE_BOOLEAN:
                return appPref.mPreferences.getBoolean(PrefKey.sKeys[index], (boolean) appPref.getDefaultValue(key));
            case TYPE_FLOAT:
                return appPref.mPreferences.getFloat(PrefKey.sKeys[index], (float) appPref.getDefaultValue(key));
            case TYPE_INTEGER:
                return appPref.mPreferences.getInt(PrefKey.sKeys[index], (int) appPref.getDefaultValue(key));
            case TYPE_LONG:
                return appPref.mPreferences.getLong(PrefKey.sKeys[index], (long) appPref.getDefaultValue(key));
            case TYPE_STRING:
                return Objects.requireNonNull(appPref.mPreferences.getString(PrefKey.sKeys[index],
                        (String) appPref.getDefaultValue(key)));
        }
        throw new IllegalArgumentException("Unknown key or type.");
    }

    public static boolean getBoolean(PrefKey key) {
        return (boolean) get(key);
    }

    public static int getInt(PrefKey key) {
        return (int) get(key);
    }

    public static long getLong(PrefKey key) {
        return (long) get(key);
    }

    @NonNull
    public static String getString(PrefKey key) {
        return (String) get(key);
    }

    public static void set(PrefKey key, Object value) {
        getInstance().setPref(key, value);
    }

    @NonNull
    private final SharedPreferences mPreferences;
    @NonNull
    private final SharedPreferences.Editor mEditor;

    private final Context mContext;

    @SuppressLint("CommitPrefEdits")
    private AppPref(@NonNull Context context) {
        mContext = context;
        mPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mEditor = mPreferences.edit();
        init();
    }

    public void setPref(PrefKey key, Object value) {
        int index = PrefKey.indexOf(key);
        if (value instanceof Boolean) mEditor.putBoolean(PrefKey.sKeys[index], (Boolean) value);
        else if (value instanceof Float) mEditor.putFloat(PrefKey.sKeys[index], (Float) value);
        else if (value instanceof Integer) mEditor.putInt(PrefKey.sKeys[index], (Integer) value);
        else if (value instanceof Long) mEditor.putLong(PrefKey.sKeys[index], (Long) value);
        else if (value instanceof String) mEditor.putString(PrefKey.sKeys[index], (String) value);
        mEditor.apply();
        mEditor.commit();
    }

    public void setPref(String key, @Nullable Object value) {
        int index = PrefKey.indexOf(key);
        if (index == -1) throw new IllegalArgumentException("Invalid key: " + key);
        // Set default value if the requested value is null
        if (value == null) value = getDefaultValue(PrefKey.sPrefKeyList.get(index));
        if (value instanceof Boolean) mEditor.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) mEditor.putFloat(key, (Float) value);
        else if (value instanceof Integer) mEditor.putInt(key, (Integer) value);
        else if (value instanceof Long) mEditor.putLong(key, (Long) value);
        else if (value instanceof String) mEditor.putString(key, (String) value);
        mEditor.apply();
        mEditor.commit();
    }

    @NonNull
    public Object get(String key) {
        int index = PrefKey.indexOf(key);
        if (index == -1) throw new IllegalArgumentException("Invalid key: " + key);
        Object defaultValue = getDefaultValue(PrefKey.sPrefKeyList.get(index));
        switch (PrefKey.sTypes[index]) {
            case TYPE_BOOLEAN:
                return mPreferences.getBoolean(key, (boolean) defaultValue);
            case TYPE_FLOAT:
                return mPreferences.getFloat(key, (float) defaultValue);
            case TYPE_INTEGER:
                return mPreferences.getInt(key, (int) defaultValue);
            case TYPE_LONG:
                return mPreferences.getLong(key, (long) defaultValue);
            case TYPE_STRING:
                return Objects.requireNonNull(mPreferences.getString(key, (String) defaultValue));
        }
        throw new IllegalArgumentException("Unknown key or type.");
    }

    @NonNull
    public Object getValue(PrefKey key) {
        int index = PrefKey.indexOf(key);
        switch (PrefKey.sTypes[index]) {
            case TYPE_BOOLEAN:
                return mPreferences.getBoolean(PrefKey.sKeys[index], (boolean) getDefaultValue(key));
            case TYPE_FLOAT:
                return mPreferences.getFloat(PrefKey.sKeys[index], (float) getDefaultValue(key));
            case TYPE_INTEGER:
                return mPreferences.getInt(PrefKey.sKeys[index], (int) getDefaultValue(key));
            case TYPE_LONG:
                return mPreferences.getLong(PrefKey.sKeys[index], (long) getDefaultValue(key));
            case TYPE_STRING:
                return Objects.requireNonNull(mPreferences.getString(PrefKey.sKeys[index],
                        (String) getDefaultValue(key)));
        }
        throw new IllegalArgumentException("Unknown key or type.");
    }

    private void init() {
        for (int i = 0; i < PrefKey.sKeys.length; ++i) {
            if (!mPreferences.contains(PrefKey.sKeys[i])) {
                switch (PrefKey.sTypes[i]) {
                    case TYPE_BOOLEAN:
                        mEditor.putBoolean(PrefKey.sKeys[i], (boolean) getDefaultValue(PrefKey.sPrefKeyList.get(i)));
                        break;
                    case TYPE_FLOAT:
                        mEditor.putFloat(PrefKey.sKeys[i], (float) getDefaultValue(PrefKey.sPrefKeyList.get(i)));
                        break;
                    case TYPE_INTEGER:
                        mEditor.putInt(PrefKey.sKeys[i], (int) getDefaultValue(PrefKey.sPrefKeyList.get(i)));
                        break;
                    case TYPE_LONG:
                        mEditor.putLong(PrefKey.sKeys[i], (long) getDefaultValue(PrefKey.sPrefKeyList.get(i)));
                        break;
                    case TYPE_STRING:
                        mEditor.putString(PrefKey.sKeys[i], (String) getDefaultValue(PrefKey.sPrefKeyList.get(i)));
                }
            }
        }
        mEditor.apply();
    }

    @NonNull
    public Object getDefaultValue(@NonNull PrefKey key) {
        switch (key) {
            case PREF_BACKUP_FLAGS_INT:
                return BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_RULES
                        | BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_EXTRAS;
            case PREF_BACKUP_COMPRESSION_METHOD_STR:
                return TarUtils.TAR_GZIP;
            case PREF_ENABLE_KILL_FOR_SYSTEM_BOOL:
            case PREF_GLOBAL_BLOCKING_ENABLED_BOOL:
            case PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL:
            case PREF_INSTALLER_BLOCK_TRACKERS_BOOL:
            case PREF_INSTALLER_FORCE_DEX_OPT_BOOL:
            case PREF_INSTALLER_SIGN_APK_BOOL:
            case PREF_BACKUP_ANDROID_KEYSTORE_BOOL:
            case PREF_ENABLE_SCREEN_LOCK_BOOL:
            case PREF_MAIN_WINDOW_SORT_REVERSE_BOOL:
            case PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL:
            case PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL:
            case PREF_APP_THEME_PURE_BLACK_BOOL:
            case PREF_DISPLAY_CHANGELOG_BOOL:
            case PREF_FM_DISPLAY_IN_LAUNCHER_BOOL:
            case PREF_FM_REMEMBER_LAST_PATH_BOOL:
            case PREF_FM_SORT_REVERSE_BOOL:
            case PREF_ENABLE_PERSISTENT_SESSION_BOOL:
                return false;
            case PREF_APP_OP_SHOW_DEFAULT_BOOL:
            case PREF_SHOW_DISCLAIMER_BOOL:
            case PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL:
            case PREF_INSTALLER_DISPLAY_CHANGES_BOOL:
            case PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL:
            case PREF_ZIP_ALIGN_BOOL:
            case PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL:
            case PREF_ENABLE_AUTO_LOCK_BOOL:
                return true;
            case PREF_CONCURRENCY_THREAD_COUNT_INT:
            case PREF_APP_THEME_CUSTOM_INT:
            case PREF_TIPS_PREFS_INT:
                return 0;
            case PREF_LAST_VERSION_CODE_LONG:
            case PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG:
                return 0L;
            case PREF_ENABLED_FEATURES_INT:
                return 0xffff_ffff;  /* All features enabled */
            case PREF_APP_THEME_INT:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            case PREF_MAIN_WINDOW_FILTER_FLAGS_INT:
                return MainListOptions.FILTER_NO_FILTER;
            case PREF_MAIN_WINDOW_SORT_ORDER_INT:
                return MainListOptions.SORT_BY_APP_LABEL;
            case PREF_CUSTOM_LOCALE_STR:
                return LangUtils.LANG_AUTO;
            case PREF_APP_OP_SORT_ORDER_INT:
            case PREF_COMPONENTS_SORT_ORDER_INT:
            case PREF_PERMISSIONS_SORT_ORDER_INT:
                return AppDetailsFragment.SORT_BY_NAME;
            case PREF_RUNNING_APPS_SORT_ORDER_INT:
                return RunningAppsActivity.SORT_BY_PID;
            case PREF_RUNNING_APPS_FILTER_FLAGS_INT:
                return RunningAppsActivity.FILTER_NONE;
            case PREF_ENCRYPTION_STR:
                return CryptoUtils.MODE_NO_ENCRYPTION;
            case PREF_OPEN_PGP_PACKAGE_STR:
            case PREF_OPEN_PGP_USER_ID_STR:
            case PREF_MAIN_WINDOW_FILTER_PROFILE_STR:
            case PREF_SELECTED_USERS_STR:
            case PREF_VIRUS_TOTAL_API_KEY_STR:
                return "";
            case PREF_MODE_OF_OPS_STR:
                return Ops.MODE_AUTO;
            case PREF_INSTALLER_INSTALL_LOCATION_INT:
                return PackageInfo.INSTALL_LOCATION_AUTO;
            case PREF_INSTALLER_INSTALLER_APP_STR:
                return BuildConfig.APPLICATION_ID;
            case PREF_SIGNATURE_SCHEMES_INT:
                return SigSchemes.DEFAULT_SCHEMES;
            case PREF_BACKUP_VOLUME_STR:
            case PREF_FM_HOME_STR:
                return Uri.fromFile(Environment.getExternalStorageDirectory()).toString();
            case PREF_LOG_VIEWER_FILTER_PATTERN_STR:
                return mContext.getString(R.string.pref_filter_pattern_default);
            case PREF_LOG_VIEWER_DISPLAY_LIMIT_INT:
                return LogcatHelper.DEFAULT_DISPLAY_LIMIT;
            case PREF_LOG_VIEWER_WRITE_PERIOD_INT:
                return LogcatHelper.DEFAULT_LOG_WRITE_INTERVAL;
            case PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT:
                return Log.VERBOSE;
            case PREF_LOG_VIEWER_BUFFER_INT:
                return LogcatHelper.LOG_ID_DEFAULT;
            case PREF_LAYOUT_ORIENTATION_INT:
                return View.LAYOUT_DIRECTION_LTR;
            case PREF_DEFAULT_BLOCKING_METHOD_STR:
                // This is default for root
                return ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE;
            case PREF_SAVED_APK_FORMAT_STR:
                return "%label%_%version%";
            case PREF_AUTHORIZATION_KEY_STR:
                return AuthManager.generateKey();
            case PREF_FREEZE_TYPE_INT:
                return FreezeUtils.FREEZE_DISABLE;
            case PREF_FM_OPTIONS_INT:
                return FmListOptions.OPTIONS_DISPLAY_DOT_FILES | FmListOptions.OPTIONS_FOLDERS_FIRST;
            case PREF_FM_SORT_ORDER_INT:
                return FmListOptions.SORT_BY_NAME;
            case PREF_DEBLOATER_FILTER_FLAGS_INT:
                return DebloaterListOptions.getDefaultFilterFlags();
            case PREF_FM_LAST_PATH_STR:
                return "{}";
        }
        throw new IllegalArgumentException("Pref key not found.");
    }
}
