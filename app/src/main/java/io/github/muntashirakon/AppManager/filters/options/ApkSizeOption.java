// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class ApkSizeOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_SIZE_BYTES);
        put("le", TYPE_SIZE_BYTES);
        put("ge", TYPE_SIZE_BYTES);
    }};

    public ApkSizeOption() {
        super("apk_size");
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
                return result.setMatched(info.getApkSize() == longValue);
            case "le":
                return result.setMatched(info.getApkSize() <= longValue);
            case "ge":
                return result.setMatched(info.getApkSize() >= longValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("APK size");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = ").append(Formatter.formatFileSize(context, longValue));
            case "le":
                return sb.append(" ≤ ").append(Formatter.formatFileSize(context, longValue));
            case "ge":
                return sb.append(" ≥ ").append(Formatter.formatFileSize(context, longValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
