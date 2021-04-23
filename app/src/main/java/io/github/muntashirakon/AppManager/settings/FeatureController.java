/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.SparseArrayCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.details.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.utils.AppPref;

import java.util.*;

public class FeatureController {
    @IntDef(flag = true, value = {
            FEAT_INTERCEPTOR,
            FEAT_MANIFEST,
            FEAT_SCANNER,
            FEAT_INSTALLER,
            FEAT_USAGE_ACCESS,
            FEAT_LOG_VIEWER,
    })
    public @interface FeatureFlags {
    }

    public static final int FEAT_INTERCEPTOR = 1;
    public static final int FEAT_MANIFEST = 1 << 1;
    public static final int FEAT_SCANNER = 1 << 2;
    public static final int FEAT_INSTALLER = 1 << 3;
    public static final int FEAT_USAGE_ACCESS = 1 << 4;
    public static final int FEAT_LOG_VIEWER = 1 << 5;

    @NonNull
    public static FeatureController getInstance() {
        return new FeatureController();
    }

    @FeatureFlags
    public static final List<Integer> featureFlags = new ArrayList<>();

    private static final LinkedHashMap<Integer, Integer> featureFlagsMap = new LinkedHashMap<Integer, Integer>() {
        {
            featureFlags.add(FEAT_INTERCEPTOR);
            put(FEAT_INTERCEPTOR, R.string.interceptor);
            featureFlags.add(FEAT_MANIFEST);
            put(FEAT_MANIFEST, R.string.manifest_viewer);
            featureFlags.add(FEAT_SCANNER);
            put(FEAT_SCANNER, R.string.scanner);
            featureFlags.add(FEAT_INSTALLER);
            put(FEAT_INSTALLER, R.string.package_installer);
            featureFlags.add(FEAT_USAGE_ACCESS);
            put(FEAT_USAGE_ACCESS, R.string.usage_access);
            featureFlags.add(FEAT_LOG_VIEWER);
            put(FEAT_LOG_VIEWER, R.string.log_viewer);
        }
    };

    @NonNull
    public static CharSequence[] getFormattedFlagNames(@NonNull Context context) {
        CharSequence[] flagNames = new CharSequence[featureFlags.size()];
        for (int i = 0; i < flagNames.length; ++i) {
            flagNames[i] = context.getText(Objects.requireNonNull(featureFlagsMap.get(featureFlags.get(i))));
        }
        return flagNames;
    }

    private static final String packageName = AppManager.getContext().getPackageName();
    private static final SparseArrayCompat<ComponentName> componentCache = new SparseArrayCompat<>(4);

    private final PackageManager pm;
    private int flags;

    private FeatureController() {
        pm = AppManager.getContext().getPackageManager();
        flags = (int) AppPref.get(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT);
    }

    public static boolean isInterceptorEnabled() {
        return getInstance().isEnabled(FEAT_INTERCEPTOR);
    }

    public static boolean isManifestEnabled() {
        return getInstance().isEnabled(FEAT_MANIFEST);
    }

    public static boolean isScannerEnabled() {
        return getInstance().isEnabled(FEAT_SCANNER);
    }

    public static boolean isInstallerEnabled() {
        return getInstance().isEnabled(FEAT_INSTALLER);
    }

    public static boolean isUsageAccessEnabled() {
        return getInstance().isEnabled(FEAT_USAGE_ACCESS);
    }

    public static boolean isLogViewerEnabled() {
        return getInstance().isEnabled(FEAT_LOG_VIEWER);
    }

    public boolean isEnabled(@FeatureFlags int key) {
        ComponentName cn;
        switch (key) {
            case FEAT_INSTALLER:
                cn = getComponentName(key, PackageInstallerActivity.class);
                break;
            case FEAT_INTERCEPTOR:
                cn = getComponentName(key, ActivityInterceptor.class);
                break;
            case FEAT_MANIFEST:
                cn = getComponentName(key, ManifestViewerActivity.class);
                break;
            case FEAT_SCANNER:
                cn = getComponentName(key, ScannerActivity.class);
                break;
            case FEAT_USAGE_ACCESS:
                // Only depends on flag
                return (flags & key) != 0;
            case FEAT_LOG_VIEWER:
                cn = getComponentName(key, LogViewerActivity.class);
                break;
            default:
                throw new IllegalArgumentException();
        }
        return isComponentEnabled(cn) && (flags & key) != 0;
    }

    @NonNull
    public boolean[] flagsToCheckedItems() {
        boolean[] checkedItems = new boolean[featureFlags.size()];
        Arrays.fill(checkedItems, false);
        for (int i = 0; i < checkedItems.length; ++i) {
            if ((flags & featureFlags.get(i)) != 0) checkedItems[i] = true;
        }
        return checkedItems;
    }

    public void modifyState(@FeatureFlags int key, boolean enabled) {
        switch (key) {
            case FEAT_INSTALLER:
                modifyState(key, PackageInstallerActivity.class, enabled);
                break;
            case FEAT_INTERCEPTOR:
                modifyState(key, ActivityInterceptor.class, enabled);
                break;
            case FEAT_MANIFEST:
                modifyState(key, ManifestViewerActivity.class, enabled);
                break;
            case FEAT_SCANNER:
                modifyState(key, ScannerActivity.class, enabled);
                break;
            case FEAT_USAGE_ACCESS:
                // Only depends on flag
                break;
            case FEAT_LOG_VIEWER:
                modifyState(key, LogViewerActivity.class, enabled);
                break;
        }
        // Modify flags
        flags = enabled ? (flags | key) : (flags & ~key);
        // Save to pref
        AppPref.set(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT, flags);
    }

    private void modifyState(@FeatureFlags int key, @Nullable Class<? extends AppCompatActivity> clazz, boolean enabled) {
        ComponentName cn = getComponentName(key, clazz);
        if (cn == null) return;
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
    }

    @Nullable
    private ComponentName getComponentName(@FeatureFlags int key, @Nullable Class<? extends AppCompatActivity> clazz) {
        if (clazz == null) return null;
        ComponentName cn = componentCache.get(key);
        if (cn == null) {
            cn = new ComponentName(packageName, clazz.getName());
            componentCache.put(key, cn);
        }
        return cn;
    }

    private boolean isComponentEnabled(@Nullable ComponentName componentName) {
        if (componentName == null) return true;
        int status = pm.getComponentEnabledSetting(componentName);
        return status == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
