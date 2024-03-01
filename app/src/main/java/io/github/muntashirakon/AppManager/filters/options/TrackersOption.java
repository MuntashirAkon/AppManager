// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class TrackersOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("eq", TYPE_INT);
        put("le", TYPE_INT);
        put("ge", TYPE_INT);
        // TODO: 7/2/24 Enhance this to include more curated options such as regex and find by tracker name
    }};

    public TrackersOption() {
        super("trackers");
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
                return result.setMatched(info.getTrackerComponents().size() == intValue);
            case "le":
                return result.setMatched(info.getTrackerComponents().size() <= intValue);
            case "ge":
                return result.setMatched(info.getTrackerComponents().size() >= intValue);
        }
    }
}
