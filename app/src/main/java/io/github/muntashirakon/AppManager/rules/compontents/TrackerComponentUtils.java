package io.github.muntashirakon.AppManager.rules.compontents;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.oneclickops.ItemCount;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class TrackerComponentUtils {
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
    public static List<String> unblockTrackingComponents(@NonNull Context context, @NonNull Collection<String> packageNames) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = getTrackerComponentsForPackage(packageName);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
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
}
