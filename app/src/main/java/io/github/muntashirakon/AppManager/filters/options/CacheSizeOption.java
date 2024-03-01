// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class CacheSizeOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("eq", TYPE_SIZE_BYTES);
        put("le", TYPE_SIZE_BYTES);
        put("ge", TYPE_SIZE_BYTES);
    }};

    public CacheSizeOption() {
        super("cache_size");
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
                return result.setMatched(info.getCacheSize() == longValue);
            case "le":
                return result.setMatched(info.getCacheSize() <= longValue);
            case "ge":
                return result.setMatched(info.getCacheSize() >= longValue);
        }
    }
}
