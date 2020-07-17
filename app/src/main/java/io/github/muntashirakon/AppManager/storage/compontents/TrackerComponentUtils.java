package io.github.muntashirakon.AppManager.storage.compontents;

import java.util.HashMap;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
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
}
