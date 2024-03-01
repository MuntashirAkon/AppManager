// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class RunningAppsOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("running", TYPE_NONE);
        put("not_running", TYPE_NONE);
    }};

    public RunningAppsOption() {
        super("running_apps");
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
            case "running":
                return result.setMatched(info.isRunning());
            case "not_running":
                return result.setMatched(!info.isRunning());
        }
    }
}
