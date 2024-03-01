// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class LastUpdateOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("before", TYPE_TIME_MILLIS);
        put("after", TYPE_TIME_MILLIS);
    }};

    public LastUpdateOption() {
        super("last_update");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        boolean installed = info.isInstalled();
        switch (key) {
            default:
                return result.setMatched(true);
            case "before":
                return result.setMatched(installed && info.getLastUpdateTime() <= longValue);
            case "after":
                return result.setMatched(installed && info.getLastUpdateTime() >= longValue);
        }
    }
}
