// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;

public final class ComponentUtils {
    public static boolean isTracker(String componentName) {
        for (String signature : StaticDataset.getTrackerCodeSignatures()) {
            if (componentName.startsWith(signature) || componentName.contains(signature)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static HashMap<String, RuleType> getTrackerComponentsForPackage(PackageInfo packageInfo) {
        HashMap<String, RuleType> trackers = new HashMap<>();
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageInfo);
        for (String componentName : components.keySet()) {
            if (isTracker(componentName))
                trackers.put(componentName, components.get(componentName));
        }
        return trackers;
    }

    @NonNull
    public static HashMap<String, RuleType> getTrackerComponentsForPackage(String packageName, @UserIdInt int userHandle) {
        HashMap<String, RuleType> trackers = new HashMap<>();
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageName, userHandle);
        for (String componentName : components.keySet()) {
            if (isTracker(componentName))
                trackers.put(componentName, components.get(componentName));
        }
        return trackers;
    }

    @NonNull
    public static HashMap<String, RuleType> getTrackerComponentsForPackageInfo(PackageInfo packageInfo) {
        HashMap<String, RuleType> trackers = new HashMap<>();
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageInfo);
        for (String componentName : components.keySet()) {
            if (isTracker(componentName))
                trackers.put(componentName, components.get(componentName));
        }
        return trackers;
    }

    public static void blockTrackingComponents(@NonNull UserPackagePair pair) {
        HashMap<String, RuleType> components = ComponentUtils.getTrackerComponentsForPackage(pair.getPackageName(), pair.getUserHandle());
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
            for (String componentName : components.keySet()) {
                cb.addComponent(componentName, components.get(componentName));
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> blockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                blockTrackingComponents(pair);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    public static void unblockTrackingComponents(@NonNull UserPackagePair pair) {
        HashMap<String, RuleType> components = getTrackerComponentsForPackage(pair.getPackageName(), pair.getUserHandle());
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
            for (String componentName : components.keySet()) {
                cb.removeComponent(componentName);
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> unblockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                unblockTrackingComponents(pair);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    public static void blockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        HashMap<String, RuleType> components = PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserHandle(), signatures);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
            for (String componentName : components.keySet()) {
                cb.addComponent(componentName, components.get(componentName));
            }
            cb.applyRules(true);
        }
    }

    public static void unblockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        HashMap<String, RuleType> components = PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserHandle(), signatures);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
            for (String componentName : components.keySet()) {
                cb.removeComponent(componentName);
            }
            cb.applyRules(true);
        }
    }

    public static void storeRules(@NonNull OutputStream os, @NonNull List<RuleEntry> rules, boolean isExternal)
            throws IOException {
        for (RuleEntry entry : rules) {
            os.write((entry.flattenToString(isExternal) + "\n").getBytes());
        }
    }

    @NonNull
    public static List<String> getAllPackagesWithRules() {
        List<String> packages = new ArrayList<>();
        Path confDir = RulesStorageManager.getConfDir();
        Path[] paths = confDir.listFiles((dir, name) -> name.endsWith(".tsv"));
        for (Path path : paths) {
            packages.add(FileUtils.trimExtension(path.getUri().getLastPathSegment()));
        }
        return packages;
    }

    @WorkerThread
    public static void removeAllRules(@NonNull String packageName, int userHandle) {
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
            // Remove all blocking rules
            for (ComponentRule entry : cb.getAllComponents()) {
                cb.removeComponent(entry.name);
            }
            cb.applyRules(true);
            // Reset configured app ops
            AppOpsService appOpsService = new AppOpsService();
            try {
                appOpsService.resetAllModes(userHandle, packageName);
                for (AppOpRule entry : cb.getAll(AppOpRule.class)) {
                    try {
                        appOpsService.setMode(entry.getOp(), uid, packageName, AppOpsManager.MODE_DEFAULT);
                        cb.removeEntry(entry);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Grant configured permissions
            for (PermissionRule entry : cb.getAll(PermissionRule.class)) {
                try {
                    PermissionCompat.grantPermission(packageName, entry.name, userHandle);
                    cb.removeEntry(entry);
                } catch (RemoteException e) {
                    Log.e("ComponentUtils", "Cannot revoke permission " + entry.name + " for package " + packageName, e);
                }
            }
        }
    }

    @NonNull
    public static HashMap<String, RuleType> getIFWRulesForPackage(@NonNull String packageName) {
        return getIFWRulesForPackage(packageName, ComponentsBlocker.SYSTEM_RULES_PATH);
    }

    @VisibleForTesting
    @NonNull
    public static HashMap<String, RuleType> getIFWRulesForPackage(@NonNull String packageName, @NonNull ProxyFile path) {
        HashMap<String, RuleType> rules = new HashMap<>();
        ProxyFile[] files = path.listFiles((dir, name) -> {
            // For our case, name must start with package name to support apps like Watt, Blocker and MyAndroidTools,
            // and to prevent unwanted situation, such as when the contains unsupported tags such as intent-filter.
            return name.startsWith(packageName) && name.endsWith(".xml");
        });
        if (files != null) {
            for (ProxyFile ifwRulesFile : files) {
                // Get file contents
                try (InputStream inputStream = new ProxyInputStream(ifwRulesFile)) {
                    // Read rules
                    rules.putAll(readIFWRules(inputStream, packageName));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return rules;
    }

    public static final String TAG_RULES = "rules";

    public static final String TAG_ACTIVITY = "activity";
    public static final String TAG_BROADCAST = "broadcast";
    public static final String TAG_SERVICE = "service";

    @NonNull
    public static HashMap<String, RuleType> readIFWRules(@NonNull InputStream inputStream, @NonNull String packageName) {
        HashMap<String, RuleType> rules = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, TAG_RULES);
            int event = parser.nextTag();
            RuleType componentType = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals(TAG_ACTIVITY) || name.equals(TAG_BROADCAST) || name.equals(TAG_SERVICE)) {
                            componentType = getComponentType(name);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("component-filter")) {
                            String fullKey = parser.getAttributeValue(null, "name");
                            ComponentName cn = ComponentName.unflattenFromString(fullKey);
                            if (cn.getPackageName().equals(packageName)) {
                                rules.put(cn.getClassName(), componentType);
                            }
                        }
                }
                event = parser.nextTag();
            }
        } catch (Throwable ignore) {
            // The file contains errors, simply ignore
        }
        return rules;
    }

    /**
     * Get component type from TAG_* constants
     *
     * @param componentTag Name of the constant: one of the TAG_*
     * @return One of the {@link RuleType}
     */
    @Nullable
    static RuleType getComponentType(@NonNull String componentTag) {
        switch (componentTag) {
            case TAG_ACTIVITY:
                return RuleType.ACTIVITY;
            case TAG_BROADCAST:
                return RuleType.RECEIVER;
            case TAG_SERVICE:
                return RuleType.SERVICE;
            default:
                return null;
        }
    }
}
