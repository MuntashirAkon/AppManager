// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class InstallerOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put("all", TYPE_NONE);
        put("installer", TYPE_STR_SINGLE);
        put("installers", TYPE_STR_MULTIPLE);
        put("regex", TYPE_REGEX);
    }};

    public InstallerOption() {
        super("installer");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        InstallSourceInfoCompat installSourceInfo = info.getInstallerInfo();
        if (installSourceInfo == null) {
            return result.setMatched(key.equals("all"));
        }
        // There's at least one installer at this point
        Set<String> installers = getInstallers(installSourceInfo);
        switch (key) {
            default:
                return result.setMatched(false);
            case "installer":
                return result.setMatched(installers.contains(value));
            case "installers":
                for (String installer: stringValues) {
                    if (installers.contains(installer)) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
            case "regex":
                for (String installer : installers) {
                    if (regexValue.matcher(installer).matches()) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
        }
    }

    @NonNull
    private static Set<String> getInstallers(InstallSourceInfoCompat installSourceInfo) {
        Set<String> installers = new LinkedHashSet<>();
        if (installSourceInfo.getInstallingPackageName() != null) {
            installers.add(installSourceInfo.getInstallingPackageName());
        }
        if (installSourceInfo.getInitiatingPackageName() != null) {
            installers.add(installSourceInfo.getInitiatingPackageName());
        }
        if (installSourceInfo.getOriginatingPackageName() != null) {
            installers.add(installSourceInfo.getOriginatingPackageName());
        }
        return installers;
    }
}
