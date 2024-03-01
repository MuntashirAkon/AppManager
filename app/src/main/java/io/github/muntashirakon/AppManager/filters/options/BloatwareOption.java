// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_CAUTION;
import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_REPLACE;
import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_SAFE;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_AOSP;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_CARRIER;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_GOOGLE;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_MISC;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_OEM;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_PENDING;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class BloatwareOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("type", TYPE_INT_FLAGS);
        put("removal", TYPE_INT_FLAGS);
    }};

    private final Map<Integer, CharSequence> mBloatwareTypeFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(FILTER_LIST_AOSP, "AOSP");
        put(FILTER_LIST_CARRIER, "Carrier");
        put(FILTER_LIST_GOOGLE, "Google");
        put(FILTER_LIST_MISC, "Misc");
        put(FILTER_LIST_OEM, "OEM");
        put(FILTER_LIST_PENDING, "Pending");
    }};

    private final Map<Integer, CharSequence> mRemovalFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(REMOVAL_SAFE, "Safe");
        put(REMOVAL_REPLACE, "Replace");
        put(REMOVAL_CAUTION, "Caution");
    }};

    public BloatwareOption() {
        super("bloatware");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("type")) {
            return mBloatwareTypeFlags;
        } else if (key.equals("removal")) {
            return mRemovalFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        DebloatObject object = info.getBloatwareInfo();
        if (object == null) {
            return result.setMatched(key.equals("all"));
        }
        switch (key) {
            default:
                return result.setMatched(false);
            case "type":
                return result.setMatched((typeToFlag(object.type) & intValue) != 0);
            case "removal":
                return result.setMatched((object.getRemoval() & intValue) != 0);
        }
    }

    public int typeToFlag(@NonNull String type) {
        switch (type) {
            case "aosp":
                return FILTER_LIST_AOSP;
            case "carrier":
                return FILTER_LIST_CARRIER;
            case "google":
                return FILTER_LIST_GOOGLE;
            case "misc":
                return FILTER_LIST_MISC;
            case "oem":
                return FILTER_LIST_OEM;
            case "pending":
                return FILTER_LIST_PENDING;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
