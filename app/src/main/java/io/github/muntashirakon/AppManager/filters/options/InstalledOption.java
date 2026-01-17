// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class InstalledOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("installed", TYPE_NONE);
        put("uninstalled", TYPE_NONE);
        put("installed_before", TYPE_TIME_MILLIS);
        put("installed_after", TYPE_TIME_MILLIS);
    }};

    public InstalledOption() {
        super("installed");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        boolean installed = info.isInstalled();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "installed":
                return result.setMatched(installed);
            case "uninstalled":
                return result.setMatched(!installed);
            case "installed_before":
                return result.setMatched(installed && info.getFirstInstallTime() <= longValue);
            case "installed_after":
                return result.setMatched(installed && info.getFirstInstallTime() >= longValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Installed");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "installed":
                return "Installed apps";
            case "uninstalled":
                return "Uninstalled apps";
            case "installed_before":
                return sb.append(" before ").append(DateUtils.formatDateTime(context, longValue));
            case "installed_after":
                return sb.append(" after ").append(DateUtils.formatDateTime(context, longValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
