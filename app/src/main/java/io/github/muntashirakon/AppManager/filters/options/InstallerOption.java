// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class InstallerOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("installer", TYPE_STR_SINGLE);
        put("installer_any", TYPE_STR_MULTIPLE);
        put("installer_none", TYPE_STR_MULTIPLE);
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
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        InstallSourceInfoCompat installSourceInfo = info.getInstallerInfo();
        if (installSourceInfo == null) {
            return result.setMatched(key.equals(KEY_ALL));
        }
        // There's at least one installer at this point
        Set<String> installers = getInstallers(installSourceInfo);
        switch (key) {
            case KEY_ALL:
                return result.setMatched(false);
            case "installer":
                return result.setMatched(installers.contains(value));
            case "installer_any":
                for (String installer: stringValues) {
                    if (installers.contains(installer)) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
            case "installer_none":
                for (String installer: stringValues) {
                    if (installers.contains(installer)) {
                        return result.setMatched(false);
                    }
                }
                return result.setMatched(true);
            case "regex":
                for (String installer : installers) {
                    if (regexValue.matcher(installer).matches()) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    private static Set<String> getInstallers(@NonNull InstallSourceInfoCompat installSourceInfo) {
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

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Only the apps with installer");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "installer":
                return sb.append(" ").append(value);
            case "installer_any":
                return sb.append(" matching any of ").append(String.join(", ", stringValues));
            case "installer_none":
                return sb.append(" matching none of ").append(String.join(", ", stringValues));
            case "regex":
                return sb.append(" that matches '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
