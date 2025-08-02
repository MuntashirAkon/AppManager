// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class VersionNameOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
    }};

    public VersionNameOption() {
        super("version_name");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        if (info.getVersionName() == null) {
            return result.setMatched(key.equals(KEY_ALL));
        }
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getVersionName().equals(Objects.requireNonNull(value)));
            case "contains":
                return result.setMatched(info.getVersionName().contains(Objects.requireNonNull(value)));
            case "starts_with":
                return result.setMatched(info.getVersionName().startsWith(Objects.requireNonNull(value)));
            case "ends_with":
                return result.setMatched(info.getVersionName().endsWith(Objects.requireNonNull(value)));
            case "regex":
                return result.setMatched(regexValue.matcher(info.getVersionName()).matches());
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Version name");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = '").append(value).append("'");
            case "contains":
                return sb.append(" contains '").append(value).append("'");
            case "starts_with":
                return sb.append(" starts with '").append(value).append("'");
            case "ends_with":
                return sb.append(" ends with '").append(value).append("'");
            case "regex":
                return sb.append(" matches '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
