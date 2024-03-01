// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class ScreenTimeOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("eq", TYPE_DURATION_MILLIS);
        put("le", TYPE_DURATION_MILLIS);
        put("ge", TYPE_DURATION_MILLIS);
    }};

    public ScreenTimeOption() {
        super("screen_time");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        switch (key) {
            default:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getTotalScreenTime() == longValue);
            case "le":
                return result.setMatched(info.getTotalScreenTime() <= longValue);
            case "ge":
                return result.setMatched(info.getTotalScreenTime() >= longValue);
        }
    }
}
