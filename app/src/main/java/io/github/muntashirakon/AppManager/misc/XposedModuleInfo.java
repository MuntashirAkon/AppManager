// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.util.LocalizedString;
import io.github.muntashirakon.util.UiUtils;

// Source: https://github.com/LSPosed/LSPosed/blob/1c586fe41f22fac84c46d33db61e3a04ad528409/app/src/main/java/org/lsposed/manager/util/ModuleUtil.java#L88
// Copyright 2020 EdXposed Contributors
// Copyright 2021 LSPosed Contributors
// Copyright 2023 Muntashir Al-Islam
public class XposedModuleInfo implements LocalizedString {
    public static final String TAG = XposedModuleInfo.class.getSimpleName();

    @Nullable
    public static Boolean isXposedModule(@NonNull ApplicationInfo app, @NonNull ZipFile zipFile) {
        if (app.metaData != null && app.metaData.containsKey("xposedminversion")) {
            return null;
        }
        return zipFile.getEntry("META-INF/xposed/module.prop") != null;
    }

    public final String packageName;
    public final boolean legacy;
    public final int minVersion;
    public final int targetVersion;
    public final boolean staticScope;
    private final ApplicationInfo mApp;

    private CharSequence mAppLabel;
    private CharSequence mDescription;
    private List<String> mScopeList;

    public XposedModuleInfo(@NonNull ApplicationInfo applicationInfo, @Nullable ZipFile modernModuleApk) {
        mApp = applicationInfo;
        packageName = mApp.packageName;
        legacy = modernModuleApk == null;

        if (legacy) {
            Object minVersionRaw = mApp.metaData.get("xposedminversion");
            if (minVersionRaw instanceof Integer) {
                minVersion = (Integer) minVersionRaw;
            } else if (minVersionRaw instanceof String) {
                minVersion = extractIntPart((String) minVersionRaw);
            } else {
                minVersion = 0;
            }
            targetVersion = minVersion; // legacy modules don't have a target version
            staticScope = false;
        } else {
            int minVersion = 100;
            int targetVersion = 100;
            boolean staticScope = false;
            try {
                ZipEntry propEntry = modernModuleApk.getEntry("META-INF/xposed/module.prop");
                if (propEntry != null) {
                    Properties prop = new Properties();
                    prop.load(modernModuleApk.getInputStream(propEntry));
                    minVersion = extractIntPart(prop.getProperty("minApiVersion"));
                    targetVersion = extractIntPart(prop.getProperty("targetApiVersion"));
                    staticScope = TextUtils.equals(prop.getProperty("staticScope"), "true");
                }
                ZipEntry scopeEntry = modernModuleApk.getEntry("META-INF/xposed/scope.list");
                if (scopeEntry != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(modernModuleApk.getInputStream(scopeEntry)))) {
                        mScopeList = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            mScopeList.add(line);
                        }
                    }
                } else {
                    mScopeList = Collections.emptyList();
                }
            } catch (IOException | OutOfMemoryError e) {
                Log.e(TAG, "Error while reading modern module APK", e);
            }
            this.minVersion = minVersion;
            this.targetVersion = targetVersion;
            this.staticScope = staticScope;
        }
    }

    public CharSequence getAppLabel(@NonNull PackageManager pm) {
        if (mAppLabel == null)
            mAppLabel = mApp.loadLabel(pm);
        return mAppLabel;
    }

    public CharSequence getDescription(@NonNull PackageManager pm) {
        if (mDescription != null) return mDescription;
        CharSequence descriptionTmp = "";
        if (legacy) {
            Object descriptionRaw = mApp.metaData.get("xposeddescription");
            if (descriptionRaw instanceof String) {
                descriptionTmp = ((String) descriptionRaw).trim();
            } else if (descriptionRaw instanceof Integer) {
                try {
                    int resId = (Integer) descriptionRaw;
                    if (resId != 0)
                        descriptionTmp = pm.getResourcesForApplication(mApp).getString(resId).trim();
                } catch (Exception ignored) {
                }
            }
        } else {
            CharSequence des = mApp.loadDescription(pm);
            if (des != null) {
                descriptionTmp = des;
            }
        }
        mDescription = descriptionTmp;
        return mDescription;
    }

    public List<String> getScopeList(@NonNull PackageManager pm) {
        if (mScopeList != null) return mScopeList;
        List<String> list = null;
        try {
            int scopeListResourceId = mApp.metaData.getInt("xposedscope");
            if (scopeListResourceId != 0) {
                list = Arrays.asList(pm.getResourcesForApplication(mApp).getStringArray(scopeListResourceId));
            } else {
                String scopeListString = mApp.metaData.getString("xposedscope");
                if (scopeListString != null)
                    list = Arrays.asList(scopeListString.split(";"));
            }
        } catch (Exception ignored) {
        }
        if (list != null) {
            // For historical reasons, legacy modules use the opposite name.
            // https://github.com/rovo89/XposedBridge/commit/6b49688c929a7768f3113b4c65b429c7a7032afa
            list.replaceAll(s -> {
                switch (s) {
                    case "android":
                        return "system";
                    case "system":
                        return "android";
                    default:
                        return s;
                }
            });
            mScopeList = list;
        }
        return mScopeList;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        SpannableStringBuilder sb = new SpannableStringBuilder()
                .append(getStyledKeyValue(context, R.string.module_name, getAppLabel(pm))).append("\n")
                .append(getStyledKeyValue(context, R.string.title_description, getDescription(pm))).append("\n")
                .append(getStyledKeyValue(context, R.string.type, legacy ? "Legacy" : "Modern")).append("\n");
        if (legacy) {
            sb.append(getStyledKeyValue(context, "Xposed Minimum API", String.valueOf(minVersion))).append("\n");
        } else {
            sb.append(getStyledKeyValue(context, "Xposed API", "")).append("\n")
                    .append(getStyledKeyValue(context, "  Min", String.valueOf(minVersion))).append(", ")
                    .append(getStyledKeyValue(context, "Target", String.valueOf(targetVersion))).append("\n")
                    .append(getStyledKeyValue(context, "Scope", staticScope ? "Static" : "Dynamic")).append("\n");
        }
        List<String> scopeList = getScopeList(pm);
        if (scopeList != null && !scopeList.isEmpty()) {
            sb.append(getBoldString("Scopes")).append("\n").append(UiUtils.getOrderedList(scopeList));
        }
        return sb;
    }

    public static int extractIntPart(@NonNull String str) {
        int result = 0, length = str.length();
        for (int offset = 0; offset < length; offset++) {
            char c = str.charAt(offset);
            if ('0' <= c && c <= '9')
                result = result * 10 + (c - '0');
            else
                break;
        }
        return result;
    }
}
