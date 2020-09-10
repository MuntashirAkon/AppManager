/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.rules.compontents;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.oneclickops.ItemCount;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class ComponentUtils {
    public static boolean isTracker(String componentName) {
        for(String signature: StaticDataset.getTrackerCodeSignatures()) {
            if (componentName.startsWith(signature) || componentName.contains(signature)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getTrackerComponentsForPackage(String packageName) {
        HashMap<String, RulesStorageManager.Type> trackers = new HashMap<>();
        HashMap<String, RulesStorageManager.Type> components = PackageUtils.collectComponentClassNames(packageName);
        for (String componentName: components.keySet()) {
            if (isTracker(componentName))
                trackers.put(componentName, components.get(componentName));
        }
        return trackers;
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getTrackerComponentsForPackageInfo(PackageInfo packageInfo) {
        HashMap<String, RulesStorageManager.Type> trackers = new HashMap<>();
        HashMap<String, RulesStorageManager.Type> components = PackageUtils.collectComponentClassNames(packageInfo);
        for (String componentName: components.keySet()) {
            if (isTracker(componentName))
                trackers.put(componentName, components.get(componentName));
        }
        return trackers;
    }

    @NonNull
    public static List<String> blockTrackingComponents(@NonNull Collection<String> packageNames) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = ComponentUtils.getTrackerComponentsForPackage(packageName);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName)) {
                for (String componentName: components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> unblockTrackingComponents(@NonNull Collection<String> packageNames) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = getTrackerComponentsForPackage(packageName);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName)) {
                for (String componentName: components.keySet()) {
                    cb.removeComponent(componentName);
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> blockFilteredComponents(@NonNull Collection<String> packageNames, String[] signatures) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = PackageUtils.getFilteredComponents(packageName, signatures);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName)) {
                for (String componentName: components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<ItemCount> getTrackerCountsForPackages(@NonNull List<String> packages) {
        List<ItemCount> trackerCounts = new ArrayList<>();
        PackageManager pm = AppManager.getContext().getPackageManager();
        for (String packageName: packages) {
            ItemCount trackerCount = new ItemCount();
            trackerCount.packageName = packageName;
            try {
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                trackerCount.packageLabel = info.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                trackerCount.packageLabel = packageName;
                e.printStackTrace();
            }
            trackerCount.count = getTrackerComponentsForPackage(packageName).size();
        }
        return trackerCounts;
    }

    @NonNull
    public static ItemCount getTrackerCountForApp(@NonNull ApplicationInfo applicationInfo) {
        PackageManager pm = AppManager.getContext().getPackageManager();
        ItemCount trackerCount = new ItemCount();
        trackerCount.packageName = applicationInfo.packageName;
        trackerCount.packageLabel = applicationInfo.loadLabel(pm).toString();
        trackerCount.count = getTrackerComponentsForPackage(applicationInfo.packageName).size();
        return trackerCount;
    }

    @NonNull
    public static List<String> getAllPackagesWithRules() {
        List<String> packages = new ArrayList<>();
        File confDir = RulesStorageManager.getConfDir();
        String[] names = confDir.list((dir, name) -> name.endsWith(".tsv"));
        if (names != null) {
            for (String name: names) {
                packages.add(Utils.trimExtension(name));
            }
        }
        return packages;
    }

    public static void removeAllRules(@NonNull String packageName) {
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName)) {
            // Remove all blocking rules
            for (RulesStorageManager.Entry entry: cb.getAllComponents()) {
                cb.removeComponent(entry.name);
            }
            cb.applyRules(true);
            // Reset configured app ops
            AppOpsService appOpsService = new AppOpsService();
            for (RulesStorageManager.Entry entry: cb.getAll(RulesStorageManager.Type.APP_OP)) {
                try {
                    appOpsService.setMode((Integer) entry.extra, -1, packageName, AppOpsManager.MODE_DEFAULT);
                    cb.removeEntry(entry);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Grant configured permissions
            for (RulesStorageManager.Entry entry: cb.getAll(RulesStorageManager.Type.PERMISSION)) {
                if (RunnerUtils.grantPermission(packageName, entry.name, Users.getCurrentUserHandle()).isSuccessful()) {
                    cb.removeEntry(entry);
                }
            }
        }
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getIFWRulesForPackage(@NonNull String packageName) {
        HashMap<String, RulesStorageManager.Type> rules = new HashMap<>();
        if (RootShellRunner.runCommand(String.format("ls %s/%s*.xml", ComponentsBlocker.SYSTEM_RULES_PATH, packageName)).isSuccessful()) {
            List<String> ifwRulesFiles = RootShellRunner.getLastResult().getOutputAsList();
            for (String ifwRulesFile: ifwRulesFiles) {
                // Get file contents
                if (RootShellRunner.runCommand(String.format("cat %s", ifwRulesFile)).isSuccessful()) {
                    String xmlContents = RootShellRunner.getLastResult().getOutput();
                    try (InputStream inputStream = new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8))) {
                        // Read rules
                        rules.putAll(readIFWRules(inputStream, packageName));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return rules;
    }

    public static final String TAG_ACTIVITY = "activity";
    public static final String TAG_RECEIVER = "broadcast";
    public static final String TAG_SERVICE = "service";

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> readIFWRules(@NonNull InputStream inputStream, @NonNull String packageName) {
        HashMap<String, RulesStorageManager.Type> rules = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "rules");
            int event = parser.nextTag();
            RulesStorageManager.Type componentType = RulesStorageManager.Type.UNKNOWN;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals(TAG_ACTIVITY) || name.equals(TAG_RECEIVER) || name.equals(TAG_SERVICE)) {
                            componentType = getComponentType(name);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("component-filter")) {
                            String fullKey = parser.getAttributeValue(null, "name");
                            int divider = fullKey.indexOf('/');
                            String pkgName = fullKey.substring(0, divider);
                            String componentName = fullKey.substring(divider + 1);
                            if (componentName.startsWith(".")) componentName = packageName + componentName;
                            if (pkgName.equals(packageName)) {
                                rules.put(componentName, componentType);
                            }
                        }
                }
                event = parser.nextTag();
            }
        } catch (XmlPullParserException | IOException ignore) {}
        return rules;
    }

    /**
     * Get component type from TAG_* constants
     * @param componentName Name of the constant: one of the TAG_*
     * @return One of the {@link RulesStorageManager.Type}
     */
    static RulesStorageManager.Type getComponentType(@NonNull String componentName) {
        switch (componentName) {
            case TAG_ACTIVITY: return RulesStorageManager.Type.ACTIVITY;
            case TAG_RECEIVER: return RulesStorageManager.Type.RECEIVER;
            case TAG_SERVICE: return RulesStorageManager.Type.SERVICE;
            default: return RulesStorageManager.Type.UNKNOWN;
        }
    }
}
