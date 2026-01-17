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

public class ScreenTimeOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
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
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getTotalScreenTime() == longValue);
            case "le":
                return result.setMatched(info.getTotalScreenTime() <= longValue);
            case "ge":
                return result.setMatched(info.getTotalScreenTime() >= longValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Screentime");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = ").append(DateUtils.getFormattedDuration(context, longValue, false, true));
            case "le":
                return sb.append(" ≤ ").append(DateUtils.getFormattedDuration(context, longValue, false, true));
            case "ge":
                return sb.append(" ≥ ").append(DateUtils.getFormattedDuration(context, longValue, false, true));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
