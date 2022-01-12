// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.Application;
import android.content.Intent;
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
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.RemoteException;
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
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.AppOpsUtils;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView.ChoiceGenerator;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.scanner.NativeLibraries;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

import static io.github.muntashirakon.AppManager.appops.AppOpsManager.OP_NONE;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class AppDetailsViewModel extends AndroidViewModel {
    public static final String TAG = AppDetailsViewModel.class.getSimpleName();

    private final PackageManager mPackageManager;
    private final Object mBlockerLocker = new Object();

    private PackageInfo mPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(4);
    private final CountDownLatch mPackageInfoWatcher = new CountDownLatch(1);

    private String mPackageName;
    @GuardedBy("blockerLocker")
    private ComponentsBlocker mBlocker;
    private PackageIntentReceiver mReceiver;
    private String mApkPath;
    private ApkFile mApkFile;
    private int mApkFileKey;
    private int mUserHandle;
    @AppDetailsFragment.SortOrder
    private int mSortOrderComponents = (int) AppPref.get(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT);
    @AppDetailsFragment.SortOrder
    private int mSortOrderAppOps = (int) AppPref.get(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT);
    @AppDetailsFragment.SortOrder
    private int mSortOrderPermissions = (int) AppPref.get(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT);
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType;
    private boolean mWaitForBlocker;
    private boolean mIsExternalApk = false;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        Log.d("ADVM", "New constructor called.");
        mPackageManager = application.getPackageManager();
        mReceiver = new PackageIntentReceiver(this);
        mWaitForBlocker = true;
    }

    @GuardedBy("blockerLocker")
    @Override
    public void onCleared() {
        Log.d("ADVM", "On Clear called for " + mPackageName);
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
        FileUtils.closeQuietly(mApkFile);
        mExecutor.shutdownNow();
    }

    @UiThread
    @NonNull
    public LiveData<PackageInfo> setPackage(@NonNull Uri packageUri, @Nullable String type) {
        MutableLiveData<PackageInfo> packageInfoLiveData = new MutableLiveData<>();
        mExecutor.submit(() -> {
            try {
                Log.d("ADVM", "Package Uri is being set");
                mIsExternalApk = true;
                mApkFileKey = ApkFile.createInstance(packageUri, type);
                mApkFile = ApkFile.getInstance(mApkFileKey);
                setPackageName(mApkFile.getPackageName());
                File cachedApkFile = mApkFile.getBaseEntry().getRealCachedFile();
                if (!cachedApkFile.canRead()) throw new Exception("Cannot read " + cachedApkFile);
                mApkPath = cachedApkFile.getAbsolutePath();
                setPackageInfo(false);
                packageInfoLiveData.postValue(getPackageInfo());
            } catch (Throwable th) {
                Log.e("ADVM", "Could not fetch package info.", th);
                packageInfoLiveData.postValue(null);
            } finally {
                mPackageInfoWatcher.countDown();
            }
        });
        return packageInfoLiveData;
    }

    @UiThread
    @NonNull
    public LiveData<PackageInfo> setPackage(@NonNull String packageName) {
        MutableLiveData<PackageInfo> packageInfoLiveData = new MutableLiveData<>();
        mExecutor.submit(() -> {
            try {
                Log.d("ADVM", "Package name is being set");
                mIsExternalApk = false;
                setPackageName(packageName);
                // TODO: 23/5/21 The app could be “data only”
                setPackageInfo(false);
                PackageInfo pi = getPackageInfo();
                if (pi == null) throw new ApkFile.ApkFileException("Package not installed.");
                mApkFileKey = ApkFile.createInstance(pi.applicationInfo);
                mApkFile = ApkFile.getInstance(mApkFileKey);
                packageInfoLiveData.postValue(pi);
            } catch (Throwable th) {
                Log.e("ADVM", "Could not fetch package info.", th);
                packageInfoLiveData.postValue(null);
            } finally {
                mPackageInfoWatcher.countDown();
            }
        });
        return packageInfoLiveData;
    }

    @AnyThread
    public void setUserHandle(@UserIdInt int userHandle) {
        this.mUserHandle = userHandle;
    }

    @AnyThread
    public int getUserHandle() {
        return mUserHandle;
    }

    @AnyThread
    @GuardedBy("blockerLocker")
    private void setPackageName(String packageName) {
        if (this.mPackageName != null) return;
        Log.d("ADVM", "Package name is being set for " + packageName);
        this.mPackageName = packageName;
        if (mIsExternalApk) return;
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
                    mBlocker = ComponentsBlocker.getInstance(packageName, mUserHandle);
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

    @AnyThread
    public int getApkFileKey() {
        return mApkFileKey;
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
                AppPref.set(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT, sortOrder);
                break;
            case AppDetailsFragment.APP_OPS:
                mSortOrderAppOps = sortOrder;
                AppPref.set(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT, sortOrder);
                break;
            case AppDetailsFragment.USES_PERMISSIONS:
                mSortOrderPermissions = sortOrder;
                AppPref.set(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT, sortOrder);
                break;
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
        }
        return AppDetailsFragment.SORT_BY_NAME;
    }

    @AnyThread
    public void setSearchQuery(String searchQuery, int searchType, @AppDetailsFragment.Property int property) {
        this.mSearchQuery = searchType == AdvancedSearchView.SEARCH_TYPE_REGEX ? searchQuery
                : searchQuery.toLowerCase(Locale.ROOT);
        this.mSearchType = searchType;
        mExecutor.submit(() -> filterAndSortItemsInternal(property));
    }

    @AnyThread
    public String getSearchQuery() {
        return mSearchQuery;
    }

    public void filterAndSortItems(@AppDetailsFragment.Property int property) {
        mExecutor.submit(() -> filterAndSortItemsInternal(property));
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void filterAndSortItemsInternal(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                mActivities.postValue(filterAndSortComponents(mActivityItems));
                break;
            case AppDetailsFragment.PROVIDERS:
                mProviders.postValue(filterAndSortComponents(mProviderItems));
                break;
            case AppDetailsFragment.RECEIVERS:
                mReceivers.postValue(filterAndSortComponents(mReceiverItems));
                break;
            case AppDetailsFragment.SERVICES:
                mServices.postValue(filterAndSortComponents(mServiceItems));
                break;
            case AppDetailsFragment.APP_OPS: {
                List<AppDetailsItem<OpEntry>> appDetailsItems;
                synchronized (mAppOpItems) {
                    if (!TextUtils.isEmpty(mSearchQuery)) {
                        appDetailsItems = AdvancedSearchView.matches(mSearchQuery, mAppOpItems,
                                (ChoiceGenerator<AppDetailsItem<OpEntry>>) item ->
                                        lowercaseIfNotRegex(item.name, mSearchType),
                                mSearchType);
                    } else appDetailsItems = mAppOpItems;
                }
                Collections.sort(appDetailsItems, (o1, o2) -> {
                    switch (mSortOrderAppOps) {
                        case AppDetailsFragment.SORT_BY_NAME:
                            return o1.name.compareToIgnoreCase(o2.name);
                        case AppDetailsFragment.SORT_BY_APP_OP_VALUES:
                            Integer o1Op = o1.vanillaItem.getOp();
                            Integer o2Op = o2.vanillaItem.getOp();
                            return o1Op.compareTo(o2Op);
                        case AppDetailsFragment.SORT_BY_DENIED_APP_OPS:
                            // A slight hack to sort it this way: ignore > foreground > deny > default[ > ask] > allow
                            Integer o1Mode = o1.vanillaItem.getMode();
                            Integer o2Mode = o2.vanillaItem.getMode();
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
                mPermissions.postValue(filterAndSortPermissions(mPermissionItems));
                break;
            case AppDetailsFragment.APP_INFO:
            case AppDetailsFragment.CONFIGURATIONS:
            case AppDetailsFragment.FEATURES:
            case AppDetailsFragment.NONE:
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
        if (mPackageName == null || mIsExternalApk) {
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

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void updateRulesForComponent(String componentName, RuleType type,
                                        @ComponentRule.ComponentStatus String componentStatus) {
        if (mIsExternalApk) return;
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
            if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
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
    public void addRules(List<? extends RuleEntry> entries, boolean forceApply) {
        if (mIsExternalApk) return;
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
            if (forceApply || (Boolean) AppPref.get(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
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
        if (mIsExternalApk) return;
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
            if (forceApply || (Boolean) AppPref.get(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
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
        if (mIsExternalApk) return false;
        boolean isSuccessful;
        try {
            if (!permissionItem.permission.isGranted()) {
                Log.d(TAG, "Granting permission: " + permissionItem.name);
                isSuccessful = permissionItem.grantPermission(mPackageInfo, true, true);
            } else {
                Log.d(TAG, "Revoking permission: " + permissionItem.name);
                isSuccessful = permissionItem.revokePermission(mPackageInfo, true);
            }
        } catch (RemoteException e) {
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
        return isSuccessful;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean revokeDangerousPermissions() {
        if (mIsExternalApk) return false;
        List<AppDetailsPermissionItem> revokedPermissions = new ArrayList<>();
        boolean isSuccessful = true;
        synchronized (mUsesPermissionItems) {
            for (AppDetailsPermissionItem permissionItem : mUsesPermissionItems) {
                if (!permissionItem.isDangerous || !permissionItem.permission.isGranted()) continue;
                try {
                    if (permissionItem.revokePermission(mPackageInfo, true)) {
                        revokedPermissions.add(permissionItem);
                    }
                } catch (RemoteException e) {
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
    private final AppOpsService mAppOpsService = new AppOpsService();

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setAppOp(int op, int mode) {
        if (mIsExternalApk) return false;
        try {
            // Set mode
            mAppOpsService.setMode(op, mPackageInfo.applicationInfo.uid, mPackageName, mode);
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
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean resetAppOps() {
        if (mIsExternalApk) return false;
        try {
            mAppOpsService.resetAllModes(mUserHandle, mPackageName);
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
        if (mIsExternalApk) return false;
        AppDetailsItem<OpEntry> appDetailsItem;
        OpEntry opEntry;
        String permName;
        final List<Integer> opItems = new ArrayList<>();
        boolean isSuccessful = true;
        synchronized (mAppOpItems) {
            for (int i = 0; i < mAppOpItems.size(); ++i) {
                appDetailsItem = mAppOpItems.get(i);
                opEntry = appDetailsItem.vanillaItem;
                try {
                    permName = AppOpsManager.opToPermission(opEntry.getOp());
                    if (permName != null) {
                        PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permName,
                                PackageManager.GET_META_DATA);
                        int basePermissionType = PermissionInfoCompat.getProtection(permissionInfo);
                        if (basePermissionType == PermissionInfo.PROTECTION_DANGEROUS) {
                            // Set mode
                            try {
                                mAppOpsService.setMode(opEntry.getOp(), mPackageInfo.applicationInfo.uid,
                                        mPackageName, AppOpsManager.MODE_IGNORED);
                                opItems.add(opEntry.getOp());
                                appDetailsItem.vanillaItem = new OpEntry(opEntry.getOp(),
                                        AppOpsManager.MODE_IGNORED, opEntry.getTime(),
                                        opEntry.getRejectTime(), opEntry.getDuration(),
                                        opEntry.getProxyUid(), opEntry.getProxyPackageName());
                                mAppOpItems.set(i, appDetailsItem);
                            } catch (Exception e) {
                                e.printStackTrace();
                                isSuccessful = false;
                                break;
                            }
                        }
                    }
                } catch (PackageManager.NameNotFoundException | IllegalArgumentException | IndexOutOfBoundsException ignore) {
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

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void applyRules() {
        if (mIsExternalApk) return;
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
    }

    @UiThread
    public LiveData<List<AppDetailsItem<?>>> get(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                return getInternal(mActivities, this::loadActivities);
            case AppDetailsFragment.SERVICES:
                return getInternal(mServices, this::loadServices);
            case AppDetailsFragment.RECEIVERS:
                return getInternal(mReceivers, this::loadReceivers);
            case AppDetailsFragment.PROVIDERS:
                return getInternal(mProviders, this::loadProviders);
            case AppDetailsFragment.APP_OPS:
                return getInternal(mAppOps, this::loadAppOps);
            case AppDetailsFragment.USES_PERMISSIONS:
                return getInternal(mUsesPermissions, this::loadUsesPermissions);
            case AppDetailsFragment.PERMISSIONS:
                return getInternal(mPermissions, this::loadPermissions);
            case AppDetailsFragment.FEATURES:
                return getInternal(mFeatures, this::loadFeatures);
            case AppDetailsFragment.CONFIGURATIONS:
                return getInternal(mConfigurations, this::loadConfigurations);
            case AppDetailsFragment.SIGNATURES:
                return getInternal(mSignatures, this::loadSignatures);
            case AppDetailsFragment.SHARED_LIBRARIES:
                return getInternal(mSharedLibraries, this::loadSharedLibraries);
            case AppDetailsFragment.APP_INFO:
                return getInternal(appInfo, this::loadAppInfo);
            case AppDetailsFragment.NONE:
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @AnyThread
    @NonNull
    private MutableLiveData<List<AppDetailsItem<?>>> getInternal(@NonNull MutableLiveData<?> liveData, Runnable loader) {
        if (liveData.getValue() == null) {
            mExecutor.submit(loader);
        }
        return (MutableLiveData<List<AppDetailsItem<?>>>) liveData;
    }

    @AnyThread
    public void load(@AppDetailsFragment.Property int property) {
        mExecutor.submit(() -> {
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
                case AppDetailsFragment.APP_INFO:
                    loadAppInfo();
                case AppDetailsFragment.NONE:
                    break;
            }
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
    private final MutableLiveData<Boolean> mIsPackageChanged = new MutableLiveData<>();

    @UiThread
    public LiveData<Boolean> getIsPackageChanged() {
        if (mIsPackageChanged.getValue() == null) {
            mIsPackageChanged.setValue(false);
        }
        return mIsPackageChanged;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void setIsPackageChanged() {
        setPackageInfo(true);
        if (mIsExternalApk || mExecutor.isShutdown() || mExecutor.isTerminated()) return;
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
    public boolean getIsExternalApk() {
        return mIsExternalApk;
    }

    @AnyThread
    public int getSplitCount() {
        if (mApkFile.isSplit()) return mApkFile.getEntries().size() - 1;
        return 0;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void waitForBlockerOrExit() {
        if (mIsExternalApk) return;
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
        mExecutor.submit(this::loadActivities);
        mExecutor.submit(this::loadServices);
        mExecutor.submit(this::loadReceivers);
        mExecutor.submit(this::loadProviders);
    }

    @SuppressLint("WrongConstant")
    @WorkerThread
    public void setPackageInfo(boolean reload) {
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
                                | PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES | flagDisabledComponents
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | flagMatchUninstalled
                                | PackageManager.GET_SERVICES | PackageManager.GET_CONFIGURATIONS | flagSigningInfo
                                | PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_URI_PERMISSION_PATTERNS,
                        mUserHandle);
                if (!new File(mInstalledPackageInfo.applicationInfo.publicSourceDir).exists()) {
                    throw new ApkFile.ApkFileException("App not installed. It only has data.");
                }
            } catch (Throwable e) {
                Log.e(TAG, e);
                mInstalledPackageInfo = null;
            }
            if (mIsExternalApk) {
                // Do not get signatures via Android framework as it will simply return NULL without any clarifications.
                // All signatures are fetched using PackageUtils where a fallback method is used in case the PackageInfo
                // didn't load any signature. So, we should be safe from any harm.
                mPackageInfo = mPackageManager.getPackageArchiveInfo(mApkPath, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | flagDisabledComponents | PackageManager.GET_CONFIGURATIONS
                        | PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | PackageManager.GET_META_DATA);
                if (mPackageInfo == null) {
                    throw new PackageManager.NameNotFoundException("Package cannot be parsed");
                }
                if (mInstalledPackageInfo == null) {
                    Log.d("ADVM", mPackageName + " not installed for user " + mUserHandle);
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
            mIsPackageChanged.postValue(true);
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
            final List<UserInfo> userInfoList;
            if (!mIsExternalApk && AppPref.isRootOrAdbEnabled()) {
                userInfoList = Users.getUsers();
            } else userInfoList = null;
            if (userInfoList != null && userInfoList.size() > 1) {
                for (UserInfo userInfo : userInfoList) {
                    if (userInfo.id == mUserHandle) {
                        userInfoMutableLiveData.postValue(userInfo);
                        break;
                    }
                }
            }
        });
        return userInfoMutableLiveData;
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<PackageInfo>>> appInfo = new MutableLiveData<>();

    @WorkerThread
    private void loadAppInfo() {
        getPackageInfoInternal();
        if (mPackageInfo == null) {
            appInfo.postValue(null);
            return;
        }
        AppDetailsItem<PackageInfo> appDetailsItem = new AppDetailsItem<>(mPackageInfo);
        appDetailsItem.name = mPackageName;
        List<AppDetailsItem<PackageInfo>> appDetailsItems = Collections.singletonList(appDetailsItem);
        appInfo.postValue(appDetailsItems);
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
        }
        if (getPackageInfoInternal() == null || mPackageInfo.activities == null) {
            mActivities.postValue(mActivityItems);
            return;
        }
        for (ActivityInfo activityInfo : mPackageInfo.activities) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
            appDetailsItem.name = activityInfo.targetActivity == null ? activityInfo.name : activityInfo.targetActivity;
            synchronized (mBlockerLocker) {
                if (!mIsExternalApk) {
                    appDetailsItem.setRule(mBlocker.getComponent(activityInfo.name));
                }
            }
            appDetailsItem.setTracker(ComponentUtils.isTracker(activityInfo.name));
            synchronized (mActivityItems) {
                mActivityItems.add(appDetailsItem);
            }
        }
        synchronized (mActivityItems) {
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
        }
        if (getPackageInfoInternal() == null || mPackageInfo.services == null) {
            // There are no services
            mServices.postValue(Collections.emptyList());
            return;
        }
        for (ServiceInfo serviceInfo : mPackageInfo.services) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(serviceInfo);
            appDetailsItem.name = serviceInfo.name;
            synchronized (mBlockerLocker) {
                if (!mIsExternalApk) {
                    appDetailsItem.setRule(mBlocker.getComponent(serviceInfo.name));
                }
            }
            appDetailsItem.setTracker(ComponentUtils.isTracker(serviceInfo.name));
            synchronized (mServiceItems) {
                mServiceItems.add(appDetailsItem);
            }
        }
        synchronized (mServiceItems) {
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
        }
        if (getPackageInfoInternal() == null || mPackageInfo.receivers == null) {
            // There are no receivers
            mReceivers.postValue(Collections.emptyList());
            return;
        }
        for (ActivityInfo activityInfo : mPackageInfo.receivers) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
            appDetailsItem.name = activityInfo.name;
            synchronized (mBlockerLocker) {
                if (!mIsExternalApk) {
                    appDetailsItem.setRule(mBlocker.getComponent(activityInfo.name));
                }
            }
            appDetailsItem.setTracker(ComponentUtils.isTracker(activityInfo.name));
            synchronized (mReceiverItems) {
                mReceiverItems.add(appDetailsItem);
            }
        }
        synchronized (mReceiverItems) {
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
        }
        if (getPackageInfoInternal() == null || mPackageInfo.providers == null) {
            // There are no providers
            mProviders.postValue(Collections.emptyList());
            return;
        }
        for (ProviderInfo providerInfo : mPackageInfo.providers) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(providerInfo);
            appDetailsItem.name = providerInfo.name;
            synchronized (mBlockerLocker) {
                if (!mIsExternalApk) {
                    appDetailsItem.setRule(mBlocker.getComponent(providerInfo.name));
                }
            }
            appDetailsItem.setTracker(ComponentUtils.isTracker(providerInfo.name));
            synchronized (mProviderItems) {
                mProviderItems.add(appDetailsItem);
            }
        }
        synchronized (mProviderItems) {
            mProviders.postValue(filterAndSortComponents(mProviderItems));
        }
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

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<OpEntry>>> mAppOps = new MutableLiveData<>();
    @NonNull
    private final List<AppDetailsItem<OpEntry>> mAppOpItems = new ArrayList<>();

    @WorkerThread
    public void setAppOp(AppDetailsItem<OpEntry> appDetailsItem) {
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
        if (mPackageName == null || mIsExternalApk || !(AppPref.isRootOrAdbEnabled()
                || PermissionUtils.hasAppOpsPermission(getApplication()))) {
            mAppOps.postValue(Collections.emptyList());
            return;
        }
        synchronized (mAppOpItems) {
            mAppOpItems.clear();
        }
        try {
            int uid = mPackageInfo.applicationInfo.uid;
            List<OpEntry> opEntries = new ArrayList<>(AppOpsUtils.getChangedAppOps(mAppOpsService, mPackageName, uid));
            OpEntry opEntry;
            // Include from permissions
            List<String> permissions = getRawPermissions();
            for (String permission : permissions) {
                int op = AppOpsManager.permissionToOpCode(permission);
                if (op == OP_NONE || op >= AppOpsManager._NUM_OP) {
                    // Invalid/unsupported app operation
                    continue;
                }
                opEntry = new OpEntry(op, mAppOpsService.checkOperation(op, uid, mPackageName), 0,
                        0, 0, 0, null);
                if (!opEntries.contains(opEntry)) opEntries.add(opEntry);
            }
            // Include defaults ie. app ops without any associated permissions if requested
            if ((boolean) AppPref.get(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL)) {
                for (int op : AppOpsManager.sOpsWithNoPerm) {
                    if (op >= AppOpsManager._NUM_OP) {
                        // Unsupported app operation
                        continue;
                    }
                    opEntry = new OpEntry(op, AppOpsManager.opToDefaultMode(op), 0,
                            0, 0, 0, null);
                    if (!opEntries.contains(opEntry)) opEntries.add(opEntry);
                }
            }
            // TODO(24/12/20): App op with MODE_DEFAULT are determined by their associated permissions.
            //  Therefore, mode for such app ops should be determined from the permission.
            Set<String> uniqueSet = new HashSet<>();
            for (OpEntry entry : opEntries) {
                String opName = AppOpsManager.opToName(entry.getOp());
                if (uniqueSet.contains(opName)) continue;
                AppDetailsItem<OpEntry> appDetailsItem = new AppDetailsItem<>(entry);
                appDetailsItem.name = opName;
                uniqueSet.add(opName);
                synchronized (mAppOpItems) {
                    mAppOpItems.add(appDetailsItem);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        }
        if (getPackageInfoInternal() == null || mPackageInfo.requestedPermissions == null) {
            // No requested permissions
            mUsesPermissions.postValue(Collections.emptyList());
            return;
        }
        boolean isRootOrAdbEnabled = AppPref.isRootOrAdbEnabled();
        for (int i = 0; i < mPackageInfo.requestedPermissions.length; ++i) {
            try {
                String permissionName = mPackageInfo.requestedPermissions[i];
                PermissionInfo permissionInfo = PermissionCompat.getPermissionInfo(mPackageInfo.requestedPermissions[i],
                        mPackageInfo.packageName, PackageManager.GET_META_DATA);
                if (permissionInfo == null) {
                    Log.d(TAG, "Couldn't fetch info for permission " + permissionName);
                    permissionInfo = new PermissionInfo();
                    permissionInfo.name = permissionName;
                }
                boolean isGranted = (mPackageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                int appOp = AppOpsManager.permissionToOpCode(permissionName);
                int permissionFlags = 0;
                if (isRootOrAdbEnabled) {
                    try {
                        permissionFlags = PermissionCompat.getPermissionFlags(permissionName, mPackageName,
                                mUserHandle);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                boolean appOpAllowed;
                try {
                    appOpAllowed = !mIsExternalApk && appOp != OP_NONE && mAppOpsService.checkOperation(appOp,
                            mPackageInfo.applicationInfo.uid, mPackageName) == AppOpsManager.MODE_ALLOWED;
                } catch (RemoteException e) {
                    appOpAllowed = false;
                }
                int flags = mPackageInfo.requestedPermissionsFlags[i];
                Permission permission = new Permission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
                AppDetailsPermissionItem appDetailsItem = new AppDetailsPermissionItem(permissionInfo, permission, flags);
                appDetailsItem.name = permissionName;
                synchronized (mUsesPermissionItems) {
                    mUsesPermissionItems.add(appDetailsItem);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        filterAndSortItemsInternal(AppDetailsFragment.USES_PERMISSIONS);
    }

    @WorkerThread
    public List<String> getRawPermissions() {
        List<String> rawPermissions = new ArrayList<>();
        if (getPackageInfoInternal() != null && mPackageInfo.requestedPermissions != null) {
            rawPermissions.addAll(Arrays.asList(mPackageInfo.requestedPermissions));
        }
        return rawPermissions;
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<PermissionInfo>>> mPermissions = new MutableLiveData<>();
    private final List<AppDetailsItem<PermissionInfo>> mPermissionItems = new ArrayList<>();

    @WorkerThread
    private void loadPermissions() {
        mPermissionItems.clear();
        if (getPackageInfoInternal() == null || mPackageInfo.permissions == null) {
            // No custom permissions
            mPermissions.postValue(mPermissionItems);
            return;
        }
        for (PermissionInfo permissionInfo : mPackageInfo.permissions) {
            AppDetailsItem<PermissionInfo> appDetailsItem = new AppDetailsItem<>(permissionInfo);
            appDetailsItem.name = permissionInfo.name;
            mPermissionItems.add(appDetailsItem);
        }
        mPermissions.postValue(filterAndSortPermissions(mPermissionItems));
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<FeatureInfo>>> mFeatures = new MutableLiveData<>();

    public static final String OPEN_GL_ES = "OpenGL ES";

    @WorkerThread
    private void loadFeatures() {
        List<AppDetailsItem<FeatureInfo>> appDetailsItems = new ArrayList<>();
        if (getPackageInfoInternal() == null || mPackageInfo.reqFeatures == null) {
            // No required features
            mFeatures.postValue(appDetailsItems);
            return;
        }
        for (FeatureInfo fi : mPackageInfo.reqFeatures) {
            if (fi.name == null) fi.name = OPEN_GL_ES;
        }
        Arrays.sort(mPackageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        for (FeatureInfo featureInfo : mPackageInfo.reqFeatures) {
            AppDetailsItem<FeatureInfo> appDetailsItem = new AppDetailsItem<>(featureInfo);
            appDetailsItem.name = featureInfo.name;
            appDetailsItems.add(appDetailsItem);
        }
        mFeatures.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<ConfigurationInfo>>> mConfigurations = new MutableLiveData<>();

    @WorkerThread
    private void loadConfigurations() {
        List<AppDetailsItem<ConfigurationInfo>> appDetailsItems = new ArrayList<>();
        if (getPackageInfoInternal() == null || mPackageInfo.configPreferences != null) {
            for (ConfigurationInfo configurationInfo : mPackageInfo.configPreferences) {
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

    @SuppressWarnings("deprecation")
    @WorkerThread
    private void loadSignatures() {
        List<AppDetailsItem<X509Certificate>> appDetailsItems = new ArrayList<>();
        if (mApkFile == null) {
            mSignatures.postValue(appDetailsItems);
            return;
        }
        try {
            File idsigFile = mApkFile.getIdsigFile();
            ApkVerifier.Builder builder = new ApkVerifier.Builder(mApkFile.getBaseEntry().getRealCachedFile());
            if (idsigFile != null) {
                builder.setV4SignatureFile(idsigFile);
            }
            ApkVerifier apkVerifier = builder.build();
            mApkVerifierResult = apkVerifier.verify();
            // Get signer certificates
            List<X509Certificate> certificates = mApkVerifierResult.getSignerCertificates();
            if (certificates != null && certificates.size() > 0) {
                for (X509Certificate certificate : certificates) {
                    AppDetailsItem<X509Certificate> item = new AppDetailsItem<>(certificate);
                    item.name = "Signer Certificate";
                    appDetailsItems.add(item);
                }
                if (mIsExternalApk && mPackageInfo.signatures == null) {
                    List<Signature> signatures = new ArrayList<>(certificates.size());
                    for (X509Certificate certificate : certificates) {
                        try {
                            signatures.add(new Signature(certificate.getEncoded()));
                        } catch (CertificateEncodingException ignore) {
                        }
                    }
                    mPackageInfo.signatures = signatures.toArray(new Signature[0]);
                }
            } else {
                //noinspection ConstantConditions Null is deliberately set here to get at least one row
                appDetailsItems.add(new AppDetailsItem<>(null));
            }
            // Get source stamp certificate
            if (mApkVerifierResult.isSourceStampVerified()) {
                ApkVerifier.Result.SourceStampInfo sourceStampInfo = mApkVerifierResult.getSourceStampInfo();
                X509Certificate certificate = sourceStampInfo.getCertificate();
                if (certificate != null) {
                    AppDetailsItem<X509Certificate> item = new AppDetailsItem<>(certificate);
                    item.name = "SourceStamp Certificate";
                    appDetailsItems.add(item);
                }
            }
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        mSignatures.postValue(appDetailsItems);
    }

    @NonNull
    private final MutableLiveData<List<AppDetailsItem<?>>> mSharedLibraries = new MutableLiveData<>();

    @WorkerThread
    private void loadSharedLibraries() {
        List<AppDetailsItem<?>> appDetailsItems = new ArrayList<>();
        if (getPackageInfoInternal() == null) {
            mSharedLibraries.postValue(appDetailsItems);
            return;
        }
        ApplicationInfo info = mPackageInfo.applicationInfo;
        File jniDir = new File(info.nativeLibraryDir);
        if (info.sharedLibraryFiles != null) {
            for (String sharedLibrary : info.sharedLibraryFiles) {
                File sharedLib = new File(sharedLibrary);
                AppDetailsItem<?> appDetailsItem = new AppDetailsItem<>(sharedLib);
                appDetailsItem.name = sharedLib.getName();
                appDetailsItems.add(appDetailsItem);
            }
        }
        List<String> nativeLibs = new ArrayList<>();
        if (jniDir.isDirectory()) {
            File[] libs = jniDir.listFiles();
            if (libs != null) {
                for (File lib : libs) {
                    nativeLibs.add(lib.getName());
                    AppDetailsItem<?> appDetailsItem = new AppDetailsItem<>(lib);
                    appDetailsItem.name = lib.getName();
                    appDetailsItems.add(appDetailsItem);
                }
            }
        }
        List<ApkFile.Entry> entries = mApkFile.getEntries();
        for (ApkFile.Entry entry : entries) {
            if (entry.type == ApkFile.APK_BASE
                    || entry.type == ApkFile.APK_SPLIT_FEATURE
                    || entry.type == ApkFile.APK_SPLIT_ABI
                    || entry.type == ApkFile.APK_SPLIT_UNKNOWN) {
                // Scan for .so files
                NativeLibraries nativeLibraries;
                try (InputStream is = entry.getRealInputStream()) {
                    try {
                        nativeLibraries = new NativeLibraries(is);
                    } catch (IOException e) {
                        // Maybe zip error, Try without InputStream
                        nativeLibraries = new NativeLibraries(entry.getRealCachedFile());
                    }
                    for (String nativeLib : nativeLibraries.getLibs()) {
                        if (!nativeLibs.contains(nativeLib)) {
                            AppDetailsItem<?> appDetailsItem = new AppDetailsItem<>(nativeLib);
                            appDetailsItem.name = nativeLib;
                            appDetailsItems.add(appDetailsItem);
                        }
                    }
                } catch (Throwable th) {
                    Log.e(TAG, th);
                }
            }
        }
        mSharedLibraries.postValue(appDetailsItems);
    }

    /**
     * Helper class to look for interesting changes to the installed apps
     * so that the loader can be updated.
     */
    public static class PackageIntentReceiver extends PackageChangeReceiver {
        final AppDetailsViewModel mModel;

        public PackageIntentReceiver(@NonNull AppDetailsViewModel model) {
            super(model.getApplication());
            this.mModel = model;
        }

        @Override
        @WorkerThread
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            if (uid != null && (mModel.mPackageInfo == null || mModel.mPackageInfo.applicationInfo.uid == uid)) {
                Log.d("ADVM", "Package is changed.");
                mModel.setIsPackageChanged();
            } else if (packages != null) {
                for (String packageName : packages) {
                    if (packageName.equals(mModel.mPackageName)) {
                        Log.d("ADVM", "Package availability changed.");
                        mModel.setIsPackageChanged();
                    }
                }
            } else {
                Log.d("ADVM", "Locale changed.");
                mModel.setIsPackageChanged();
            }
        }
    }
}
