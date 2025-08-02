// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.os.Build;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class CompileSdkOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("eq", TYPE_INT);
            put("le", TYPE_INT);
            put("ge", TYPE_INT);
        }
    }};

    public CompileSdkOption() {
        super("compile_sdk");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return result.setMatched(true);
        }
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getCompileSdk() == intValue);
            case "le":
                return result.setMatched(info.getCompileSdk() <= intValue);
            case "ge":
                return result.setMatched(info.getCompileSdk() >= intValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Compile SDK");
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
