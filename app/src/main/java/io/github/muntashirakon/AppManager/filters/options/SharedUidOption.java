// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class SharedUidOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("with_shared", TYPE_NONE);
        put("without_shared", TYPE_NONE);
        put("uid_name", TYPE_STR_SINGLE);
        put("uid_names", TYPE_STR_MULTIPLE);
        put("uid_name_regex", TYPE_REGEX);
    }};

    public SharedUidOption() {
        super("shared_uid");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        String shared_uid = info.getSharedUserId();
        switch (key) {
            default:
                return result.setMatched(false);
            case "with_shared":
                return result.setMatched(shared_uid != null);
            case "without_shared":
                return result.setMatched(shared_uid == null);
            case "uid_name":
                return result.setMatched(shared_uid.equals(value));
            case "installers":
                return result.setMatched(ArrayUtils.contains(stringValues, shared_uid));
            case "regex":
                return result.setMatched(regexValue.matcher(shared_uid).matches());
        }
    }
}
