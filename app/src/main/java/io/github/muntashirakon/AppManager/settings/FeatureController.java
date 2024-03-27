// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.SparseArrayCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.viewer.ExplorerActivity;

public class FeatureController {
    @IntDef(flag = true, value = {
            FEAT_INTERCEPTOR,
            FEAT_MANIFEST,
            FEAT_SCANNER,
            FEAT_INSTALLER,
            FEAT_USAGE_ACCESS,
            FEAT_LOG_VIEWER,
            FEAT_INTERNET,
            FEAT_APP_EXPLORER,
            FEAT_APP_INFO,
            FEAT_CODE_EDITOR,
            FEAT_VIRUS_TOTAL,
    })
    public @interface FeatureFlags {
    }

    private static final int FEAT_INTERCEPTOR = 1;
    private static final int FEAT_MANIFEST = 1 << 1;
    private static final int FEAT_SCANNER = 1 << 2;
    public static final int FEAT_INSTALLER = 1 << 3;
    public static final int FEAT_USAGE_ACCESS = 1 << 4;
    public static final int FEAT_LOG_VIEWER = 1 << 5;
    public static final int FEAT_INTERNET = 1 << 6;
    private static final int FEAT_APP_EXPLORER = 1 << 7;
    private static final int FEAT_APP_INFO = 1 << 8;
    private static final int FEAT_CODE_EDITOR = 1 << 9;
    public static final int FEAT_VIRUS_TOTAL = 1 << 10;

    @NonNull
    public static FeatureController getInstance() {
        return new FeatureController();
    }

    @FeatureFlags
    public static final List<Integer> featureFlags = new ArrayList<>();

    private static final LinkedHashMap<Integer, Integer> sFeatureFlagsMap = new LinkedHashMap<Integer, Integer>() {
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
            featureFlags.add(FEAT_APP_EXPLORER);
            put(FEAT_APP_EXPLORER, R.string.app_explorer);
            featureFlags.add(FEAT_APP_INFO);
            put(FEAT_APP_INFO, R.string.app_info);
            featureFlags.add(FEAT_CODE_EDITOR);
            put(FEAT_CODE_EDITOR, R.string.title_code_editor);
            featureFlags.add(FEAT_VIRUS_TOTAL);
            put(FEAT_VIRUS_TOTAL, R.string.virus_total);
        }
    };

    @NonNull
    public static CharSequence[] getFormattedFlagNames(@NonNull Context context) {
        CharSequence[] flagNames = new CharSequence[featureFlags.size()];
        for (int i = 0; i < flagNames.length; ++i) {
            flagNames[i] = context.getText(Objects.requireNonNull(sFeatureFlagsMap.get(featureFlags.get(i))));
        }
        return flagNames;
    }

    private static final SparseArrayCompat<ComponentName> sComponentCache = new SparseArrayCompat<>(4);

    private final String mPackageName = BuildConfig.APPLICATION_ID;
    private final PackageManager mPm;
    private int mFlags;

    private FeatureController() {
        mPm = ContextUtils.getContext().getPackageManager();
        mFlags = AppPref.getInt(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT);
    }

    public int getFlags() {
        return mFlags;
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

    public static boolean isInternetEnabled() {
        return getInstance().isEnabled(FEAT_INTERNET);
    }

    public static boolean isVirusTotalEnabled() {
        return getInstance().isEnabled(FEAT_VIRUS_TOTAL);
    }

    private boolean isEnabled(@FeatureFlags int key) {
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
                return (mFlags & key) != 0;
            case FEAT_VIRUS_TOTAL:
                return (mFlags & key) != 0 && isEnabled(FEAT_INTERNET);
            case FEAT_INTERNET:
                return (mFlags & key) != 0 && SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET);
            case FEAT_LOG_VIEWER:
                cn = getComponentName(key, LogViewerActivity.class);
                break;
            case FEAT_APP_EXPLORER:
                cn = getComponentName(key, ExplorerActivity.class);
                break;
            case FEAT_APP_INFO:
                cn = getComponentName(key, AppDetailsActivity.ALIAS_APP_INFO);
                break;
            case FEAT_CODE_EDITOR:
                cn = getComponentName(key, CodeEditorActivity.ALIAS_EDITOR);
                break;
            default:
                throw new IllegalArgumentException();
        }
        return isComponentEnabled(cn) && (mFlags & key) != 0;
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
            case FEAT_INTERNET:
            case FEAT_VIRUS_TOTAL:
                // Only depends on flag
                break;
            case FEAT_LOG_VIEWER:
                modifyState(key, LogViewerActivity.class, enabled);
                break;
            case FEAT_APP_EXPLORER:
                modifyState(key, ExplorerActivity.class, enabled);
                break;
            case FEAT_APP_INFO:
                modifyState(key, AppDetailsActivity.ALIAS_APP_INFO, enabled);
                break;
            case FEAT_CODE_EDITOR:
                modifyState(key, CodeEditorActivity.ALIAS_EDITOR, enabled);
                break;
        }
        // Modify flags
        mFlags = enabled ? (mFlags | key) : (mFlags & ~key);
        // Save to pref
        AppPref.set(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT, mFlags);
    }

    private void modifyState(@FeatureFlags int key, @Nullable Class<? extends AppCompatActivity> clazz, boolean enabled) {
        ComponentName cn = getComponentName(key, clazz);
        if (cn == null) return;
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        mPm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
    }

    private void modifyState(@FeatureFlags int key, @Nullable String name, boolean enabled) {
        ComponentName cn = getComponentName(key, name);
        if (cn == null) return;
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        mPm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
    }

    @Nullable
    private ComponentName getComponentName(@FeatureFlags int key, @Nullable Class<? extends AppCompatActivity> clazz) {
        if (clazz == null) return null;
        ComponentName cn = sComponentCache.get(key);
        if (cn == null) {
            cn = new ComponentName(mPackageName, clazz.getName());
            sComponentCache.put(key, cn);
        }
        return cn;
    }

    @Nullable
    private ComponentName getComponentName(@FeatureFlags int key, @Nullable String name) {
        if (name == null) return null;
        ComponentName cn = sComponentCache.get(key);
        if (cn == null) {
            cn = new ComponentName(mPackageName, name);
            sComponentCache.put(key, cn);
        }
        return cn;
    }

    private boolean isComponentEnabled(@Nullable ComponentName componentName) {
        if (componentName == null) return true;
        int status = mPm.getComponentEnabledSetting(componentName);
        return status == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
