// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class TargetSdkOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("eq", TYPE_INT);
        put("le", TYPE_INT);
        put("ge", TYPE_INT);
    }};

    public TargetSdkOption() {
        super("target_sdk");
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
                return result.setMatched(info.getTargetSdk() == intValue);
            case "le":
                return result.setMatched(info.getTargetSdk() <= intValue);
            case "ge":
                return result.setMatched(info.getTargetSdk() >= intValue);
        }
    }
}
