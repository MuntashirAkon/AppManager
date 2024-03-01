// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class InstalledOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("installed", TYPE_NONE);
        put("installed_before", TYPE_TIME_MILLIS);
        put("installed_after", TYPE_TIME_MILLIS);
    }};

    public InstalledOption() {
        super("installed");
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
            case "installed":
                return result.setMatched(installed);
            case "uninstalled":
                return result.setMatched(!installed);
            case "installed_before":
                return result.setMatched(installed && info.getFirstInstallTime() <= longValue);
            case "installed_after":
                return result.setMatched(installed && info.getFirstInstallTime() >= longValue);
        }
    }
}
