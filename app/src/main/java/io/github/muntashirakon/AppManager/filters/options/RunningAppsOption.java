// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;

public class RunningAppsOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
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
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "running":
                return result.setMatched(info.isRunning());
            case "not_running":
                return result.setMatched(!info.isRunning());
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("App label");
        switch (key) {
            case KEY_ALL:
                return "Both running and not running apps";
            case "running":
                return "Only the running apps";
            case "not_running":
                return "Only the not running apps";
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
