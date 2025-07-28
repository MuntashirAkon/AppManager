// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class TargetSdkOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
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
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getTargetSdk() == intValue);
            case "le":
                return result.setMatched(info.getTargetSdk() <= intValue);
            case "ge":
                return result.setMatched(info.getTargetSdk() >= intValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Target SDK");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = ").append(Integer.toString(intValue));
            case "le":
                return sb.append(" ≤ ").append(Integer.toString(intValue));
            case "ge":
                return sb.append(" ≥ ").append(Integer.toString(intValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
