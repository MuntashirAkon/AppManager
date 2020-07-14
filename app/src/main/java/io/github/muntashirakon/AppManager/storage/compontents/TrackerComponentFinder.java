package io.github.muntashirakon.AppManager.storage.compontents;

import io.github.muntashirakon.AppManager.StaticDataset;

public class TrackerComponentFinder {
    public static boolean isTracker(String componentName) {
        for(String signature: StaticDataset.getTrackerCodeSignatures()) {
            if (componentName.startsWith(signature) || componentName.contains(signature)) {
                return true;
            }
        }
        return false;
    }
}
