// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class PackageNameOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
    }};

    public PackageNameOption() {
        super("pkg_name");
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
                return result.setMatched(info.getPackageName().equals(Objects.requireNonNull(value)));
            case "contains":
                return result.setMatched(info.getPackageName().contains(Objects.requireNonNull(value)));
            case "starts_with":
                return result.setMatched(info.getPackageName().startsWith(Objects.requireNonNull(value)));
            case "ends_with":
                return result.setMatched(info.getPackageName().endsWith(Objects.requireNonNull(value)));
            case "regex":
                return result.setMatched(regexValue.matcher(info.getPackageName()).matches());
        }
    }
}
