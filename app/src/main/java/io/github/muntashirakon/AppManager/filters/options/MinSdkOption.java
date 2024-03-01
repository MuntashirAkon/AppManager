// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class MinSdkOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put("eq", TYPE_INT);
            put("le", TYPE_INT);
            put("ge", TYPE_INT);
        }
    }};

    public MinSdkOption() {
        super("min_sdk");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return result.setMatched(true);
        }
        switch (key) {
            default:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getMinSdk() == intValue);
            case "le":
                return result.setMatched(info.getMinSdk() <= intValue);
            case "ge":
                return result.setMatched(info.getMinSdk() >= intValue);
        }
    }
}
