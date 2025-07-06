// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class PermissionsOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
        // TODO: 11/19/24 Add more curated options such as permission flags, private flags, grant
    }};

    public PermissionsOption() {
        super("permissions");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        List<String> permissions = result.getMatchedPermissions() != null
                ? result.getMatchedPermissions()
                : info.getAllPermissions();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true).setMatchedPermissions(permissions);
            case "eq": {
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.equals(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "contains": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.contains(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "starts_with": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.startsWith(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "ends_with": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.endsWith(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "regex": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (regexValue.matcher(permission).matches()) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Permissions");
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
