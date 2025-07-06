// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class SharedUidOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("with_shared", TYPE_NONE);
        put("without_shared", TYPE_NONE);
        put("uid_name", TYPE_STR_SINGLE);
        put("uid_names", TYPE_STR_MULTIPLE);
        put("uid_name_regex", TYPE_REGEX);
    }};

    public SharedUidOption() {
        super("shared_uid");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        String shared_uid = info.getSharedUserId();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(false);
            case "with_shared":
                return result.setMatched(shared_uid != null);
            case "without_shared":
                return result.setMatched(shared_uid == null);
            case "uid_name":
                return result.setMatched(shared_uid.equals(value));
            case "uid_names":
                return result.setMatched(ArrayUtils.contains(stringValues, shared_uid));
            case "uid_name_regex":
                return result.setMatched(regexValue.matcher(shared_uid).matches());
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Only the apps with Shared UID");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("ignore");
            case "with_shared":
                return "Only the apps with a shared UID";
            case "without_shared":
                return "Only the apps without a shared UID";
            case "uid_name":
                return sb.append(" ").append(value);
            case "uid_names":
                return sb.append(" (exclusive) ").append(String.join(", ", stringValues));
            case "uid_name_regex":
                return sb.append(" that matches '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
