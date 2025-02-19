// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.CachedApkSource;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.OverlayManagerCompact;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsDefinedPermissionItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsFeatureItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsLibraryItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsOverlayItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsServiceItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView.ChoiceGenerator;
import io.github.muntashirakon.AppManager.permission.DevelopmentPermission;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.PermissionException;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;
import io.github.muntashirakon.AppManager.permission.RuntimePermission;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.scanner.NativeLibraries;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.IoUtils;

public class AppDetailsViewModel extends AndroidViewModel {
    public static final String TAG = AppDetailsViewModel.class.getSimpleName();

    private final PackageManager mPackageManager;
    private final Object mBlockerLocker = new Object();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(4);
    private final CountDownLatch mPackageInfoWatcher = new CountDownLatch(1);
    private final MutableLiveData<PackageInfo> mPackageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mTagsAlteredLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mFreezeTypeLiveData = new MutableLiveData<>();
    private final MutableLiveData<AppDetailsComponentItem> mComponentChangedLiveData = new MutableLiveData<>();

    @Nullable
    private PackageInfo mPackageInfo;
    @Nullable
    private PackageInfo mInstalledPackageInfo;
    @Nullable
    private String mPackageName;
    @GuardedBy("blockerLocker")
    private ComponentsBlocker mBlocker;
    @Nullable
    private PackageIntentReceiver mReceiver;
    @Nullable
    private String mApkPath;
    @Nullable
    private ApkSource mApkSource;
    @Nullable
    private ApkFile mApkFile;
    private int mUserId;
    @AppDetailsFragment.SortOrder
    private int mSortOrderComponents = Prefs.AppDetailsPage.getComponentsSortOrder();
    @AppDetailsFragment.SortOrder
    private int mSortOrderAppOps = Prefs.AppDetailsPage.getAppOpsSortOrder();
    @AppDetailsFragment.SortOrder
    private int mSortOrderPermissions = Prefs.AppDetailsPage.getPermissionsSortOrder();
    @AppDetailsFragment.SortOrder
    private int mSortOrderOverlays = Prefs.AppDetailsPage.getOverlaysSortOrder();
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType;
    private boolean mWaitForBlocker;
    private boolean mExternalApk = false;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        mPackageManager = application.getPackageManager();
        mReceiver = new PackageIntentReceiver(this);
        mWaitForBlocker = true;
    }

    @GuardedBy("blockerLocker")
    @Override
    public void onCleared() {
        Log.d(TAG, "On Clear called for %s", mPackageName);
        super.onCleared();
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                if (mBlocker != null) {
                    // To prevent commit if a mutable instance was created in the middle,
                    // set the instance read only again
                    mBlocker.setReadOnly();
                    mBlocker.close();
                }
            }
        });
        if (mReceiver != null) {
            getApplication().unregisterReceiver(mReceiver);
        }
        mReceiver = null;
        IoUtils.closeQuietly(mApkFile);
        if (mApkSource instanceof CachedApkSource) {
            ((CachedApkSource) mApkSource).cleanup();
        }
        mExecutor.shutdownNow();
    }

    public LiveData<Integer> getFreezeTypeLiveData() {
        return mFreezeTypeLiveData;
    }

    public void loadFreezeType() {
        mExecutor.submit(() -> {
            Integer freezeType = FreezeUtils.getFreezingMethod(mPackageName);
            mFreezeTypeLiveData.postValue(freezeType);
        });
    }

    public MutableLiveData<Boolean> getTagsAlteredLiveData() {
        return mTagsAlteredLiveData;
    }

    @UiThread
    @NonNull
    public LiveData<PackageInfo> setPackage(@NonNull ApkSource apkSource) {
        mApkSource = apkSource;
        mExternalApk = true;
        mExecutor.submit(() -> {
            try {
                Log.d(TAG, "Package Uri is being set");
                mApkFile = mApkSource.resolve();
                setPackageName(mApkFile.getPackageName());
                File cachedApkFile = mApkFile.getBaseEntry().getFile(false);
                if (!cachedApkFile.canRead()) throw new Exception("Cannot read " + cachedApkFile);
                mApkPath = cachedApkFile.getAbsolutePath();
                setPackageInfo(false);
                mPackageInfoLiveData.postValue(getPackageInfo());
            } catch (Throwable th) {
                Log.e(TAG, "Could not fetch package info.", th);
                mPackageInfoLiveData.postValue(null);
            } finally {
                mPackageInfoWatcher.countDown();
            }
        });
        return mPackageInfoLiveData;
    }

    @UiThread
    @NonNull
    public LiveData<PackageInfo> setPackage(@NonNull String packageName) {
        mExternalApk = false;
        mExecutor.submit(() -> {
            try {
                Log.d(TAG, "Package name is being set");
                setPackageName(packageName);
                // TODO: 23/5/21 The app could be “data only”
                setPackageInfo(false);
                PackageInfo pi = getPackageInfo();
                if (pi == null) throw new ApkFile.ApkFileException("Package not installed.");
                mApkSource = ApkSource.getApkSource(pi.applicationInfo);
                mApkFile = mApkSource.resolve();
                mPackageInfoLiveData.postValue(pi);
            } catch (Throwable th) {
                Log.e(TAG, "Could not fetch package info.", th);
                mPackageInfoLiveData.postValue(null);
            } finally {
                mPackageInfoWatcher.countDown();
            }
        });
        return mPackageInfoLiveData;
    }

    @AnyThread
    public void setUserId(@UserIdInt int userId) {
        mUserId = userId;
    }

    @AnyThread
    public int getUserId() {
        return mUserId;
    }

    @AnyThread
    @GuardedBy("blockerLocker")
    private void setPackageName(String packageName) {
        if (mPackageName != null) return;
        Log.d(TAG, "Package name is being set for %s", packageName);
        mPackageName = packageName;
        if (mExternalApk) return;
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                try {
                    mWaitForBlocker = true;
                    if (mBlocker != null) {
                        // To prevent commit if a mutable instance was created in the middle,
                        // set the instance read only again
                        mBlocker.setReadOnly();
                        mBlocker.close();
                    }
                    mBlocker = ComponentsBlocker.getInstance(packageName, mUserId);
                } finally {
                    mWaitForBlocker = false;
                    mBlockerLocker.notifyAll();
                }
            }
        });
    }

    @AnyThread
    public String getPackageName() {
        return mPackageName;
    }

    @Nullable
    public ApkFile getApkFile() {
        return mApkFile;
    }

    @AnyThread
    @Nullable
    public ApkSource getApkSource() {
        return mApkSource;
    }

    public boolean isTestOnlyApp() {
        return mPackageInfo != null && ApplicationInfoCompat.isTestOnly(mPackageInfo.applicationInfo);
    }

    @AnyThread
    @SuppressLint("SwitchIntDef")
    public void setSortOrder(@AppDetailsFragment.SortOrder int sortOrder, @AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
            case AppDetailsFragment.SERVICES:
            case AppDetailsFragment.RECEIVERS:
            case AppDetailsFragment.PROVIDERS:
                mSortOrderComponents = sortOrder;
                Prefs.AppDetailsPage.setComponentsSortOrder(sortOrder);
                break;
            case AppDetailsFragment.APP_OPS:
                mSortOrderAppOps = sortOrder;
                Prefs.AppDetailsPage.setAppOpsSortOrder(sortOrder);
                break;
            case AppDetailsFragment.USES_PERMISSIONS:
                mSortOrderPermissions = sortOrder;
                Prefs.AppDetailsPage.setPermissionsSortOrder(sortOrder);
                break;
            case AppDetailsFragment.OVERLAYS:
                mSortOrderOverlays = sortOrder;
                Prefs.AppDetailsPage.setOverlaysSortOrder(sortOrder);
        }
        mExecutor.submit(() -> filterAndSortItemsInternal(property));
    }

    @AnyThread
    @SuppressLint("SwitchIntDef")
    @AppDetailsFragment.SortOrder
    public int getSortOrder(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
            case AppDetailsFragment.SERVICES:
            case AppDetailsFragment.RECEIVERS:
            case AppDetailsFragment.PROVIDERS:
                return mSortOrderComponents;
            case AppDetailsFragment.APP_OPS:
                return mSortOrderAppOps;
            case AppDetailsFragment.USES_PERMISSIONS:
                return mSortOrderPermissions;
            case AppDetailsFragment.OVERLAYS:
                return mSortOrderOverlays;
        }
        return AppDetailsFragment.SORT_BY_NAME;
    }

    @AnyThread
    public void setSearchQuery(String searchQuery, int searchType, @AppDetailsFragment.Property int property) {
        mSearchQuery = searchType == AdvancedSearchView.SEARCH_TYPE_REGEX ? searchQuery
                : searchQuery.toLowerCase(Locale.ROOT);
        mSearchType = searchType;
        mExecutor.submit(() -> filterAndSortItemsInternal(property));
    }

    @AnyThread
    public String getSearchQuery() {
        return mSearchQuery;
    }

    public void filterAndSortItems(@AppDetailsFragment.Property int property) {
        mExecutor.submit(() -> filterAndSortItemsInternal(property));
    }

    @SuppressLint({"SwitchIntDef", "NewApi"})
    @WorkerThread
    private void filterAndSortItemsInternal(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                synchronized (mActivityItems) {
                    mActivities.postValue(filterAndSortComponents(mActivityItems));
                }
                break;
            case AppDetailsFragment.PROVIDERS:
                synchronized (mProviderItems) {
                    mProviders.postValue(filterAndSortComponents(mProviderItems));
                }
                break;
            case AppDetailsFragment.RECEIVERS:
                synchronized (mReceiverItems) {
                    mReceivers.postValue(filterAndSortComponents(mReceiverItems));
                }
                break;
            case AppDetailsFragment.SERVICES:
                synchronized (mServiceItems) {
                    mServices.postValue(filterAndSortComponents(mServiceItems));
                }
                break;
            case AppDetailsFragment.APP_OPS: {
                List<AppDetailsAppOpItem> appDetailsItems;
                synchronized (mAppOpItems) {
                    if (!TextUtils.isEmpty(mSearchQuery)) {
                        appDetailsItems = AdvancedSearchView.matches(mSearchQuery, mAppOpItems,
                                (ChoiceGenerator<AppDetailsAppOpItem>) item ->
                                        lowercaseIfNotRegex(item.name, mSearchType),
                                mSearchType);
                    } else appDetailsItems = mAppOpItems;
                }
                Collections.sort(appDetailsItems, (o1, o2) -> {
                    switch (mSortOrderAppOps) {
                        case AppDetailsFragment.SORT_BY_NAME:
                            return o1.name.compareToIgnoreCase(o2.name);
                        case AppDetailsFragment.SORT_BY_APP_OP_VALUES:
                            Integer o1Op = o1.getOp();
                            Integer o2Op = o2.getOp();
                            return o1Op.compareTo(o2Op);
                        case AppDetailsFragment.SORT_BY_DENIED_APP_OPS:
                            // A slight hack to sort it this way: ignore > foreground > deny > default[ > ask] > allow
                            Integer o1Mode = o1.getMode();
                            Integer o2Mode = o2.getMode();
                            return -o1Mode.compareTo(o2Mode);
                    }
                    return 0;
                });
                mAppOps.postValue(appDetailsItems);
                break;
            }
            case AppDetailsFragment.USES_PERMISSIONS: {
                List<AppDetailsPermissionItem> appDetailsItems;
                synchronized (mUsesPermissionItems) {
                    if (!TextUtils.isEmpty(mSearchQuery)) {
                        appDetailsItems = AdvancedSearchView.matches(mSearchQuery, mUsesPermissionItems,
                                (ChoiceGenerator<AppDetailsPermissionItem>) item -> lowercaseIfNotRegex(item.name,
                                        mSearchType), mSearchType);
                    } else appDetailsItems = mUsesPermissionItems;
                }
                Collections.sort(appDetailsItems, (o1, o2) -> {
                    switch (mSortOrderPermissions) {
                        case AppDetailsFragment.SORT_BY_NAME:
                            return o1.name.compareToIgnoreCase(o2.name);
                        case AppDetailsFragment.SORT_BY_DANGEROUS_PERMS:
                            return -Boolean.compare(o1.isDangerous, o2.isDangerous);
                        case AppDetailsFragment.SORT_BY_DENIED_PERMS:
                            return Boolean.compare(o1.permission.isGranted(), o2.permission.isGranted());
                    }
                    return 0;
                });
                mUsesPermissions.postValue(new ArrayList<>(appDetailsItems));
                break;
            }
            case AppDetailsFragment.PERMISSIONS:
                synchronized (mPermissionItems) {
                    mPermissions.postValue(filterAndSortPermissions(mPermissionItems));
                }
                break;
            case AppDetailsFragment.OVERLAYS: {
                List<AppDetailsOverlayItem> appDetailsItems;
                synchronized (mOverlays) {
                    if (!TextUtils.isEmpty(mSearchQuery)) {
                        appDetailsItems = AdvancedSearchView.matches(mSearchQuery, mOverlays.getValue(),
                                (ChoiceGenerator<AppDetailsOverlayItem>) item -> lowercaseIfNotRegex(item.name,
                                        mSearchType), mSearchType);
                    } else appDetailsItems = mOverlays.getValue();
                }
                Collections.sort(appDetailsItems, (o1, o2) -> {
                    switch (mSortOrderOverlays) {
                        case AppDetailsFragment.SORT_BY_NAME:
                            return o1.name.compareToIgnoreCase(o2.name);
                        case AppDetailsFragment.SORT_BY_PRIORITY:
                            return Integer.compare(o1.getPriority(), o2.getPriority());
                    }
                    return 0;
                });
                mOverlays.postValue(new ArrayList<>(appDetailsItems));
                break;
            }
            case AppDetailsFragment.APP_INFO:
            case AppDetailsFragment.CONFIGURATIONS:
            case AppDetailsFragment.FEATURES:
            case AppDetailsFragment.SHARED_LIBRARIES:
            case AppDetailsFragment.SIGNATURES:
                // do nothing
                break;
        }
    }

    @WorkerThread
    @Nullable
    private List<AppDetailsItem<ComponentInfo>> filterAndSortComponents(
            @Nullable List<AppDetailsItem<ComponentInfo>> appDetailsItems) {
        if (appDetailsItems == null) return null;
        if (TextUtils.isEmpty(mSearchQuery)) {
            sortComponents(appDetailsItems);
            return appDetailsItems;
        }
        List<AppDetailsItem<ComponentInfo>> appDetailsItemsInt = AdvancedSearchView.matches(mSearchQuery, appDetailsItems,
                (ChoiceGenerator<AppDetailsItem<ComponentInfo>>) item -> lowercaseIfNotRegex(item.name, mSearchType), mSearchType);
        sortComponents(appDetailsItemsInt);
        return appDetailsItemsInt;
    }

    @WorkerThread
    @Nullable
    private List<AppDetailsItem<PermissionInfo>> filterAndSortPermissions(
            @Nullable List<AppDetailsItem<PermissionInfo>> appDetailsItems) {
        if (appDetailsItems == null) return null;
        if (TextUtils.isEmpty(mSearchQuery)) {
            Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
            return appDetailsItems;
        }
        List<AppDetailsItem<PermissionInfo>> appDetailsItemsInt = AdvancedSearchView.matches(mSearchQuery,
                appDetailsItems, (ChoiceGenerator<AppDetailsItem<PermissionInfo>>) item ->
                        lowercaseIfNotRegex(item.name, mSearchType), mSearchType);
        Collections.sort(appDetailsItemsInt, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return appDetailsItemsInt;
    }

    /**
     * Return lowercase string if regex isn't enabled (among the search types, only regex is case-sensitive).
     */
    private String lowercaseIfNotRegex(String s, @AdvancedSearchView.SearchType int filterType) {
        return filterType == AdvancedSearchView.SEARCH_TYPE_REGEX ? s : s.toLowerCase(Locale.ROOT);
    }

    public static final int RULE_APPLIED = 0;
    public static final int RULE_NOT_APPLIED = 1;
    public static final int RULE_NO_RULE = 2;

    @NonNull
    private final MutableLiveData<Integer> mRuleApplicationStatus = new MutableLiveData<>();

    @UiThread
    public LiveData<Integer> getRuleApplicationStatus() {
        if (mRuleApplicationStatus.getValue() == null) {
            mExecutor.submit(this::setRuleApplicationStatus);
        }
        return mRuleApplicationStatus;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void setRuleApplicationStatus() {
        if (mPackageName == null || mExternalApk) {
            mRuleApplicationStatus.postValue(RULE_NO_RULE);
            return;
        }
        synchronized (mBlockerLocker) {
            waitForBlockerOrExit();
            final AtomicInteger newRuleApplicationStatus = new AtomicInteger();
            newRuleApplicationStatus.set(mBlocker.isRulesApplied() ? RULE_APPLIED : RULE_NOT_APPLIED);
            if (mBlocker.componentCount() == 0) newRuleApplicationStatus.set(RULE_NO_RULE);
            mRuleApplicationStatus.postValue(newRuleApplicationStatus.get());
        }
    }

    @AnyThread
    @GuardedBy("blockerLocker")
    public void updateRulesForComponent(@NonNull AppDetailsComponentItem componentItem, @NonNull RuleType type,
                                        @ComponentRule.ComponentStatus String componentStatus) {
        if (mExternalApk) return;
        mExecutor.submit(() -> {
            Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::pauseWatcher);
            String componentName = componentItem.name;
            synchronized (mBlockerLocker) {
                waitForBlockerOrExit();
                mBlocker.setMutable();
                if (mBlocker.hasComponentName(componentName)) {
                    // Simply delete it
                    mBlocker.deleteComponent(componentName);
                }
                // Add to the list
                mBlocker.addComponent(componentName, type, componentStatus);
                // Apply rules if global blocking enable or already applied
                if (Prefs.Blocking.globalBlockingEnabled()
                        || (mRuleApplicationStatus.getValue() != null && RULE_APPLIED == mRuleApplicationStatus.getValue())) {
                    mBlocker.applyRules(true);
                }
                // Set new status
                setRuleApplicationStatus();
                // Commit changes
                mBlocker.commit();
                mBlocker.setReadOnly();
                Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::resumeWatcher);
            }
        });
    }

    @Nullable
    public ComponentRule getComponentRule(String componentName) {
        synchronized (mBlockerLocker) {
            if (mBlocker != null) {
                return mBlocker.getComponent(componentName);
            }
            return null;
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void addRules(List<? extends RuleEntry> entries, boolean forceApply) {
        if (mExternalApk) return;
        synchronized (mBlockerLocker) {
            waitForBlockerOrExit();
            mBlocker.setMutable();
            for (RuleEntry entry : entries) {
                String componentName = entry.name;
                if (mBlocker.hasComponentName(componentName)) {
                    // Remove from the list
                    mBlocker.removeComponent(componentName);
                }
                // Add to the list (again)
                mBlocker.addComponent(componentName, entry.type);
            }
            // Apply rules if global blocking enable or already applied
            if (forceApply || Prefs.Blocking.globalBlockingEnabled()
                    || (mRuleApplicationStatus.getValue() != null && RULE_APPLIED == mRuleApplicationStatus.getValue())) {
                mBlocker.applyRules(true);
            }
            // Set new status
            setRuleApplicationStatus();
            // Commit changes
            mBlocker.commit();
            mBlocker.setReadOnly();
            // Update UI
            reloadComponents();
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void removeRules(List<? extends RuleEntry> entries, boolean forceApply) {
        if (mExternalApk) return;
        synchronized (mBlockerLocker) {
            waitForBlockerOrExit();
            mBlocker.setMutable();
            for (RuleEntry entry : entries) {
                String componentName = entry.name;
                if (mBlocker.hasComponentName(componentName)) {
                    // Remove from the list
                    mBlocker.removeComponent(componentName);
                }
            }
            // Apply rules if global blocking enable or already applied
            if (forceApply || Prefs.Blocking.globalBlockingEnabled()
                    || (mRuleApplicationStatus.getValue() != null && RULE_APPLIED == mRuleApplicationStatus.getValue())) {
                mBlocker.applyRules(true);
            }
            // Set new status
            setRuleApplicationStatus();
            // Commit changes
            mBlocker.commit();
            mBlocker.setReadOnly();
            // Update UI
            reloadComponents();
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean togglePermission(final AppDetailsPermissionItem permissionItem) {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        try {
            if (!permissionItem.isGranted()) {
                Log.d(TAG, "Granting permission: %s", permissionItem.name);
                permissionItem.grantPermission(packageInfo, mAppOpsManager);
            } else {
                Log.d(TAG, "Revoking permission: %s", permissionItem.name);
                permissionItem.revokePermission(packageInfo, mAppOpsManager);
            }
        } catch (RemoteException | PermissionException e) {
            e.printStackTrace();
            return false;
        }
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                waitForBlockerOrExit();
                mBlocker.setMutable();
                mBlocker.setPermission(permissionItem.name, permissionItem.permission.isGranted(),
                        permissionItem.permission.getFlags());
                mBlocker.commit();
                mBlocker.setReadOnly();
                mBlockerLocker.notifyAll();
            }
        });
        setUsesPermission(permissionItem);
        return true;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean revokeDangerousPermissions() {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        List<AppDetailsPermissionItem> revokedPermissions = new ArrayList<>();
        boolean isSuccessful = true;
        synchronized (mUsesPermissionItems) {
            for (AppDetailsPermissionItem permissionItem : mUsesPermissionItems) {
                if (!permissionItem.isDangerous || !permissionItem.permission.isGranted()) continue;
                try {
                    permissionItem.revokePermission(packageInfo, mAppOpsManager);
                    revokedPermissions.add(permissionItem);
                } catch (RemoteException | PermissionException e) {
                    e.printStackTrace();
                    isSuccessful = false;
                }
            }
        }
        // Save values to the blocking rules
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                waitForBlockerOrExit();
                mBlocker.setMutable();
                for (AppDetailsPermissionItem permItem : revokedPermissions) {
                    mBlocker.setPermission(permItem.name, permItem.permission.isGranted(), permItem.permission.getFlags());
                }
                mBlocker.commit();
                mBlocker.setReadOnly();
                mBlockerLocker.notifyAll();
            }
        });
        return isSuccessful;
    }

    @NonNull
    private final AppOpsManagerCompat mAppOpsManager = new AppOpsManagerCompat();

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setAppOp(int op, int mode) {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        try {
            // Set mode
            PermUtils.setAppOpMode(mAppOpsManager, op, mPackageName, packageInfo.applicationInfo.uid, mode);
            mExecutor.submit(() -> {
                synchronized (mBlockerLocker) {
                    waitForBlockerOrExit();
                    mBlocker.setMutable();
                    mBlocker.setAppOp(op, mode);
                    mBlocker.commit();
                    mBlocker.setReadOnly();
                    mBlockerLocker.notifyAll();
                }
            });
        } catch (PermissionException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setAppOpMode(AppDetailsAppOpItem appOpItem) {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        try {
            if (appOpItem.isAllowed()) {
                appOpItem.disallowAppOp(packageInfo, mAppOpsManager);
            } else {
                appOpItem.allowAppOp(packageInfo, mAppOpsManager);
            }
            setAppOp(appOpItem);
            mExecutor.submit(() -> {
                synchronized (mBlockerLocker) {
                    waitForBlockerOrExit();
                    mBlocker.setMutable();
                    mBlocker.setAppOp(appOpItem.getOp(), appOpItem.getMode());
                    mBlocker.commit();
                    mBlocker.setReadOnly();
                    mBlockerLocker.notifyAll();
                }
            });
            return true;
        } catch (PermissionException e) {
            e.printStackTrace();
            return false;
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setAppOpMode(AppDetailsAppOpItem appOpItem, @AppOpsManagerCompat.Mode int mode) {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        try {
            appOpItem.setAppOp(packageInfo, mAppOpsManager, mode);
            setAppOp(appOpItem);
            mExecutor.submit(() -> {
                synchronized (mBlockerLocker) {
                    waitForBlockerOrExit();
                    mBlocker.setMutable();
                    mBlocker.setAppOp(appOpItem.getOp(), appOpItem.getMode());
                    mBlocker.commit();
                    mBlocker.setReadOnly();
                    mBlockerLocker.notifyAll();
                }
            });
            return true;
        } catch (PermissionException e) {
            e.printStackTrace();
            return false;
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean resetAppOps() {
        if (mExternalApk) return false;
        if (getPackageInfoInternal() == null || mPackageName == null) return false;
        try {
            mAppOpsManager.resetAllModes(mUserId, mPackageName);
            mExecutor.submit(this::loadAppOps);
            // Save values to the blocking rules
            mExecutor.submit(() -> {
                synchronized (mBlockerLocker) {
                    waitForBlockerOrExit();
                    List<AppOpRule> appOpEntries = mBlocker.getAll(AppOpRule.class);
                    mBlocker.setMutable();
                    for (AppOpRule entry : appOpEntries)
                        mBlocker.removeEntry(entry);
                    mBlocker.commit();
                    mBlocker.setReadOnly();
                    mBlockerLocker.notifyAll();
                }
            });
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean ignoreDangerousAppOps() {
        if (mExternalApk) return false;
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return false;
        String permName;
        final List<Integer> opItems = new ArrayList<>();
        boolean isSuccessful = true;
        synchronized (mAppOpItems) {
            for (AppDetailsAppOpItem mAppOpItem : mAppOpItems) {
                try {
                    permName = AppOpsManagerCompat.opToPermission(mAppOpItem.getOp());
                    if (permName != null) {
                        PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permName,
                                PackageManager.GET_META_DATA);
                        int basePermissionType = PermissionInfoCompat.getProtection(permissionInfo);
                        if (basePermissionType == PermissionInfo.PROTECTION_DANGEROUS) {
                            // Set mode
                            try {
                                PermUtils.setAppOpMode(mAppOpsManager, mAppOpItem.getOp(), mPackageName,
                                        packageInfo.applicationInfo.uid, AppOpsManager.MODE_IGNORED);
                                opItems.add(mAppOpItem.getOp());
                                mAppOpItem.invalidate(mAppOpsManager, packageInfo);
                            } catch (PermissionException e) {
                                e.printStackTrace();
                                isSuccessful = false;
                                break;
                            }
                        }
                    }
                } catch (PackageManager.NameNotFoundException | IllegalArgumentException |
                         IndexOutOfBoundsException ignore) {
                }
            }
        }
        // Save values to the blocking rules
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                waitForBlockerOrExit();
                mBlocker.setMutable();
                for (int op : opItems)
                    mBlocker.setAppOp(op, AppOpsManager.MODE_IGNORED);
                mBlocker.commit();
                mBlocker.setReadOnly();
                mBlockerLocker.notifyAll();
            }
        });
        return isSuccessful;
    }

    @AnyThread
    @GuardedBy("blockerLocker")
    public void applyRules() {
        if (mExternalApk) return;
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                waitForBlockerOrExit();
                boolean oldIsRulesApplied = mBlocker.isRulesApplied();
                mBlocker.setMutable();
                mBlocker.applyRules(!oldIsRulesApplied);
                mBlocker.commit();
                mBlocker.setReadOnly();
                reloadComponents();
                setRuleApplicationStatus();
                mBlockerLocker.notifyAll();
            }
        });
    }

    @UiThread
    public LiveData<List<AppDetailsItem<?>>> get(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                return observeInternal(mActivities);
            case AppDetailsFragment.SERVICES:
                return observeInternal(mServices);
            case AppDetailsFragment.RECEIVERS:
                return observeInternal(mReceivers);
            case AppDetailsFragment.PROVIDERS:
                return observeInternal(mProviders);
            case AppDetailsFragment.APP_OPS:
                return observeInternal(mAppOps);
            case AppDetailsFragment.USES_PERMISSIONS:
                return observeInternal(mUsesPermissions);
            case AppDetailsFragment.PERMISSIONS:
                return observeInternal(mPermissions);
            case AppDetailsFragment.FEATURES:
                return observeInternal(mFeatures);
            case AppDetailsFragment.CONFIGURATIONS:
                return observeInternal(mConfigurations);
            case AppDetailsFragment.SIGNATURES:
                return observeInternal(mSignatures);
            case AppDetailsFragment.SHARED_LIBRARIES:
                return observeInternal(mSharedLibraries);
            case AppDetailsFragment.OVERLAYS:
                return observeInternal(mOverlays);
            case AppDetailsFragment.APP_INFO:
                return observeInternal(mAppInfo);
            default:
                throw new IllegalArgumentException("Invalid property: " + property);
        }
    }

    @SuppressWarnings("unchecked")
    @AnyThread
    @NonNull
    private MutableLiveData<List<AppDetailsItem<?>>> observeInternal(@NonNull MutableLiveData<?> liveData) {
        return (MutableLiveData<List<AppDetailsItem<?>>>) liveData;
    }

    @AnyThread
    public void load(@AppDetailsFragment.Property int property) {
        mExecutor.submit(() -> {
            Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::pauseWatcher);
            switch (property) {
                case AppDetailsFragment.ACTIVITIES:
                    loadActivities();
                    break;
                case AppDetailsFragment.SERVICES:
                    loadServices();
                    break;
                case AppDetailsFragment.RECEIVERS:
                    loadReceivers();
                    break;
                case AppDetailsFragment.PROVIDERS:
                    loadProviders();
                    break;
                case AppDetailsFragment.APP_OPS:
                    loadAppOps();
                    break;
                case AppDetailsFragment.USES_PERMISSIONS:
                    loadUsesPermissions();
                    break;
                case AppDetailsFragment.PERMISSIONS:
                    loadPermissions();
                    break;
                case AppDetailsFragment.FEATURES:
                    loadFeatures();
                    break;
                case AppDetailsFragment.CONFIGURATIONS:
                    loadConfigurations();
                    break;
                case AppDetailsFragment.SIGNATURES:
                    loadSignatures();
                    break;
                case AppDetailsFragment.SHARED_LIBRARIES:
                    loadSharedLibraries();
                    break;
                case AppDetailsFragment.OVERLAYS:
                    loadOverlays();
                    break;
                case AppDetailsFragment.APP_INFO:
                    loadAppInfo();
                    break;
            }
            Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::resumeWatcher);
        });
    }


    private final MutableLiveData<Boolean> mIsPackageExistLiveData = new MutableLiveData<>();
    private boolean mIsPackageExist = true;

    @UiThread
    public LiveData<Boolean> getIsPackageExistLiveData() {
        if (mIsPackageExistLiveData.getValue() == null)
            mIsPackageExistLiveData.setValue(mIsPackageExist);
        return mIsPackageExistLiveData;
    }

    @AnyThread
    public boolean isPackageExist() {
        return mIsPackageExist;
    }

    @NonNull
    private final MutableLiveData<Boolean> mPackageChanged = new MutableLiveData<>();

    @UiThread
    public LiveData<Boolean> isPackageChanged() {
        if (mPackageChanged.getValue() == null) {
            mPackageChanged.setValue(false);
        }
        return mPackageChanged;
    }

    @AnyThread
    public void triggerPackageChange() {
        mExecutor.submit(this::setPackageChanged);
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void setPackageChanged() {
        // TODO: 16/3/23 Synchronization is needed somewhere
        setPackageInfo(true);
        if (mExternalApk || mExecutor.isShutdown() || mExecutor.isTerminated()) return;
        mExecutor.submit(() -> {
            synchronized (mBlockerLocker) {
                try {
                    waitForBlockerOrExit();
                    // Reload app components
                    mBlocker.reloadComponents();
                } finally {
                    mBlockerLocker.notifyAll();
                }
            }
        });
    }

    @AnyThread
    public boolean isExternalApk() {
        return mExternalApk;
    }

    @AnyThread
    public int getSplitCount() {
        if (mApkFile != null && mApkFile.isSplit()) {
            return mApkFile.getEntries().size() - 1;
        }
        return 0;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void waitForBlockerOrExit() {
        if (mExternalApk) return;
        if (mBlocker == null) {
            try {
                while (mWaitForBlocker) mBlockerLocker.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    @WorkerThread
    private void reloadComponents() {
        mExecutor.submit(() -> {
            Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::pauseWatcher);
            loadActivities();
            loadServices();
            loadReceivers();
            loadProviders();
            loadOverlays();
            Optional.ofNullable(mReceiver).ifPresent(PackageIntentReceiver::resumeWatcher);
        });
    }

    @SuppressLint("WrongConstant")
    @WorkerThread
    private void setPackageInfo(boolean reload) {
        // Package name cannot be null
        if (mPackageName == null) return;
        // Wait for component blocker to appear
        synchronized (mBlockerLocker) {
            waitForBlockerOrExit();
        }
        if (!reload && mPackageInfo != null) return;
        try {
            try {
                mInstalledPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName, PackageManager.GET_META_DATA
                                | PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES | MATCH_DISABLED_COMPONENTS
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | MATCH_UNINSTALLED_PACKAGES
                                | PackageManager.GET_SERVICES | PackageManager.GET_CONFIGURATIONS | GET_SIGNING_CERTIFICATES
                                | PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_URI_PERMISSION_PATTERNS
                                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                        mUserId);
                if (!ApplicationInfoCompat.isInstalled(mInstalledPackageInfo.applicationInfo)) {
                    throw new ApkFile.ApkFileException("App not installed. It only has data.");
                }
            } catch (Throwable e) {
                Log.e(TAG, e);
                mInstalledPackageInfo = null;
            }
            if (mExternalApk) {
                // Do not get signatures via Android framework as it will simply return NULL without any clarifications.
                // All signatures are fetched using PackageUtils where a fallback method is used in case the PackageInfo
                // didn't load any signature. So, we should be safe from any harm.
                mPackageInfo = mPackageManager.getPackageArchiveInfo(mApkPath, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | PackageManager.GET_CONFIGURATIONS
                        | PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | PackageManager.GET_META_DATA);
                if (mPackageInfo == null) {
                    throw new PackageManager.NameNotFoundException("Package cannot be parsed");
                }
                if (mInstalledPackageInfo == null) {
                    Log.d(TAG, "%s not installed for user %d", mPackageName, mUserId);
                }
                mPackageInfo.applicationInfo.sourceDir = mApkPath;
                mPackageInfo.applicationInfo.publicSourceDir = mApkPath;
            } else {
                mPackageInfo = mInstalledPackageInfo;
                if (mPackageInfo == null) {
                    throw new PackageManager.NameNotFoundException("Package not installed");
                }
            }
            mIsPackageExistLiveData.postValue(mIsPackageExist = true);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e);
            mIsPackageExistLiveData.postValue(mIsPackageExist = false);
        } catch (Throwable e) {
            Log.e(TAG, e);
        } finally {
            mPackageChanged.postValue(true);
        }
    }

    @WorkerThread
    @Nullable
    private PackageInfo getPackageInfoInternal() {
        try {
            mPackageInfoWatcher.await();
        } catch (InterruptedException e) {
            return null;
        }
        return mPackageInfo;
    }

    @AnyThread
    @Nullable
    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    @AnyThread
    @Nullable
    public PackageInfo getInstalledPackageInfo() {
        return mInstalledPackageInfo;
    }

    @NonNull
    public LiveData<UserInfo> getUserInfo() {
        MutableLiveData<UserInfo> userInfoMutableLiveData = new MutableLiveData<>();
        mExecutor.submit(() -> {
            if (mExternalApk) {
                return;
            }
            final List<UserInfo> userInfoList = Users.getUsers();
            if (userInfoList.size() > 1) {
                for (UserInfo userInfo : userInfoList) {
                    if (userInfo.id == mUserId) {
                        userInfoMutableLiveData.postValue(userInfo);
                        break;
                    }
                }
            }
        });
        return userInfoMutableLiveData;
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<PackageInfo>>> mAppInfo = new MutableLiveData<>();

    @WorkerThread
    private void loadAppInfo() {
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) {
            mAppInfo.postValue(null);
            return;
        }
        AppDetailsItem<PackageInfo> appDetailsItem = new AppDetailsItem<>(packageInfo);
        appDetailsItem.name = packageInfo.packageName;
        List<AppDetailsItem<PackageInfo>> appDetailsItems = Collections.singletonList(appDetailsItem);
        mAppInfo.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ComponentInfo>>> mActivities = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsItem<ComponentInfo>> mActivityItems = new ArrayList<>();

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadActivities() {
        synchronized (mActivityItems) {
            mActivityItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null || packageInfo.activities == null) {
                mActivities.postValue(mActivityItems);
                return;
            }
            CharSequence appLabel = packageInfo.applicationInfo.loadLabel(mPackageManager);
            boolean canStartAnyActivity = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY);
            boolean canStartViaAssist = UserHandleHidden.myUserId() == mUserId &&
                    SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
            for (ActivityInfo activityInfo : packageInfo.activities) {
                AppDetailsActivityItem componentItem = new AppDetailsActivityItem(activityInfo);
                componentItem.label = getComponentLabel(activityInfo, appLabel);
                synchronized (mBlockerLocker) {
                    if (!mExternalApk) {
                        componentItem.setRule(mBlocker.getComponent(activityInfo.name));
                    }
                }
                componentItem.setTracker(ComponentUtils.isTracker(activityInfo.name));
                componentItem.setDisabled(isComponentDisabled(activityInfo));
                // An activity is allowed to launch only if it's
                // 1) Not from an external APK
                // 2) Root enabled or the activity is exportable
                // 3) App or the activity is not disabled and/or blocked
                componentItem.canLaunch = !mExternalApk && (canStartAnyActivity || activityInfo.exported)
                        && !componentItem.isDisabled() && !componentItem.isBlocked();
                componentItem.canLaunchAssist = !mExternalApk && canStartViaAssist && !componentItem.isDisabled()
                        && !componentItem.isBlocked();
                mActivityItems.add(componentItem);
            }
            mActivities.postValue(filterAndSortComponents(mActivityItems));
        }
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ComponentInfo>>> mServices = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsItem<ComponentInfo>> mServiceItems = new ArrayList<>();

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadServices() {
        synchronized (mServiceItems) {
            mServiceItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null || packageInfo.services == null) {
                // There are no services
                mServices.postValue(Collections.emptyList());
                return;
            }
            List<ActivityManager.RunningServiceInfo> runningServiceInfoList;
            runningServiceInfoList = ActivityManagerCompat.getRunningServices(mPackageName, mUserId);
            CharSequence appLabel = packageInfo.applicationInfo.loadLabel(mPackageManager);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                AppDetailsServiceItem serviceItem = new AppDetailsServiceItem(serviceInfo);
                serviceItem.label = getComponentLabel(serviceInfo, appLabel);
                synchronized (mBlockerLocker) {
                    if (!mExternalApk) {
                        serviceItem.setRule(mBlocker.getComponent(serviceInfo.name));
                    }
                }
                serviceItem.setTracker(ComponentUtils.isTracker(serviceInfo.name));
                serviceItem.setDisabled(isComponentDisabled(serviceInfo));
                for (ActivityManager.RunningServiceInfo runningServiceInfo : runningServiceInfoList) {
                    if (runningServiceInfo.service.getClassName().equals(serviceInfo.name)) {
                        serviceItem.setRunningServiceInfo(runningServiceInfo);
                    }
                }
                // A service is allowed to launch only if it's
                // 1) Not from an external APK
                // 2) Root enabled or the service is exportable without any permission
                // 3) App or the service is not disabled and/or blocked
                serviceItem.canLaunch = !mExternalApk && canLaunchService(serviceInfo) && !serviceItem.isDisabled()
                        && !serviceItem.isBlocked();
                mServiceItems.add(serviceItem);
            }
            mServices.postValue(filterAndSortComponents(mServiceItems));
        }
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ComponentInfo>>> mReceivers = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsItem<ComponentInfo>> mReceiverItems = new ArrayList<>();

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadReceivers() {
        synchronized (mReceiverItems) {
            mReceiverItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null || packageInfo.receivers == null) {
                // There are no receivers
                mReceivers.postValue(Collections.emptyList());
                return;
            }
            CharSequence appLabel = packageInfo.applicationInfo.loadLabel(mPackageManager);
            for (ActivityInfo activityInfo : packageInfo.receivers) {
                AppDetailsComponentItem componentItem = new AppDetailsComponentItem(activityInfo);
                componentItem.label = getComponentLabel(activityInfo, appLabel);
                synchronized (mBlockerLocker) {
                    if (!mExternalApk) {
                        componentItem.setRule(mBlocker.getComponent(activityInfo.name));
                    }
                }
                componentItem.setTracker(ComponentUtils.isTracker(activityInfo.name));
                componentItem.setDisabled(isComponentDisabled(activityInfo));
                mReceiverItems.add(componentItem);
            }
            mReceivers.postValue(filterAndSortComponents(mReceiverItems));
        }
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ComponentInfo>>> mProviders = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsItem<ComponentInfo>> mProviderItems = new ArrayList<>();

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadProviders() {
        synchronized (mProviderItems) {
            mProviderItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null || packageInfo.providers == null) {
                // There are no providers
                mProviders.postValue(Collections.emptyList());
                return;
            }
            CharSequence appLabel = packageInfo.applicationInfo.loadLabel(mPackageManager);
            for (ProviderInfo providerInfo : packageInfo.providers) {
                AppDetailsComponentItem componentItem = new AppDetailsComponentItem(providerInfo);
                componentItem.label = getComponentLabel(providerInfo, appLabel);
                synchronized (mBlockerLocker) {
                    if (!mExternalApk) {
                        componentItem.setRule(mBlocker.getComponent(providerInfo.name));
                    }
                }
                componentItem.setTracker(ComponentUtils.isTracker(providerInfo.name));
                componentItem.setDisabled(isComponentDisabled(providerInfo));
                mProviderItems.add(componentItem);
            }
            mProviders.postValue(filterAndSortComponents(mProviderItems));
        }
    }

    @NonNull
    private CharSequence getComponentLabel(@NonNull ComponentInfo componentInfo, @NonNull CharSequence appLabel) {
        CharSequence componentLabel = componentInfo.loadLabel(mPackageManager);
        if (componentLabel.equals(componentInfo.name) || componentLabel.equals(appLabel)) {
            // Component label is as good as null
            componentLabel = null;
        }
        return componentLabel != null ? componentLabel : Utils.camelCaseToSpaceSeparatedString(
                Utils.getLastComponent(componentInfo.name));
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void sortComponents(List<AppDetailsItem<ComponentInfo>> appDetailsItems) {
        // First sort by name
        Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        if (mSortOrderComponents == AppDetailsFragment.SORT_BY_NAME) return;
        Collections.sort(appDetailsItems, (o1, o2) -> {
            switch (mSortOrderComponents) {
                // No need to sort by name since we've already done it
                case AppDetailsFragment.SORT_BY_BLOCKED:
                    return -Boolean.compare(
                            ((AppDetailsComponentItem) o1).isBlocked(),
                            ((AppDetailsComponentItem) o2).isBlocked());
                case AppDetailsFragment.SORT_BY_TRACKERS:
                    return -Boolean.compare(
                            ((AppDetailsComponentItem) o1).isTracker(),
                            ((AppDetailsComponentItem) o2).isTracker());
            }
            return 0;
        });
    }

    public boolean isComponentDisabled(@NonNull ComponentInfo componentInfo) {
        if (mInstalledPackageInfo == null || FreezeUtils.isFrozen(mInstalledPackageInfo.applicationInfo)) {
            return true;
        }
        ComponentName componentName = new ComponentName(componentInfo.packageName, componentInfo.name);
        try {
            int componentEnabledSetting = PackageManagerCompat.getComponentEnabledSetting(componentName, mUserId);
            switch (componentEnabledSetting) {
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                    return true;
                case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    return false;
                case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                default:
            }
        } catch (Throwable ignore) {
        }
        return !componentInfo.isEnabled();
    }

    private static boolean canLaunchService(@NonNull ServiceInfo info) {
        if (info.exported && info.permission == null) {
            return true;
        }
        int uid = Users.getSelfOrRemoteUid();
        if (uid == Ops.ROOT_UID || (uid == Ops.SYSTEM_UID && info.permission == null)) {
            return true;
        }
        if (info.permission == null) {
            return false;
        }
        return SelfPermissions.checkSelfOrRemotePermission(info.permission, uid);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsAppOpItem>> mAppOps = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsAppOpItem> mAppOpItems = new ArrayList<>();

    @WorkerThread
    public void setAppOp(AppDetailsAppOpItem appDetailsItem) {
        synchronized (mAppOpItems) {
            for (int i = 0; i < mAppOpItems.size(); ++i) {
                if (mAppOpItems.get(i).name.equals(appDetailsItem.name)) {
                    mAppOpItems.set(i, appDetailsItem);
                    break;
                }
            }
        }
    }

    @WorkerThread
    private void loadAppOps() {
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null || mExternalApk || !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GET_APP_OPS_STATS)) {
            mAppOps.postValue(Collections.emptyList());
            return;
        }
        boolean canGetGrantRevokeRuntimePermissions = SelfPermissions.checkGetGrantRevokeRuntimePermissions();
        synchronized (mAppOpItems) {
            mAppOpItems.clear();
            try {
                int uid = packageInfo.applicationInfo.uid;
                String packageName = packageInfo.packageName;
                HashMap<Integer, AppOpsManagerCompat.OpEntry> opToOpEntryMap = new HashMap<>(AppOpsManagerCompat._NUM_OP);
                for (AppOpsManagerCompat.OpEntry opEntry : AppOpsManagerCompat
                        .getConfiguredOpsForPackage(mAppOpsManager, packageName, uid)) {
                    if (opToOpEntryMap.get(opEntry.getOp()) == null) {
                        opToOpEntryMap.put(opEntry.getOp(), opEntry);
                    }
                }
                // Include from permissions
                List<String> permissions = getRawPermissions();
                HashSet<Integer> otherOps = new HashSet<>(AppOpsManagerCompat._NUM_OP);
                for (String permission : permissions) {
                    int op = AppOpsManagerCompat.permissionToOpCode(permission);
                    if (op == AppOpsManagerCompat.OP_NONE
                            || op >= AppOpsManagerCompat._NUM_OP
                            || opToOpEntryMap.get(op) != null) {
                        // Invalid/unsupported app operation
                        continue;
                    }
                    otherOps.add(op);
                }
                // Include defaults i.e. app ops without any associated permissions if requested
                if (Prefs.AppDetailsPage.displayDefaultAppOps()) {
                    for (int op : AppOpsManagerCompat.getOpsWithoutPermissions()) {
                        if (op == AppOpsManagerCompat.OP_NONE
                                || op >= AppOpsManagerCompat._NUM_OP
                                || opToOpEntryMap.get(op) != null) {
                            // Invalid/unsupported app operation
                            continue;
                        }
                        otherOps.add(op);
                    }
                }
                for (AppOpsManagerCompat.OpEntry entry : opToOpEntryMap.values()) {
                    AppDetailsAppOpItem appDetailsItem;
                    String permissionName = AppOpsManagerCompat.opToPermission(entry.getOp());
                    if (permissionName != null) {
                        boolean isGranted = PermissionCompat.checkPermission(permissionName, packageName, mUserId)
                                == PackageManager.PERMISSION_GRANTED;
                        int permissionFlags = canGetGrantRevokeRuntimePermissions
                                ? PermissionCompat.getPermissionFlags(permissionName, packageName, mUserId)
                                : PermissionCompat.FLAG_PERMISSION_NONE;
                        PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(permissionName, packageName, 0);
                        if (permissionInfo == null) {
                            permissionInfo = new PermissionInfo();
                            permissionInfo.name = permissionName;
                        }
                        appDetailsItem = new AppDetailsAppOpItem(entry, permissionInfo, isGranted, permissionFlags,
                                permissions.contains(permissionName));
                    } else {
                        appDetailsItem = new AppDetailsAppOpItem(entry);
                    }
                    mAppOpItems.add(appDetailsItem);
                }
                // Add other ops
                for (int op : otherOps) {
                    AppDetailsAppOpItem appDetailsItem;
                    String permissionName = AppOpsManagerCompat.opToPermission(op);
                    if (permissionName != null) {
                        boolean isGranted = PermissionCompat.checkPermission(permissionName, packageName, mUserId)
                                == PackageManager.PERMISSION_GRANTED;
                        int permissionFlags = canGetGrantRevokeRuntimePermissions
                                ? PermissionCompat.getPermissionFlags(permissionName, packageName, mUserId)
                                : PermissionCompat.FLAG_PERMISSION_NONE;
                        PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(permissionName, packageName, 0);
                        if (permissionInfo == null) {
                            permissionInfo = new PermissionInfo();
                            permissionInfo.name = permissionName;
                        }
                        appDetailsItem = new AppDetailsAppOpItem(op, permissionInfo, isGranted, permissionFlags,
                                permissions.contains(permissionName));
                    } else {
                        appDetailsItem = new AppDetailsAppOpItem(op);
                    }
                    mAppOpItems.add(appDetailsItem);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        filterAndSortItemsInternal(AppDetailsFragment.APP_OPS);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<PermissionInfo>>> mUsesPermissions = new MutableLiveData<>();
    private final List<AppDetailsPermissionItem> mUsesPermissionItems = new ArrayList<>();

    @WorkerThread
    public void setUsesPermission(AppDetailsPermissionItem appDetailsPermissionItem) {
        AppDetailsPermissionItem permissionItem;
        synchronized (mUsesPermissionItems) {
            for (int i = 0; i < mUsesPermissionItems.size(); ++i) {
                permissionItem = mUsesPermissionItems.get(i);
                if (permissionItem.name.equals(appDetailsPermissionItem.name)) {
                    mUsesPermissionItems.set(i, appDetailsPermissionItem);
                    break;
                }
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void loadUsesPermissions() {
        synchronized (mUsesPermissionItems) {
            mUsesPermissionItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null || packageInfo.requestedPermissions == null) {
                // No requested permissions
                mUsesPermissions.postValue(Collections.emptyList());
                return;
            }
            List<AppOpsManagerCompat.OpEntry> opEntries = ExUtils.requireNonNullElse(() -> AppOpsManagerCompat
                            .getConfiguredOpsForPackage(mAppOpsManager, packageInfo.packageName, packageInfo.applicationInfo.uid),
                    Collections.emptyList());
            for (int i = 0; i < packageInfo.requestedPermissions.length; ++i) {
                AppDetailsPermissionItem permissionItem = getPermissionItem(packageInfo.requestedPermissions[i],
                        (packageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0,
                        opEntries);
                if (permissionItem != null) {
                    mUsesPermissionItems.add(permissionItem);
                }
            }
        }
        filterAndSortItemsInternal(AppDetailsFragment.USES_PERMISSIONS);
    }

    @WorkerThread
    public List<String> getRawPermissions() {
        List<String> rawPermissions = new ArrayList<>();
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo != null && packageInfo.requestedPermissions != null) {
            rawPermissions.addAll(Arrays.asList(packageInfo.requestedPermissions));
        }
        return rawPermissions;
    }

    @Nullable
    private AppDetailsPermissionItem getPermissionItem(@NonNull String permissionName, boolean isGranted,
                                                       @NonNull List<AppOpsManagerCompat.OpEntry> opEntries) {
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null) return null;
        try {
            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(permissionName,
                    packageInfo.packageName, PackageManager.GET_META_DATA);
            if (permissionInfo == null) {
                Log.d(TAG, "Couldn't fetch info for permission %s", permissionName);
                permissionInfo = new PermissionInfo();
                permissionInfo.name = permissionName;
            }
            int flags = permissionInfo.flags;
            int appOp = AppOpsManagerCompat.permissionToOpCode(permissionName);
            int permissionFlags;
            boolean appOpAllowed = false;
            if (!mExternalApk && SelfPermissions.checkGetGrantRevokeRuntimePermissions()) {
                permissionFlags = PermissionCompat.getPermissionFlags(
                        permissionName, packageInfo.packageName, mUserId);
            } else permissionFlags = PermissionCompat.FLAG_PERMISSION_NONE;
            if (!mExternalApk && appOp != AppOpsManagerCompat.OP_NONE) {
                int mode = AppOpsManagerCompat.getModeFromOpEntriesOrDefault(appOp, opEntries);
                appOpAllowed = mode == AppOpsManager.MODE_ALLOWED;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOpAllowed |= mode == AppOpsManager.MODE_FOREGROUND;
                }
            }
            int protection = PermissionInfoCompat.getProtection(permissionInfo);
            int protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
            Permission permission;
            if (protection == PermissionInfo.PROTECTION_DANGEROUS && PermUtils.systemSupportsRuntimePermissions()) {
                permission = new RuntimePermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            } else if ((protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                permission = new DevelopmentPermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            } else {
                permission = new ReadOnlyPermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            }
            AppDetailsPermissionItem appDetailsItem = new AppDetailsPermissionItem(permissionInfo, permission, flags);
            appDetailsItem.name = permissionName;
            return appDetailsItem;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<PermissionInfo>>> mPermissions = new MutableLiveData<>();
    private final List<AppDetailsItem<PermissionInfo>> mPermissionItems = new ArrayList<>();

    @WorkerThread
    private void loadPermissions() {
        synchronized (mPermissionItems) {
            mPermissionItems.clear();
            PackageInfo packageInfo = getPackageInfoInternal();
            if (packageInfo == null) {
                // No custom permissions
                mPermissions.postValue(mPermissionItems);
                return;
            }
            Set<String> visitedPerms = new HashSet<>();
            if (packageInfo.permissions != null) {
                for (PermissionInfo permissionInfo : packageInfo.permissions) {
                    AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, false);
                    mPermissionItems.add(appDetailsItem);
                    visitedPerms.add(permissionInfo.name);
                }
            }
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    if (activityInfo.permission != null && !visitedPerms.contains(activityInfo.permission)) {
                        try {
                            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(activityInfo.permission,
                                    packageInfo.packageName, PackageManager.GET_META_DATA);
                            if (permissionInfo == null) {
                                Log.d(TAG, "Couldn't fetch info for permission %s", activityInfo.permission);
                                permissionInfo = new PermissionInfo();
                                permissionInfo.name = activityInfo.permission;
                            }
                            AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, true);
                            mPermissionItems.add(appDetailsItem);
                            visitedPerms.add(permissionInfo.name);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (packageInfo.services != null) {
                for (ServiceInfo serviceInfo : packageInfo.services) {
                    if (serviceInfo.permission != null && !visitedPerms.contains(serviceInfo.permission)) {
                        try {
                            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(serviceInfo.permission,
                                    packageInfo.packageName, PackageManager.GET_META_DATA);
                            if (permissionInfo == null) {
                                Log.d(TAG, "Couldn't fetch info for permission %s", serviceInfo.permission);
                                permissionInfo = new PermissionInfo();
                                permissionInfo.name = serviceInfo.permission;
                            }
                            AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, true);
                            mPermissionItems.add(appDetailsItem);
                            visitedPerms.add(permissionInfo.name);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (packageInfo.providers != null) {
                for (ProviderInfo providerInfo : packageInfo.providers) {
                    if (providerInfo.readPermission != null && !visitedPerms.contains(providerInfo.readPermission)) {
                        try {
                            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(providerInfo.readPermission,
                                    packageInfo.packageName, PackageManager.GET_META_DATA);
                            if (permissionInfo == null) {
                                Log.d(TAG, "Couldn't fetch info for permission %s", providerInfo.readPermission);
                                permissionInfo = new PermissionInfo();
                                permissionInfo.name = providerInfo.readPermission;
                            }
                            AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, true);
                            mPermissionItems.add(appDetailsItem);
                            visitedPerms.add(permissionInfo.name);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    if (providerInfo.writePermission != null && !visitedPerms.contains(providerInfo.writePermission)) {
                        try {
                            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(providerInfo.writePermission,
                                    packageInfo.packageName, PackageManager.GET_META_DATA);
                            if (permissionInfo == null) {
                                Log.d(TAG, "Couldn't fetch info for permission %s", providerInfo.writePermission);
                                permissionInfo = new PermissionInfo();
                                permissionInfo.name = providerInfo.writePermission;
                            }
                            AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, true);
                            mPermissionItems.add(appDetailsItem);
                            visitedPerms.add(permissionInfo.name);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (packageInfo.receivers != null) {
                for (ActivityInfo activityInfo : packageInfo.receivers) {
                    if (activityInfo.permission != null && !visitedPerms.contains(activityInfo.permission)) {
                        try {
                            PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(activityInfo.permission,
                                    packageInfo.packageName, PackageManager.GET_META_DATA);
                            if (permissionInfo == null) {
                                Log.d(TAG, "Couldn't fetch info for permission %s", activityInfo.permission);
                                permissionInfo = new PermissionInfo();
                                permissionInfo.name = activityInfo.permission;
                            }
                            AppDetailsDefinedPermissionItem appDetailsItem = new AppDetailsDefinedPermissionItem(permissionInfo, true);
                            mPermissionItems.add(appDetailsItem);
                            visitedPerms.add(permissionInfo.name);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            mPermissions.postValue(filterAndSortPermissions(mPermissionItems));
        }
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsFeatureItem>> mFeatures = new MutableLiveData<>();

    @WorkerThread
    private void loadFeatures() {
        List<AppDetailsFeatureItem> appDetailsItems = new ArrayList<>();
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null || packageInfo.reqFeatures == null) {
            // No required features
            mFeatures.postValue(appDetailsItems);
            return;
        }
        for (FeatureInfo fi : packageInfo.reqFeatures) {
            if (fi.name == null) fi.name = AppDetailsFeatureItem.OPEN_GL_ES;
        }
        for (FeatureInfo featureInfo : packageInfo.reqFeatures) {
            String name = featureInfo.name;
            boolean isAvailable;
            if (name == null) {
                // At most, only one name could be null
                name = AppDetailsFeatureItem.OPEN_GL_ES;
                ActivityManager activityManager = (ActivityManager) getApplication().getSystemService(Context.ACTIVITY_SERVICE);
                int glEsVersion = activityManager.getDeviceConfigurationInfo().reqGlEsVersion;
                isAvailable = featureInfo.reqGlEsVersion <= glEsVersion;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isAvailable = mPackageManager.hasSystemFeature(featureInfo.name, featureInfo.version);
            } else {
                isAvailable = mPackageManager.hasSystemFeature(featureInfo.name);
            }
            AppDetailsFeatureItem appDetailsItem = new AppDetailsFeatureItem(featureInfo, isAvailable);
            appDetailsItems.add(appDetailsItem);
            appDetailsItem.name = name;
        }
        Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        mFeatures.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ConfigurationInfo>>> mConfigurations = new MutableLiveData<>();

    @WorkerThread
    private void loadConfigurations() {
        List<AppDetailsItem<ConfigurationInfo>> appDetailsItems = new ArrayList<>();
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo != null && packageInfo.configPreferences != null) {
            for (ConfigurationInfo configurationInfo : packageInfo.configPreferences) {
                AppDetailsItem<ConfigurationInfo> appDetailsItem = new AppDetailsItem<>(configurationInfo);
                appDetailsItems.add(appDetailsItem);
            }
        }
        mConfigurations.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<X509Certificate>>> mSignatures = new MutableLiveData<>();
    private ApkVerifier.Result mApkVerifierResult;

    @AnyThread
    public ApkVerifier.Result getApkVerifierResult() {
        return mApkVerifierResult;
    }

    @WorkerThread
    private void loadSignatures() {
        List<AppDetailsItem<X509Certificate>> appDetailsItems = new ArrayList<>();
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null || mApkFile == null) {
            mSignatures.postValue(appDetailsItems);
            return;
        }
        try {
            File idsigFile = mApkFile.getIdsigFile();
            ApkVerifier.Builder builder = new ApkVerifier.Builder(mApkFile.getBaseEntry().getFile(false))
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
            if (idsigFile != null) {
                builder.setV4SignatureFile(idsigFile);
            }
            ApkVerifier apkVerifier = builder.build();
            mApkVerifierResult = apkVerifier.verify();
            SignerInfo signerInfo = new SignerInfo(mApkVerifierResult);
            // Get signer certificates
            X509Certificate[] certificates = signerInfo.getCurrentSignerCerts();
            if (certificates != null) {
                for (X509Certificate certificate : certificates) {
                    AppDetailsItem<X509Certificate> item = new AppDetailsItem<>(certificate);
                    item.name = "Signer Certificate";
                    appDetailsItems.add(item);
                }
            } else {
                //noinspection ConstantConditions Null is deliberately set here to get at least one row
                appDetailsItems.add(new AppDetailsItem<>(null));
            }
            // Get source stamp certificate
            if (mApkVerifierResult.isSourceStampVerified()) {
                X509Certificate certificate = signerInfo.getSourceStampCert();
                if (certificate != null) {
                    AppDetailsItem<X509Certificate> item = new AppDetailsItem<>(certificate);
                    item.name = "SourceStamp Certificate";
                    appDetailsItems.add(item);
                }
            }
            // Get source lineage certificates
            certificates = signerInfo.getSignerCertsInLineage();
            if (certificates != null) {
                for (X509Certificate certificate : certificates) {
                    AppDetailsItem<X509Certificate> item = new AppDetailsItem<>(certificate);
                    item.name = "Certificate for Lineage";
                    appDetailsItems.add(item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mSignatures.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsLibraryItem<?>>> mSharedLibraries = new MutableLiveData<>();

    @WorkerThread
    private void loadSharedLibraries() {
        List<AppDetailsLibraryItem<?>> appDetailsItems = new ArrayList<>();
        PackageInfo packageInfo = getPackageInfoInternal();
        if (packageInfo == null || mApkFile == null) {
            mSharedLibraries.postValue(appDetailsItems);
            return;
        }
        // Add shared libraries including the static shared libraries (which are basically APK files)
        ApplicationInfo info = packageInfo.applicationInfo;
        if (info.sharedLibraryFiles != null) {
            for (String sharedLibrary : info.sharedLibraryFiles) {
                File sharedLib = new File(sharedLibrary);
                AppDetailsLibraryItem<?> appDetailsItem = null;
                if (sharedLib.exists() && sharedLib.getName().endsWith(".apk")) {
                    // APK file
                    PackageInfo packageArchiveInfo = mPackageManager.getPackageArchiveInfo(sharedLibrary, 0);
                    if (packageArchiveInfo != null) {
                        appDetailsItem = new AppDetailsLibraryItem<>(packageArchiveInfo);
                        appDetailsItem.name = packageArchiveInfo.applicationInfo.loadLabel(mPackageManager).toString();
                        appDetailsItem.type = "APK";
                    }
                }
                if (appDetailsItem == null) {
                    appDetailsItem = new AppDetailsLibraryItem<>(sharedLib);
                    appDetailsItem.name = sharedLib.getName();
                    appDetailsItem.type = sharedLibrary.endsWith(".so") ? "SO" : "JAR";
                }
                appDetailsItem.path = sharedLib;
                appDetailsItem.size = sharedLib.length();
                appDetailsItems.add(appDetailsItem);
            }
        }
        // Add native libraries (shared objects)
        List<ApkFile.Entry> entries = mApkFile.getEntries();
        for (ApkFile.Entry entry : entries) {
            if (entry.type == ApkFile.APK_BASE
                    || entry.type == ApkFile.APK_SPLIT_FEATURE
                    || entry.type == ApkFile.APK_SPLIT_ABI
                    || entry.type == ApkFile.APK_SPLIT_UNKNOWN) {
                // Scan for .so files
                NativeLibraries nativeLibraries;
                try (InputStream is = entry.getInputStream(false)) {
                    try {
                        nativeLibraries = new NativeLibraries(is);
                    } catch (IOException e) {
                        // Maybe zip error, Try without InputStream
                        nativeLibraries = new NativeLibraries(entry.getFile(false));
                    }
                    for (NativeLibraries.NativeLib nativeLib : nativeLibraries.getLibs()) {
                        AppDetailsLibraryItem<?> appDetailsItem = new AppDetailsLibraryItem<>(nativeLib);
                        appDetailsItem.name = nativeLib.getName();
                        if (nativeLib instanceof NativeLibraries.ElfLib) {
                            switch (((NativeLibraries.ElfLib) nativeLib).getType()) {
                                case NativeLibraries.ElfLib.TYPE_DYN:
                                    appDetailsItem.type = "SHARED";
                                    break;
                                case NativeLibraries.ElfLib.TYPE_EXEC:
                                    appDetailsItem.type = "EXEC";
                                    break;
                                default:
                                    appDetailsItem.type = "SO";
                            }
                        } else appDetailsItem.type = "⚠️";
                        appDetailsItems.add(appDetailsItem);
                    }
                } catch (Throwable th) {
                    Log.e(TAG, th);
                }
            }
        }
        Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        mSharedLibraries.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsOverlayItem>> mOverlays = new MutableLiveData<>();

    @WorkerThread
    private void loadOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mPackageName == null || mExternalApk
                || !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)) {
            mOverlays.postValue(Collections.emptyList());
            return;
        }
        final List<OverlayInfo> overlays = ExUtils.requireNonNullElse(() -> OverlayManagerCompact
                        .getOverlayManager().getOverlayInfosForTarget(mPackageName, mUserId),
                Collections.emptyList());
        List<AppDetailsOverlayItem> overlayItems = new ArrayList<>(overlays.size());
        for (OverlayInfo overlay : overlays) {
            overlayItems.add(new AppDetailsOverlayItem(overlay));
        }
        mOverlays.postValue(overlayItems);
    }

    /**
     * Helper class to look for interesting changes to the installed apps
     * so that the loader can be updated.
     */
    public static class PackageIntentReceiver extends PackageChangeReceiver {
        final AppDetailsViewModel mModel;
        volatile boolean mPauseWatcher = false;
        int mChangeCount = 0;

        public PackageIntentReceiver(@NonNull AppDetailsViewModel model) {
            super(model.getApplication());
            mModel = model;
        }

        public void resumeWatcher() {
            if (mChangeCount > 0) {
                mChangeCount = 0;
                mModel.setPackageChanged();
            }
            mPauseWatcher = false;
        }

        public void pauseWatcher() {
            mChangeCount = 0;
            mPauseWatcher = true;
        }

        @Override
        @WorkerThread
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            boolean packageChanged = false;
            if (uid != null) {
                if (mModel.mPackageInfo != null && mModel.mPackageInfo.applicationInfo.uid == uid) {
                    Log.d(TAG, "Package is changed.");
                    packageChanged = true;
                }
            } else if (packages != null) {
                for (String packageName : packages) {
                    if (packageName.equals(mModel.mPackageName)) {
                        Log.d(TAG, "Package availability changed.");
                        packageChanged = true;
                        break;
                    }
                }
            }
            if (packageChanged) {
                if (mPauseWatcher) {
                    ++mChangeCount;
                } else mModel.setPackageChanged();
            }
        }
    }
}
