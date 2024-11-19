// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

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
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        List<String> permissions = result.getMatchedPermissions() != null
                ? result.getMatchedPermissions()
                : info.getAllPermissions();
        switch (key) {
            default:
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
        }
    }
}
