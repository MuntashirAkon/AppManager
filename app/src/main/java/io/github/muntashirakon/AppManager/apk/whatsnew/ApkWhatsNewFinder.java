package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.TrackerComponentUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ApkWhatsNewFinder {
    @IntDef(value = {
            CHANGE_ADD,
            CHANGE_REMOVED,
            CHANGE_INFO
    })
    public @interface ChangeType {}
    public static final int CHANGE_ADD = 1;
    public static final int CHANGE_REMOVED = 2;
    public static final int CHANGE_INFO = 3;

    public static final int VERSION_INFO = 0;
    public static final int TRACKER_INFO = 1;
    public static final int SIGNING_CERT_SHA256 = 2;
    public static final int PERMISSION_INFO = 3;
    public static final int COMPONENT_INFO = 4;
    public static final int FEATURE_INFO = 5;
    public static final int SHARED_LIBRARIES = 6;
    public static final int SDK_INFO = 7;

    private static final int INFO_COUNT = 8;

    private final Set<String> tmpInfo = new HashSet<>();

    private static ApkWhatsNewFinder instance;
    public static ApkWhatsNewFinder getInstance() {
        if (instance == null) instance = new ApkWhatsNewFinder();
        return instance;
    }

    /**
     * Get changes between two packages: one is the apk file and other is the installed app
     * @param newPkgInfo Package info fetched with {@link PackageManager#getPackageArchiveInfo(String, int)}
     *                   with the following flags: {@link PackageManager#GET_META_DATA}, {@link PackageManager#GET_SIGNATURES}
     *                   {@link PackageManager#GET_PERMISSIONS}, {@link PackageManager#GET_CONFIGURATIONS},
     *                   {@link PackageManager#GET_SHARED_LIBRARY_FILES}
     * @param oldPkgInfo Package info fetched with {@link PackageManager#getPackageInfo(String, int)}
     *                   with the following flags: {@link PackageManager#GET_META_DATA}, {@link PackageManager#GET_SIGNATURES} or
     *                   {@link PackageManager#GET_SIGNING_CERTIFICATES}, {@link PackageManager#GET_PERMISSIONS},
     *                   {@link PackageManager#GET_CONFIGURATIONS}, {@link PackageManager#GET_SHARED_LIBRARY_FILES}
     * @return Changes
     */
    @NonNull
    public Change[][] getWhatsNew(@NonNull PackageInfo newPkgInfo, @NonNull PackageInfo oldPkgInfo) {
        Context context = AppManager.getContext();
        ApplicationInfo newAppInfo = newPkgInfo.applicationInfo;
        ApplicationInfo oldAppInfo = oldPkgInfo.applicationInfo;
        Change[][] changes = new Change[INFO_COUNT][];
        String[] componentInfo = context.getResources().getStringArray(R.array.whats_new_titles);
        // Version info
        String newVersionInfo = newPkgInfo.versionName + " (" + PackageUtils.getVersionCode(newPkgInfo) + ')';
        String oldVersionInfo = oldPkgInfo.versionName + " (" + PackageUtils.getVersionCode(oldPkgInfo) + ')';
        changes[VERSION_INFO] = new Change[]{
                new Change(CHANGE_INFO, componentInfo[VERSION_INFO]),
                new Change(CHANGE_ADD, newVersionInfo),
                new Change(CHANGE_REMOVED, oldVersionInfo)
        };
        // Tracker info
        HashMap<String, RulesStorageManager.Type> newPkgComponents = PackageUtils.collectComponentClassNames(newPkgInfo);
        HashMap<String, RulesStorageManager.Type> oldPkgComponents = PackageUtils.collectComponentClassNames(oldPkgInfo);
        List<Change> componentChanges = new ArrayList<>();
        componentChanges.add(new Change(CHANGE_INFO, componentInfo[COMPONENT_INFO]));
        componentChanges.addAll(findChanges(newPkgComponents.keySet(), oldPkgComponents.keySet()));
        int newTrackerCount = 0;
        int oldTrackerCount = 0;
        for (Change component: componentChanges) {
            if (TrackerComponentUtils.isTracker(component.value)) {
                if (component.changeType == CHANGE_ADD) ++newTrackerCount;
                else if (component.changeType == CHANGE_REMOVED) ++oldTrackerCount;
            }
        }
        if (newTrackerCount == 0 && oldTrackerCount == 0) {
            changes[TRACKER_INFO] = new Change[0];
        } else {
            Change newTrackers = new Change(CHANGE_ADD, context.getResources()
                    .getQuantityString(R.plurals.no_of_trackers, newTrackerCount, newTrackerCount));
            Change oldTrackers = new Change(CHANGE_REMOVED, context.getResources()
                    .getQuantityString(R.plurals.no_of_trackers, oldTrackerCount, oldTrackerCount));
            changes[TRACKER_INFO] = new Change[]{new Change(CHANGE_INFO, componentInfo[TRACKER_INFO]), newTrackers, oldTrackers};
        }
        // Sha256 of signing certificates
        Set<String> newCertSha256 = new HashSet<>();
        Set<String> oldCertSha256 = new HashSet<>(Arrays.asList(PackageUtils
                .getSigningCertSha256Checksum(oldPkgInfo)));
        Signature[] signatureArray = newPkgInfo.signatures;
        for (Signature signature: signatureArray) {
            try {
                newCertSha256.add(PackageUtils.byteToHexString(MessageDigest.getInstance("sha256")
                        .digest(signature.toByteArray())));
            } catch (NoSuchAlgorithmException ignore) {}
        }
        List<Change> certSha256Changes = new ArrayList<>();
        certSha256Changes.add(new Change(CHANGE_INFO, componentInfo[SIGNING_CERT_SHA256]));
        certSha256Changes.addAll(findChanges(newCertSha256, oldCertSha256));
        changes[SIGNING_CERT_SHA256] = certSha256Changes.size() == 1 ? new Change[0] : certSha256Changes.toArray(new Change[0]);
        // Permissions
        Set<String> newPermissions = new HashSet<>();
        Set<String> oldPermissions = new HashSet<>();
        if (newPkgInfo.permissions != null)
            for (PermissionInfo permissionInfo: newPkgInfo.permissions)
                newPermissions.add(permissionInfo.name);
        if (newPkgInfo.requestedPermissions != null)
            newPermissions.addAll(Arrays.asList(newPkgInfo.requestedPermissions));
        if (oldPkgInfo.permissions != null)
            for (PermissionInfo permissionInfo: oldPkgInfo.permissions)
                oldPermissions.add(permissionInfo.name);
        if (oldPkgInfo.requestedPermissions != null)
            oldPermissions.addAll(Arrays.asList(oldPkgInfo.requestedPermissions));
        List<Change> permissionChanges = new ArrayList<>();
        permissionChanges.add(new Change(CHANGE_INFO, componentInfo[PERMISSION_INFO]));
        permissionChanges.addAll(findChanges(newPermissions, oldPermissions));
        changes[PERMISSION_INFO] = permissionChanges.size() == 1 ? new Change[0] : permissionChanges.toArray(new Change[0]);
        // Component info
        changes[COMPONENT_INFO] = componentChanges.size() == 1 ? new Change[0] : componentChanges.toArray(new Change[0]);
        // Feature info
        Set<String> newFeatures = new HashSet<>();
        Set<String> oldFeatures = new HashSet<>();
        if (newPkgInfo.reqFeatures != null)
            for (FeatureInfo featureInfo: newPkgInfo.reqFeatures)
                if (featureInfo.name != null) newFeatures.add(featureInfo.name);
                else newFeatures.add("OpenGL ES v" + featureInfo.reqGlEsVersion);
        if (oldPkgInfo.reqFeatures != null)
            for (FeatureInfo featureInfo: oldPkgInfo.reqFeatures)
                if (featureInfo.name != null) oldFeatures.add(featureInfo.name);
                else oldFeatures.add("OpenGL ES v" + Utils.getOpenGL(featureInfo.reqGlEsVersion));
        List<Change> featureChanges = new ArrayList<>();
        featureChanges.add(new Change(CHANGE_INFO, componentInfo[FEATURE_INFO]));
        featureChanges.addAll(findChanges(newFeatures, oldFeatures));
        changes[FEATURE_INFO] = featureChanges.size() == 1 ? new Change[0] : featureChanges.toArray(new Change[0]);
        // Shared libraries
        Set<String> newSharedLibs = new HashSet<>();
        Set<String> oldSharedLibs = new HashSet<>();
        if (newAppInfo.sharedLibraryFiles != null)
            newSharedLibs.addAll(Arrays.asList(newAppInfo.sharedLibraryFiles));
        if (oldAppInfo.sharedLibraryFiles != null)
            oldSharedLibs.addAll(Arrays.asList(oldAppInfo.sharedLibraryFiles));
        List<Change> sharedLibChanges = new ArrayList<>();
        sharedLibChanges.add(new Change(CHANGE_INFO, componentInfo[SHARED_LIBRARIES]));
        sharedLibChanges.addAll(findChanges(newSharedLibs, oldSharedLibs));
        changes[SHARED_LIBRARIES] = sharedLibChanges.size() == 1 ? new Change[0] : sharedLibChanges.toArray(new Change[0]);
        // SDK
        final StringBuilder newSdk = new StringBuilder();
        newSdk.append(context.getString(R.string.sdk_max)).append(": ").append(newAppInfo.targetSdkVersion);
        if (Build.VERSION.SDK_INT > 23)
            newSdk.append(", ").append(context.getString(R.string.sdk_min)).append(": ").append(newAppInfo.minSdkVersion);
        final StringBuilder oldSdk = new StringBuilder();
        oldSdk.append(context.getString(R.string.sdk_max)).append(": ").append(oldAppInfo.targetSdkVersion);
        if (Build.VERSION.SDK_INT > 23)
            oldSdk.append(", ").append(context.getString(R.string.sdk_min)).append(": ").append(oldAppInfo.minSdkVersion);
        if (!newSdk.toString().equals(oldSdk.toString())) {
            changes[SDK_INFO] = new Change[]{
                    new Change(CHANGE_INFO, componentInfo[SHARED_LIBRARIES]),
                    new Change(CHANGE_ADD, newSdk.toString()),
                    new Change(CHANGE_REMOVED, oldSdk.toString())
            };
        } else changes[SDK_INFO] = new Change[0];
        return changes;
    }

    @NonNull
    private List<Change> findChanges(Set<String> newInfo, Set<String> oldInfo) {
        List<Change> changeList = new ArrayList<>();
        tmpInfo.clear();
        tmpInfo.addAll(newInfo);
        newInfo.removeAll(oldInfo);
        for (String info: newInfo) changeList.add(new Change(CHANGE_ADD, info));
        oldInfo.removeAll(tmpInfo);
        for (String info: oldInfo) changeList.add(new Change(CHANGE_REMOVED, info));
        return changeList;
    }

    public static class Change {
        public @ChangeType int changeType;
        public @NonNull String value;
        public Change(int changeType, @NonNull String value) {
            this.changeType = changeType;
            this.value = value;
        }

        @NonNull
        @Override
        public String toString() {
            return "Change{" +
                    "changeType=" + changeType +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}
