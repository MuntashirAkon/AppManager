package io.github.muntashirakon.AppManager;

public class StaticDataset {
    private static String[] trackerCodeSignatures;
    private static String[] trackerNames;

    public static String[] getTrackerCodeSignatures() {
        if (trackerCodeSignatures == null) {
            trackerCodeSignatures = AppManager.getContext().getResources().getStringArray(R.array.tracker_signatures);
        }
        return trackerCodeSignatures;
    }

    public static String[] getTrackerNames() {
        if (trackerNames == null) {
            trackerNames = AppManager.getContext().getResources().getStringArray(R.array.tracker_names);
        }
        return trackerNames;
    }
}
