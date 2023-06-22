// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ApkWhatsNewFinder {
    @IntDef(value = {
            CHANGE_ADD,
            CHANGE_REMOVED,
            CHANGE_INFO
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChangeType {
    }

    public static final int CHANGE_ADD = 1;
    public static final int CHANGE_REMOVED = 2;
    public static final int CHANGE_INFO = 3;

    public static final int VERSION_INFO = 0;
    public static final int TRACKER_INFO = 1;
    public static final int SIGNING_CERT_SHA256 = 2;
    public static final int PERMISSION_INFO = 3;
    public static final int COMPONENT_INFO = 4;
    public static final int FEATURE_INFO = 5;
    public static final int SDK_INFO = 6;

    private static final int INFO_COUNT = 7;

    private static ApkWhatsNewFinder sInstance;

    public static ApkWhatsNewFinder getInstance() {
        if (sInstance == null) sInstance = new ApkWhatsNewFinder();
        return sInstance;
    }

    private final Set<String> mTmpInfo = new HashSet<>();

    /**
     * Get changes between two packages: one is the apk file and other is the installed app
     *
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
    @WorkerThread
    @NonNull
    public Change[][] getWhatsNew(@NonNull Context context, @NonNull PackageInfo newPkgInfo,
                                  @NonNull PackageInfo oldPkgInfo) {
        ApplicationInfo newAppInfo = newPkgInfo.applicationInfo;
        ApplicationInfo oldAppInfo = oldPkgInfo.applicationInfo;
        Change[][] changes = new Change[INFO_COUNT][];
        String[] componentInfo = context.getResources().getStringArray(R.array.whats_new_titles);
        // Version info
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPkgInfo);
        long oldVersionCode = PackageInfoCompat.getLongVersionCode(oldPkgInfo);
        if (newVersionCode != oldVersionCode) {
            String newVersionInfo = newPkgInfo.versionName + " (" + newVersionCode + ')';
            String oldVersionInfo = oldPkgInfo.versionName + " (" + oldVersionCode + ')';
            changes[VERSION_INFO] = new Change[]{
                    new Change(CHANGE_INFO, componentInfo[VERSION_INFO]),
                    new Change(CHANGE_ADD, newVersionInfo),
                    new Change(CHANGE_REMOVED, oldVersionInfo)
            };
        } else changes[VERSION_INFO] = ArrayUtils.emptyArray(Change.class);
        if (ThreadUtils.isInterrupted()) {
            return changes;
        }
        // Tracker info
        HashMap<String, RuleType> newPkgComponents = PackageUtils.collectComponentClassNames(newPkgInfo);
        HashMap<String, RuleType> oldPkgComponents = PackageUtils.collectComponentClassNames(oldPkgInfo);
        List<Change> componentChanges = new ArrayList<>();
        componentChanges.add(new Change(CHANGE_INFO, componentInfo[COMPONENT_INFO]));
        componentChanges.addAll(findChanges(newPkgComponents.keySet(), oldPkgComponents.keySet()));
        int newTrackerCount = 0;
        int oldTrackerCount = 0;
        for (Change component : componentChanges) {
            if (ComponentUtils.isTracker(component.value)) {
                if (component.changeType == CHANGE_ADD) ++newTrackerCount;
                else if (component.changeType == CHANGE_REMOVED) ++oldTrackerCount;
            }
        }
        if (newTrackerCount == 0 && oldTrackerCount == 0) {
            changes[TRACKER_INFO] = ArrayUtils.emptyArray(Change.class);
        } else {
            Change newTrackers = new Change(CHANGE_ADD, context.getResources()
                    .getQuantityString(R.plurals.no_of_trackers, newTrackerCount, newTrackerCount));
            Change oldTrackers = new Change(CHANGE_REMOVED, context.getResources()
                    .getQuantityString(R.plurals.no_of_trackers, oldTrackerCount, oldTrackerCount));
            changes[TRACKER_INFO] = new Change[]{new Change(CHANGE_INFO, componentInfo[TRACKER_INFO]), newTrackers, oldTrackers};
        }
        if (ThreadUtils.isInterrupted()) {
            return changes;
        }
        // Sha256 of signing certificates
        Set<String> newCertSha256 = new HashSet<>(Arrays.asList(PackageUtils.getSigningCertSha256Checksum(newPkgInfo, true)));
        Set<String> oldCertSha256 = new HashSet<>(Arrays.asList(PackageUtils.getSigningCertSha256Checksum(oldPkgInfo)));
        List<Change> certSha256Changes = new ArrayList<>();
        certSha256Changes.add(new Change(CHANGE_INFO, componentInfo[SIGNING_CERT_SHA256]));
        certSha256Changes.addAll(findChanges(newCertSha256, oldCertSha256));
        changes[SIGNING_CERT_SHA256] = certSha256Changes.size() == 1 ? ArrayUtils.emptyArray(Change.class) : certSha256Changes.toArray(new Change[0]);
        if (ThreadUtils.isInterrupted()) {
            return changes;
        }
        // Permissions
        Set<String> newPermissions = new HashSet<>();
        Set<String> oldPermissions = new HashSet<>();
        if (newPkgInfo.permissions != null)
            for (PermissionInfo permissionInfo : newPkgInfo.permissions)
                newPermissions.add(permissionInfo.name);
        if (newPkgInfo.requestedPermissions != null)
            newPermissions.addAll(Arrays.asList(newPkgInfo.requestedPermissions));
        if (oldPkgInfo.permissions != null)
            for (PermissionInfo permissionInfo : oldPkgInfo.permissions)
                oldPermissions.add(permissionInfo.name);
        if (oldPkgInfo.requestedPermissions != null)
            oldPermissions.addAll(Arrays.asList(oldPkgInfo.requestedPermissions));
        List<Change> permissionChanges = new ArrayList<>();
        permissionChanges.add(new Change(CHANGE_INFO, componentInfo[PERMISSION_INFO]));
        permissionChanges.addAll(findChanges(newPermissions, oldPermissions));
        changes[PERMISSION_INFO] = permissionChanges.size() == 1 ? ArrayUtils.emptyArray(Change.class) : permissionChanges.toArray(new Change[0]);
        // Component info
        changes[COMPONENT_INFO] = componentChanges.size() == 1 ? ArrayUtils.emptyArray(Change.class) : componentChanges.toArray(new Change[0]);
        if (ThreadUtils.isInterrupted()) {
            return changes;
        }
        // Feature info
        Set<String> newFeatures = new HashSet<>();
        Set<String> oldFeatures = new HashSet<>();
        if (newPkgInfo.reqFeatures != null)
            for (FeatureInfo featureInfo : newPkgInfo.reqFeatures)
                if (featureInfo.name != null) newFeatures.add(featureInfo.name);
                else newFeatures.add("OpenGL ES v" + Utils.getGlEsVersion(featureInfo.reqGlEsVersion));
        if (oldPkgInfo.reqFeatures != null)
            for (FeatureInfo featureInfo : oldPkgInfo.reqFeatures)
                if (featureInfo.name != null) oldFeatures.add(featureInfo.name);
                else oldFeatures.add("OpenGL ES v" + Utils.getGlEsVersion(featureInfo.reqGlEsVersion));
        List<Change> featureChanges = new ArrayList<>();
        featureChanges.add(new Change(CHANGE_INFO, componentInfo[FEATURE_INFO]));
        featureChanges.addAll(findChanges(newFeatures, oldFeatures));
        changes[FEATURE_INFO] = featureChanges.size() == 1 ? ArrayUtils.emptyArray(Change.class) : featureChanges.toArray(new Change[0]);
        if (ThreadUtils.isInterrupted()) {
            return changes;
        }
        // SDK
        final StringBuilder newSdk = new StringBuilder(context.getString(R.string.sdk_max))
                .append(LangUtils.getSeparatorString()).append(newAppInfo.targetSdkVersion);
        final StringBuilder oldSdk = new StringBuilder(context.getString(R.string.sdk_max))
                .append(LangUtils.getSeparatorString()).append(oldAppInfo.targetSdkVersion);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            newSdk.append(", ").append(context.getString(R.string.sdk_min))
                    .append(LangUtils.getSeparatorString()).append(newAppInfo.minSdkVersion);
            oldSdk.append(", ").append(context.getString(R.string.sdk_min))
                    .append(LangUtils.getSeparatorString()).append(oldAppInfo.minSdkVersion);
        }
        if (!newSdk.toString().equals(oldSdk.toString())) {
            changes[SDK_INFO] = new Change[]{
                    new Change(CHANGE_INFO, componentInfo[SDK_INFO]),
                    new Change(CHANGE_ADD, newSdk.toString()),
                    new Change(CHANGE_REMOVED, oldSdk.toString())
            };
        } else changes[SDK_INFO] = ArrayUtils.emptyArray(Change.class);
        return changes;
    }

    @NonNull
    private List<Change> findChanges(Set<String> newInfo, Set<String> oldInfo) {
        List<Change> changeList = new ArrayList<>();
        mTmpInfo.clear();
        mTmpInfo.addAll(newInfo);
        newInfo.removeAll(oldInfo);
        for (String info : newInfo) changeList.add(new Change(CHANGE_ADD, info));
        oldInfo.removeAll(mTmpInfo);
        for (String info : oldInfo) changeList.add(new Change(CHANGE_REMOVED, info));
        return changeList;
    }

    public static class Change {
        @ChangeType
        public int changeType;
        @NonNull
        public String value;

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
