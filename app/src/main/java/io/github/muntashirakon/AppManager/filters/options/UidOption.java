// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class UidOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("uid_eq", TYPE_INT);
        put("uid_le", TYPE_INT);
        put("uid_ge", TYPE_INT);
        put("with_shared", TYPE_NONE);
        put("without_shared", TYPE_NONE);
        put("shared_uid_name", TYPE_STR_SINGLE);
        put("shared_uid_names", TYPE_STR_MULTIPLE);
        put("shared_uid_name_regex", TYPE_REGEX);
    }};

    public UidOption() {
        super("uid");
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
                return result.setMatched(false);
            case "uid_eq":
                return result.setMatched(info.getUid() == intValue);
            case "uid_le":
                return result.setMatched(info.getUid() <= intValue);
            case "uid_ge":
                return result.setMatched(info.getUid() >= intValue);
            case "with_shared":
                return result.setMatched(info.getSharedUserId() != null);
            case "without_shared":
                return result.setMatched(info.getSharedUserId() == null);
            case "shared_uid_name":
                return result.setMatched(Objects.equals(info.getSharedUserId(), value));
            case "shared_uid_names":
                return result.setMatched(ArrayUtils.contains(stringValues, info.getSharedUserId()));
            case "shared_uid_name_regex":
                return result.setMatched(regexValue.matcher(info.getSharedUserId()).matches());
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("UID");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "uid_eq":
                return sb.append(" = ").append(Integer.toString(intValue));
            case "uid_le":
                return sb.append(" ≤ ").append(Integer.toString(intValue));
            case "uid_ge":
                return sb.append(" ≥ ").append(Integer.toString(intValue));
            case "with_shared":
                return "Only the apps with a shared UID";
            case "without_shared":
                return "Only the apps without a shared UID";
            case "shared_uid_name":
                return "Only the apps with the shared UID " + value;
            case "shared_uid_names":
                return "Only the apps with the shared UID (exclusive) " + String.join(", ", stringValues);
            case "shared_uid_name_regex":
                return "Only the apps with the shared UID that matches '" + value + "'";
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
