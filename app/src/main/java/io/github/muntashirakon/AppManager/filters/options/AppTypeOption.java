// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.os.Build;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class AppTypeOption extends FilterOption {
    public static final int APP_TYPE_USER = 1 << 0;
    public static final int APP_TYPE_SYSTEM = 1 << 1;
    public static final int APP_TYPE_UPDATED_SYSTEM = 1 << 2;
    public static final int APP_TYPE_PRIVILEGED = 1 << 3;
    public static final int APP_TYPE_DATA_ONLY = 1 << 4;
    public static final int APP_TYPE_STOPPED = 1 << 5;
    public static final int APP_TYPE_SENSORS = 1 << 6;
    public static final int APP_TYPE_LARGE_HEAP = 1 << 7;
    public static final int APP_TYPE_DEBUGGABLE = 1 << 8;
    public static final int APP_TYPE_TEST_ONLY = 1 << 9;
    public static final int APP_TYPE_HAS_CODE = 1 << 10;
    public static final int APP_TYPE_PERSISTENT = 1 << 11;
    public static final int APP_TYPE_ALLOW_BACKUP = 1 << 12;
    public static final int APP_TYPE_INSTALLED_IN_EXTERNAL = 1 << 13;
    public static final int APP_TYPE_HTTP_ONLY = 1 << 14;
    public static final int APP_TYPE_BATTERY_OPT_ENABLED = 1 << 15;
    public static final int APP_TYPE_PLAY_APP_SIGNING = 1 << 16;
    public static final int APP_TYPE_SSAID = 1 << 17;
    public static final int APP_TYPE_KEYSTORE = 1 << 18;
    public static final int APP_TYPE_WITH_RULES = 1 << 19;
    public static final int APP_TYPE_PWA = 1 << 20;
    public static final int APP_TYPE_SHORT_CODE = 1 << 21;
    public static final int APP_TYPE_OVERLAY = 1 << 22;

    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("with_flags", TYPE_INT_FLAGS);
        put("without_flags", TYPE_INT_FLAGS);
    }};

    private final Map<Integer, CharSequence> mFrozenFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(APP_TYPE_USER, "User app");
        put(APP_TYPE_SYSTEM, "System app");
        put(APP_TYPE_UPDATED_SYSTEM, "Updated system app");
        put(APP_TYPE_PRIVILEGED, "Privileged app");
        put(APP_TYPE_DATA_ONLY, "Data-only app");
        put(APP_TYPE_STOPPED, "Force-stopped app");
//        put(APP_TYPE_SENSORS, "Uses sensors"); // TODO: 11/21/24 (dynamic)
        put(APP_TYPE_LARGE_HEAP, "Requests large heap");
        put(APP_TYPE_DEBUGGABLE, "Debuggable app");
        put(APP_TYPE_TEST_ONLY, "Test-only app");
        put(APP_TYPE_HAS_CODE, "Has code");
        put(APP_TYPE_PERSISTENT, "Persistent app");
        put(APP_TYPE_ALLOW_BACKUP, "Backup allowed");
        put(APP_TYPE_INSTALLED_IN_EXTERNAL, "Installed in external storage");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            put(APP_TYPE_HTTP_ONLY, "Uses cleartext (HTTP) traffic");
        }
//        put(APP_TYPE_BATTERY_OPT_ENABLED, "Battery optimized"); // TODO: 11/21/24 (dynamic)
//        put(APP_TYPE_PLAY_APP_SIGNING, "Uses Play App Signing"); // TODO: 11/21/24
        put(APP_TYPE_SSAID, "Has SSAID");
//        put(APP_TYPE_KEYSTORE, "Uses Android KeyStore"); // TODO: 11/21/24
//        put(APP_TYPE_WITH_RULES, "Has rules"); // TODO: 11/21/24
//        put(APP_TYPE_PWA, "Progressive web app (PWA)"); // TODO: 11/21/24
//        put(APP_TYPE_SHORT_CODE, "Uses short code"); // TODO: 11/21/24
//        put(APP_TYPE_OVERLAY, "Overlay app"); // TODO: 11/21/24
    }};

    public AppTypeOption() {
        super("app_type");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("with_flags") || key.equals("without_flags")) {
            return mFrozenFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        // TODO: 7/28/25 Make it more efficient by doing this on demand
        int appTypeFlags = info.getAppTypeFlags();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "with_flags":
                return result.setMatched((appTypeFlags & intValue) == intValue);
            case "without_flags":
                return result.setMatched((appTypeFlags & intValue) != intValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Apps");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "with_flags":
                return sb.append(" with flags: ").append(flagsToString("with_flags", intValue));
            case "without_flags":
                return sb.append(" without flags: ").append(flagsToString("without_flags", intValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
