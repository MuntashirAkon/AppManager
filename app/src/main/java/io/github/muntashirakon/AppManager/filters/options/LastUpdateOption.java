// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class LastUpdateOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
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
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        boolean installed = info.isInstalled();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "before":
                return result.setMatched(installed && info.getLastUpdateTime() <= longValue);
            case "after":
                return result.setMatched(installed && info.getLastUpdateTime() >= longValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Last update");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "before":
                return sb.append(" before ").append(DateUtils.formatDateTime(context, longValue));
            case "after":
                return sb.append(" after ").append(DateUtils.formatDateTime(context, longValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
