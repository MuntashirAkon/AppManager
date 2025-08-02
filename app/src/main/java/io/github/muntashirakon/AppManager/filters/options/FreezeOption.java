// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static io.github.muntashirakon.AppManager.utils.LangUtils.getSeparatorString;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;

public class FreezeOption extends FilterOption {
    public static final int FREEZE_TYPE_DISABLED = 1 << 0;
    public static final int FREEZE_TYPE_HIDDEN = 1 << 1;
    public static final int FREEZE_TYPE_SUSPENDED = 1 << 2;

    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("frozen", TYPE_NONE);
        put("unfrozen", TYPE_NONE);
        put("with_flags", TYPE_INT_FLAGS);
        put("without_flags", TYPE_INT_FLAGS);
    }};

    private final Map<Integer, CharSequence> mFrozenFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(FREEZE_TYPE_DISABLED, "Disabled");
        put(FREEZE_TYPE_HIDDEN, "Hidden");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put(FREEZE_TYPE_SUSPENDED, "Suspended");
        }
    }};

    public FreezeOption() {
        super("freeze_unfreeze");
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
        int freezeFlags = info.getFreezeFlags();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "frozen":
                return result.setMatched(freezeFlags != 0);
            case "unfrozen":
                return result.setMatched(freezeFlags == 0);
            case "with_flags":
                return result.setMatched((freezeFlags & intValue) == intValue);
            case "without_flags":
                return result.setMatched((freezeFlags & intValue) != intValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        switch (key) {
            case KEY_ALL:
                return "Frozen" + getSeparatorString() + " any";
            case "frozen":
                return "Frozen apps only";
            case "unfrozen":
                return "Unfrozen apps only";
            case "with_flags":
                return "Frozen apps with types " + flagsToString("with_flags", intValue);
            case "without_flags":
                return "Frozen apps without types " + flagsToString("without_flags", intValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
