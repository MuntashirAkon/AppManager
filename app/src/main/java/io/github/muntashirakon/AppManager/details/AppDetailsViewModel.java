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

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.DeadSystemException;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class AppDetailsViewModel extends AndroidViewModel {
    private final PackageManager mPackageManager;
    private PackageInfo packageInfo;
    private String packageName;
    private final Object blockerLocker = new Object();
    @GuardedBy("blockerLocker")
    private ComponentsBlocker blocker;
    private PackageIntentReceiver receiver;
    private String apkPath;
    private ApkFile apkFile;
    private int apkFileKey;
    private int userHandle;

    @AppDetailsFragment.SortOrder
    private int sortOrderComponents = (int) AppPref.get(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT);
    @AppDetailsFragment.SortOrder
    private int sortOrderAppOps = (int) AppPref.get(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT);
    @AppDetailsFragment.SortOrder
    private int sortOrderPermissions = (int) AppPref.get(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT);

    private String searchQuery;
    private boolean waitForBlocker;
    private boolean isExternalApk = false;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        Log.d("ADVM", "New constructor called.");
        mPackageManager = application.getPackageManager();
        receiver = new PackageIntentReceiver(this);
        waitForBlocker = true;
    }

    @GuardedBy("blockerLocker")
    @Override
    public void onCleared() {
        Log.d("ADVM", "On Clear called for " + packageName);
        new Thread(() -> {
            synchronized (blockerLocker) {
                if (blocker != null) {
                    // To prevent commit if a mutable instance was created in the middle,
                    // set the instance read only again
                    blocker.setReadOnly();
                    blocker.close();
                }
            }
        }).start();
        if (receiver != null) getApplication().unregisterReceiver(receiver);
        receiver = null;
        IOUtils.closeQuietly(apkFile);
        super.onCleared();
    }

    @WorkerThread
    public void setPackage(@NonNull Uri packageUri, @Nullable String type) throws ApkFile.ApkFileException, IOException {
        Log.d("ADVM", "Package Uri is being set");
        isExternalApk = true;
        apkFileKey = ApkFile.createInstance(packageUri, type);
        apkFile = ApkFile.getInstance(apkFileKey);
        apkPath = apkFile.getBaseEntry().getRealCachedFile().getAbsolutePath();
    }

    @WorkerThread
    public void setPackage(@NonNull String packageName) throws ApkFile.ApkFileException {
        Log.d("ADVM", "Package name is being set");
        isExternalApk = false;
        setPackageName(packageName);
        if (getPackageInfo() == null) throw new ApkFile.ApkFileException("Package not installed.");
        apkFileKey = ApkFile.createInstance(getPackageInfo().applicationInfo);
        apkFile = ApkFile.getInstance(apkFileKey);
    }

    public void setUserHandle(int userHandle) {
        this.userHandle = userHandle;
    }

    public int getUserHandle() {
        return userHandle;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void setPackageName(String packageName) {
        if (this.packageName != null) return;
        Log.d("ADVM", "Package name is being set for " + packageName);
        this.packageName = packageName;
        if (isExternalApk) return;
        new Thread(() -> {
            synchronized (blockerLocker) {
                waitForBlocker = true;
                if (blocker != null) {
                    // To prevent commit if a mutable instance was created in the middle,
                    // set the instance read only again
                    blocker.setReadOnly();
                    blocker.close();
                }
                blocker = ComponentsBlocker.getInstance(packageName, userHandle);
                waitForBlocker = false;
                blockerLocker.notifyAll();
            }
        }).start();
    }

    public String getPackageName() {
        return packageName;
    }

    public int getApkFileKey() {
        return apkFileKey;
    }

    @SuppressLint("SwitchIntDef")
    public void setSortOrder(@AppDetailsFragment.SortOrder int sortOrder, @AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
            case AppDetailsFragment.SERVICES:
            case AppDetailsFragment.RECEIVERS:
            case AppDetailsFragment.PROVIDERS:
                sortOrderComponents = sortOrder;
                AppPref.set(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT, sortOrder);
                break;
            case AppDetailsFragment.APP_OPS:
                sortOrderAppOps = sortOrder;
                AppPref.set(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT, sortOrder);
                break;
            case AppDetailsFragment.USES_PERMISSIONS:
                sortOrderPermissions = sortOrder;
                AppPref.set(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT, sortOrder);
                break;
        }
    }

    @SuppressLint("SwitchIntDef")
    @AppDetailsFragment.SortOrder
    public int getSortOrder(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
            case AppDetailsFragment.SERVICES:
            case AppDetailsFragment.RECEIVERS:
            case AppDetailsFragment.PROVIDERS:
                return sortOrderComponents;
            case AppDetailsFragment.APP_OPS:
                return sortOrderAppOps;
            case AppDetailsFragment.USES_PERMISSIONS:
                return sortOrderPermissions;
        }
        return AppDetailsFragment.SORT_BY_NAME;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    MutableLiveData<Integer> ruleApplicationStatus;
    public static final int RULE_APPLIED = 0;
    public static final int RULE_NOT_APPLIED = 1;
    public static final int RULE_NO_RULE = 2;

    public LiveData<Integer> getRuleApplicationStatus() {
        if (ruleApplicationStatus == null) {
            ruleApplicationStatus = new MutableLiveData<>();
            new Thread(this::setRuleApplicationStatus).start();
        }
        return ruleApplicationStatus;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void setRuleApplicationStatus() {
        if (packageName == null) return;
        if (isExternalApk) {
            ruleApplicationStatus.postValue(RULE_NO_RULE);
            return;
        }
        synchronized (blockerLocker) {
            waitForBlockerOrExit();
            final AtomicInteger newRuleApplicationStatus = new AtomicInteger();
            newRuleApplicationStatus.set(blocker.isRulesApplied() ? RULE_APPLIED : RULE_NOT_APPLIED);
            if (blocker.componentCount() == 0) newRuleApplicationStatus.set(RULE_NO_RULE);
            ruleApplicationStatus.postValue(newRuleApplicationStatus.get());
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void updateRulesForComponent(String componentName, RulesStorageManager.Type type) {
        if (isExternalApk) return;
        synchronized (blockerLocker) {
            waitForBlockerOrExit();
            blocker.setMutable();
            if (blocker.hasComponent(componentName)) {
                // Component is in the list
                if (blocker.isComponentBlocked(componentName)) {
                    // Remove from the list
                    blocker.removeComponent(componentName);
                } else {
                    // The component isn't being blocked, simply remove it
                    blocker.removeEntry(componentName);
                }
            } else {
                // Add to the list
                blocker.addComponent(componentName, type);
            }
            // Apply rules if global blocking enable or already applied
            if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
                    || (ruleApplicationStatus != null && RULE_APPLIED == ruleApplicationStatus.getValue())) {
                blocker.applyRules(true);
            }
            // Set new status
            setRuleApplicationStatus();
            // Commit changes
            blocker.commit();
            blocker.setReadOnly();
            // Update UI
            reloadComponents();
        }
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setPermission(String permissionName, boolean isGranted) {
        if (isExternalApk) return false;
        int appOp = AppOpsManager.permissionToOpCode(permissionName);
        if (isGranted) {
            if (appOp != AppOpsManager.OP_NONE) {
                try {
                    mAppOpsService.setMode(appOp, -1, packageName, AppOpsManager.MODE_ALLOWED, userHandle);
                } catch (Exception e) {
                    return false;
                }
            } else if (!RunnerUtils.grantPermission(packageName, permissionName, Users.getCurrentUserHandle()).isSuccessful()) {
                return false;
            }
        } else {
            if (appOp != AppOpsManager.OP_NONE) {
                try {
                    mAppOpsService.setMode(appOp, -1, packageName, AppOpsManager.MODE_IGNORED, userHandle);
                } catch (Exception e) {
                    return false;
                }
            } else if (!RunnerUtils.revokePermission(packageName, permissionName, Users.getCurrentUserHandle()).isSuccessful()) {
                return false;
            }
        }
        new Thread(() -> {
            synchronized (blockerLocker) {
                waitForBlockerOrExit();
                blocker.setMutable();
                if (appOp != AppOpsManager.OP_NONE) {
                    blocker.setAppOp(String.valueOf(appOp), isGranted ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
                } else {
                    blocker.setPermission(permissionName, isGranted);
                }
                blocker.commit();
                blocker.setReadOnly();
                blockerLocker.notifyAll();
            }
        }).start();
        return true;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean revokeDangerousPermissions() {
        if (isExternalApk) return false;
        AppDetailsPermissionItem permissionItem;
        List<String> revokedPermissions = new ArrayList<>();
        boolean isSuccessful = true;
        for (int i = 0; i < usesPermissionItems.size(); ++i) {
            permissionItem = usesPermissionItems.get(i);
            if (permissionItem.isDangerous && permissionItem.isGranted) {
                if (RunnerUtils.revokePermission(packageName, permissionItem.name, Users.getCurrentUserHandle()).isSuccessful()) {
                    permissionItem.isGranted = false;
                    usesPermissionItems.set(i, permissionItem);
                    revokedPermissions.add(permissionItem.name);
                } else {
                    isSuccessful = false;
                    break;
                }
            }
        }
        // Save values to the blocking rules
        new Thread(() -> {
            synchronized (blockerLocker) {
                waitForBlockerOrExit();
                blocker.setMutable();
                for (String permName : revokedPermissions)
                    blocker.setPermission(permName, false);
                blocker.commit();
                blocker.setReadOnly();
                blockerLocker.notifyAll();
            }
        }).start();
        return isSuccessful;
    }

    private AppOpsService mAppOpsService;

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean setAppOp(int op, int mode) {
        if (isExternalApk) return false;
        if (mAppOpsService == null) mAppOpsService = new AppOpsService();
        try {
            // Set mode
            mAppOpsService.setMode(op, -1, packageName, mode, userHandle);
            new Thread(() -> {
                synchronized (blockerLocker) {
                    waitForBlockerOrExit();
                    blocker.setMutable();
                    blocker.setAppOp(String.valueOf(op), mode);
                    blocker.commit();
                    blocker.setReadOnly();
                    blockerLocker.notifyAll();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean resetAppOps() {
        if (isExternalApk) return false;
        if (mAppOpsService != null) {
            try {
                mAppOpsService.resetAllModes(-1, packageName, userHandle);
                new Thread(this::loadAppOps).start();
                // Save values to the blocking rules
                new Thread(() -> {
                    synchronized (blockerLocker) {
                        waitForBlockerOrExit();
                        List<RulesStorageManager.Entry> appOpEntries = blocker.getAll(RulesStorageManager.Type.APP_OP);
                        blocker.setMutable();
                        for (RulesStorageManager.Entry entry : appOpEntries)
                            blocker.removeEntry(entry);
                        blocker.commit();
                        blocker.setReadOnly();
                        blockerLocker.notifyAll();
                    }
                }).start();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public boolean ignoreDangerousAppOps() {
        if (isExternalApk) return false;
        AppDetailsItem appDetailsItem;
        OpEntry opEntry;
        String permName;
        final List<Integer> opItems = new ArrayList<>();
        boolean isSuccessful = true;
        if (mAppOpsService == null) mAppOpsService = new AppOpsService();
        for (int i = 0; i < appOpItems.size(); ++i) {
            appDetailsItem = appOpItems.get(i);
            opEntry = (OpEntry) appDetailsItem.vanillaItem;
            try {
                permName = AppOpsManager.opToPermission(opEntry.getOp());
                if (permName != null) {
                    PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
                    int basePermissionType = PackageUtils.getBasePermissionType(permissionInfo);
                    if (basePermissionType == PermissionInfo.PROTECTION_DANGEROUS) {
                        // Set mode
                        try {
                            mAppOpsService.setMode(opEntry.getOp(), -1, packageName, AppOpsManager.MODE_IGNORED, userHandle);
                            opItems.add(opEntry.getOp());
                            appDetailsItem.vanillaItem = new OpEntry(opEntry.getOp(),
                                    AppOpsManager.MODE_IGNORED, opEntry.getTime(),
                                    opEntry.getRejectTime(), opEntry.getDuration(),
                                    opEntry.getProxyUid(), opEntry.getProxyPackageName());
                            appOpItems.set(i, appDetailsItem);
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
        // Save values to the blocking rules
        new Thread(() -> {
            synchronized (blockerLocker) {
                waitForBlockerOrExit();
                blocker.setMutable();
                for (int op : opItems)
                    blocker.setAppOp(String.valueOf(op), AppOpsManager.MODE_IGNORED);
                blocker.commit();
                blocker.setReadOnly();
                blockerLocker.notifyAll();
            }
        }).start();
        return isSuccessful;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    public void applyRules() {
        if (isExternalApk) return;
        synchronized (blockerLocker) {
            waitForBlockerOrExit();
            boolean oldIsRulesApplied = blocker.isRulesApplied();
            blocker.setMutable();
            blocker.applyRules(!oldIsRulesApplied);
            blocker.commit();
            blocker.setReadOnly();
            reloadComponents();
            setRuleApplicationStatus();
            blockerLocker.notifyAll();
        }
    }

    public LiveData<List<AppDetailsItem>> get(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                return getActivities();
            case AppDetailsFragment.SERVICES:
                return getServices();
            case AppDetailsFragment.RECEIVERS:
                return getReceivers();
            case AppDetailsFragment.PROVIDERS:
                return getProviders();
            case AppDetailsFragment.APP_OPS:
                return getAppOps();
            case AppDetailsFragment.USES_PERMISSIONS:
                return getUsesPermissions();
            case AppDetailsFragment.PERMISSIONS:
                return getPermissions();
            case AppDetailsFragment.FEATURES:
                return getFeatures();
            case AppDetailsFragment.CONFIGURATIONS:
                return getConfigurations();
            case AppDetailsFragment.SIGNATURES:
                return getSignatures();
            case AppDetailsFragment.SHARED_LIBRARIES:
                return getSharedLibraries();
            case AppDetailsFragment.APP_INFO:
                return getAppInfo();
            case AppDetailsFragment.NONE:
        }
        return null;
    }

    public void load(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
                new Thread(this::loadActivities).start();
                break;
            case AppDetailsFragment.SERVICES:
                new Thread(this::loadServices).start();
                break;
            case AppDetailsFragment.RECEIVERS:
                new Thread(this::loadReceivers).start();
                break;
            case AppDetailsFragment.PROVIDERS:
                new Thread(this::loadProviders).start();
                break;
            case AppDetailsFragment.APP_OPS:
                new Thread(this::loadAppOps).start();
                break;
            case AppDetailsFragment.USES_PERMISSIONS:
                new Thread(this::loadUsesPermissions).start();
                break;
            case AppDetailsFragment.PERMISSIONS:
                new Thread(this::loadPermissions).start();
                break;
            case AppDetailsFragment.FEATURES:
                new Thread(this::loadFeatures).start();
                break;
            case AppDetailsFragment.CONFIGURATIONS:
                new Thread(this::loadConfigurations).start();
                break;
            case AppDetailsFragment.SIGNATURES:
                new Thread(this::loadSignatures).start();
                break;
            case AppDetailsFragment.SHARED_LIBRARIES:
                new Thread(this::loadSharedLibraries).start();
                break;
            case AppDetailsFragment.APP_INFO:
                new Thread(this::loadAppInfo).start();
            case AppDetailsFragment.NONE:
                break;
        }
    }

    private final MutableLiveData<Boolean> isPackageExistLiveData = new MutableLiveData<>();
    private boolean isPackageExist = true;

    public LiveData<Boolean> getIsPackageExistLiveData() {
        if (isPackageExistLiveData.getValue() == null)
            isPackageExistLiveData.setValue(isPackageExist);
        return isPackageExistLiveData;
    }

    public boolean isPackageExist() {
        return isPackageExist;
    }

    @NonNull
    private final MutableLiveData<Boolean> isPackageChanged = new MutableLiveData<>();

    public LiveData<Boolean> getIsPackageChanged() {
        if (isPackageChanged.getValue() == null) {
            isPackageChanged.setValue(false);
        }
        return isPackageChanged;
    }

    @WorkerThread
    public void setIsPackageChanged() {
        setPackageInfo(true);
    }

    public boolean getIsExternalApk() {
        return isExternalApk;
    }

    public int getSplitCount() {
        if (apkFile.isSplit()) return apkFile.getEntries().size() - 1;
        return 0;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void waitForBlockerOrExit() {
        if (isExternalApk) return;
        if (blocker == null) {
            try {
                while (waitForBlocker) blockerLocker.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    @WorkerThread
    private void reloadComponents() {
        new Thread(this::loadActivities).start();
        new Thread(this::loadServices).start();
        new Thread(this::loadReceivers).start();
        new Thread(this::loadProviders).start();
    }

    @SuppressLint("WrongConstant")
    @WorkerThread
    public void setPackageInfo(boolean reload) {
        if (!isExternalApk && packageName == null) return;
        // Wait for component blocker to appear
        synchronized (blockerLocker) {
            waitForBlockerOrExit();
        }
        if (!reload && packageInfo != null) return;
        try {
            if (isExternalApk) {
                packageInfo = mPackageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | flagDisabledComponents | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS
                        | PackageManager.GET_SHARED_LIBRARY_FILES);
                if (packageInfo == null)
                    throw new PackageManager.NameNotFoundException("Package cannot be parsed");
                packageInfo.applicationInfo.sourceDir = apkPath;
                packageInfo.applicationInfo.publicSourceDir = apkPath;
                setPackageName(packageInfo.packageName);
            } else {
                packageInfo = ApiSupporter.getInstance(LocalServer.getInstance())
                        .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                | PackageManager.GET_URI_PERMISSION_PATTERNS
                                | flagDisabledComponents | flagSigningInfo
                                | PackageManager.GET_CONFIGURATIONS
                                | PackageManager.GET_SHARED_LIBRARY_FILES, userHandle);
            }
            isPackageExistLiveData.postValue(isPackageExist = true);
        } catch (PackageManager.NameNotFoundException e) {
            isPackageExistLiveData.postValue(isPackageExist = false);
        } catch (Exception e) {
            e.printStackTrace();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && e instanceof DeadSystemException) {
                // For some packages this exception might occur
                setPackageInfo(false);
                return;
            }
        }
        isPackageChanged.postValue(true);
    }

    @WorkerThread
    public PackageInfo getPackageInfo() {
        if (packageInfo == null) setPackageInfo(false);
        return packageInfo;
    }

    private MutableLiveData<List<AppDetailsItem>> appInfo;

    private LiveData<List<AppDetailsItem>> getAppInfo() {
        if (appInfo == null) {
            appInfo = new MutableLiveData<>();
            new Thread(this::loadAppInfo).start();
        }
        return appInfo;
    }

    @WorkerThread
    private void loadAppInfo() {
        if (getPackageInfo() == null || appInfo == null) return;
        AppDetailsItem appDetailsItem = new AppDetailsItem(packageInfo);
        appDetailsItem.name = packageName;
        List<AppDetailsItem> appDetailsItems = Collections.singletonList(appDetailsItem);
        appInfo.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> activities;

    private LiveData<List<AppDetailsItem>> getActivities() {
        if (activities == null) {
            activities = new MutableLiveData<>();
            new Thread(this::loadActivities).start();
        }
        return activities;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadActivities() {
        if (getPackageInfo() == null || activities == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.activities == null) {
            // There are no activities
            activities.postValue(appDetailsItems);
            return;
        }
        for (ActivityInfo activityInfo : packageInfo.activities) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
            appDetailsItem.name = activityInfo.targetActivity == null ? activityInfo.name : activityInfo.targetActivity;
            synchronized (blockerLocker) {
                if (!isExternalApk) {
                    appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                }
            }
            appDetailsItem.isTracker = ComponentUtils.isTracker(activityInfo.name);
            if (TextUtils.isEmpty(searchQuery) || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                appDetailsItems.add(appDetailsItem);
        }
        sortComponents(appDetailsItems);
        activities.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> services;

    private LiveData<List<AppDetailsItem>> getServices() {
        if (services == null) {
            services = new MutableLiveData<>();
            new Thread(this::loadServices).start();
        }
        return services;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadServices() {
        if (getPackageInfo() == null || services == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.services == null) {
            // There are no services
            services.postValue(appDetailsItems);
            return;
        }
        for (ServiceInfo serviceInfo : packageInfo.services) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(serviceInfo);
            appDetailsItem.name = serviceInfo.name;
            synchronized (blockerLocker) {
                if (!isExternalApk) {
                    appDetailsItem.isBlocked = blocker.hasComponent(serviceInfo.name);
                }
            }
            appDetailsItem.isTracker = ComponentUtils.isTracker(serviceInfo.name);
            if (TextUtils.isEmpty(searchQuery)
                    || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                appDetailsItems.add(appDetailsItem);
        }
        sortComponents(appDetailsItems);
        services.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> receivers;

    private LiveData<List<AppDetailsItem>> getReceivers() {
        if (receivers == null) {
            receivers = new MutableLiveData<>();
            new Thread(this::loadReceivers).start();
        }
        return receivers;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadReceivers() {
        if (getPackageInfo() == null || receivers == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.receivers == null) {
            // There are no receivers
            receivers.postValue(appDetailsItems);
            return;
        }
        for (ActivityInfo activityInfo : packageInfo.receivers) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
            appDetailsItem.name = activityInfo.name;
            synchronized (blockerLocker) {
                if (!isExternalApk) {
                    appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                }
            }
            appDetailsItem.isTracker = ComponentUtils.isTracker(activityInfo.name);
            if (TextUtils.isEmpty(searchQuery)
                    || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                appDetailsItems.add(appDetailsItem);
        }
        sortComponents(appDetailsItems);
        receivers.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> providers;

    private LiveData<List<AppDetailsItem>> getProviders() {
        if (providers == null) {
            providers = new MutableLiveData<>();
            new Thread(this::loadProviders).start();
        }
        return providers;
    }

    @WorkerThread
    @GuardedBy("blockerLocker")
    private void loadProviders() {
        if (getPackageInfo() == null || providers == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.providers == null) {
            // There are no providers
            providers.postValue(appDetailsItems);
            return;
        }
        for (ProviderInfo providerInfo : packageInfo.providers) {
            AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(providerInfo);
            appDetailsItem.name = providerInfo.name;
            synchronized (blockerLocker) {
                if (!isExternalApk) {
                    appDetailsItem.isBlocked = blocker.hasComponent(providerInfo.name);
                }
            }
            appDetailsItem.isTracker = ComponentUtils.isTracker(providerInfo.name);
            if (TextUtils.isEmpty(searchQuery)
                    || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                appDetailsItems.add(appDetailsItem);
        }
        sortComponents(appDetailsItems);
        providers.postValue(appDetailsItems);
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void sortComponents(List<AppDetailsItem> appDetailsItems) {
        // First sort by name
        Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        if (sortOrderComponents == AppDetailsFragment.SORT_BY_NAME) return;
        Collections.sort(appDetailsItems, (o1, o2) -> {
            switch (sortOrderComponents) {
                // No need to sort by name since we've already done it
                case AppDetailsFragment.SORT_BY_BLOCKED:
                    return -Boolean.compare(((AppDetailsComponentItem) o1).isBlocked, ((AppDetailsComponentItem) o2).isBlocked);
                case AppDetailsFragment.SORT_BY_TRACKERS:
                    return -Boolean.compare(((AppDetailsComponentItem) o1).isTracker, ((AppDetailsComponentItem) o2).isTracker);
            }
            return 0;
        });
    }

    MutableLiveData<List<AppDetailsItem>> appOps;
    List<AppDetailsItem> appOpItems;

    private LiveData<List<AppDetailsItem>> getAppOps() {
        if (appOps == null) {
            appOps = new MutableLiveData<>();
            new Thread(this::loadAppOps).start();
        }
        return appOps;
    }

    @WorkerThread
    public void setAppOp(AppDetailsItem appDetailsItem) {
        for (int i = 0; i < appOpItems.size(); ++i) {
            if (appOpItems.get(i).name.equals(appDetailsItem.name)) {
                appOpItems.set(i, appDetailsItem);
                break;
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void loadAppOps() {
        if (packageName == null || appOps == null) return;
        if (!isExternalApk && AppPref.isRootOrAdbEnabled()) {
            if (mAppOpsService == null) mAppOpsService = new AppOpsService();
            try {
                List<PackageOps> packageOpsList = mAppOpsService.getOpsForPackage(-1, packageName, null, userHandle);
                List<OpEntry> opEntries = new ArrayList<>();
                if (packageOpsList.size() == 1)
                    opEntries.addAll(packageOpsList.get(0).getOps());
                // Include defaults
                if ((boolean) AppPref.get(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL)) {
                    for (int op : AppOpsManager.sOpsWithNoPerm) {
                        opEntries.add(new OpEntry(op, AppOpsManager.opToDefaultMode(op), 0,
                                0, 0, 0, null));
                    }
                }
                Set<String> uniqueSet = new HashSet<>();
                appOpItems = new ArrayList<>(opEntries.size());
                for (OpEntry opEntry : opEntries) {
                    String opName = AppOpsManager.opToName(opEntry.getOp());
                    if (uniqueSet.contains(opName)) continue;
                    AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry);
                    appDetailsItem.name = opName;
                    uniqueSet.add(opName);
                    appOpItems.add(appDetailsItem);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (appOpItems == null) appOpItems = new ArrayList<>(0);
        if (!TextUtils.isEmpty(searchQuery)) {
            for (AppDetailsItem appDetailsItem : appOpItems)
                if (appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                    appDetailsItems.add(appDetailsItem);
        } else appDetailsItems.addAll(appOpItems);
        Collections.sort(appDetailsItems, (o1, o2) -> {
            switch (sortOrderAppOps) {
                case AppDetailsFragment.SORT_BY_NAME:
                    return o1.name.compareToIgnoreCase(o2.name);
                case AppDetailsFragment.SORT_BY_APP_OP_VALUES:
                    Integer o1Op = ((OpEntry) o1.vanillaItem).getOp();
                    Integer o2Op = ((OpEntry) o2.vanillaItem).getOp();
                    return o1Op.compareTo(o2Op);
                case AppDetailsFragment.SORT_BY_DENIED_APP_OPS:
                    // A slight hack to sort it this way: ignore > foreground > deny > default[ > ask] > allow
                    Integer o1Mode = ((OpEntry) o1.vanillaItem).getMode();
                    Integer o2Mode = ((OpEntry) o2.vanillaItem).getMode();
                    return -o1Mode.compareTo(o2Mode);
            }
            return 0;
        });
        appOps.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> usesPermissions;
    CopyOnWriteArrayList<AppDetailsPermissionItem> usesPermissionItems;

    private LiveData<List<AppDetailsItem>> getUsesPermissions() {
        if (usesPermissions == null) {
            usesPermissions = new MutableLiveData<>();
            new Thread(this::loadUsesPermissions).start();
        }
        return usesPermissions;
    }

    @WorkerThread
    public void setUsesPermission(String permissionName, boolean isGranted) {
        AppDetailsPermissionItem permissionItem;
        for (int i = 0; i < usesPermissionItems.size(); ++i) {
            permissionItem = usesPermissionItems.get(i);
            if (permissionItem.name.equals(permissionName)) {
                permissionItem.isGranted = isGranted;
                usesPermissionItems.set(i, permissionItem);
                break;
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    @WorkerThread
    private void loadUsesPermissions() {
        if (getPackageInfo() == null || usesPermissions == null) return;
        if (usesPermissionItems == null) {
            usesPermissionItems = new CopyOnWriteArrayList<>();
        } else usesPermissionItems.clear();
        if (packageInfo.requestedPermissions == null) {
            // No requested permissions
            usesPermissions.postValue(new ArrayList<>());
            return;
        }
        for (int i = 0; i < packageInfo.requestedPermissions.length; ++i) {
            try {
                PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(
                        packageInfo.requestedPermissions[i], PackageManager.GET_META_DATA);
                AppDetailsPermissionItem appDetailsItem = new AppDetailsPermissionItem(permissionInfo);
                appDetailsItem.name = packageInfo.requestedPermissions[i];
                appDetailsItem.flags = packageInfo.requestedPermissionsFlags[i];
                int basePermissionType = PackageUtils.getBasePermissionType(permissionInfo);
                appDetailsItem.isDangerous = basePermissionType == PermissionInfo.PROTECTION_DANGEROUS;
                appDetailsItem.isGranted = (appDetailsItem.flags & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                appDetailsItem.appOp = AppOpsManager.permissionToOpCode(appDetailsItem.name);
                if (appDetailsItem.appOp != AppOpsManager.OP_NONE) {
                    // Override isGranted
                    try {
                        appDetailsItem.isGranted = mAppOpsService.checkOperation(appDetailsItem.appOp, -1, packageName, userHandle) == AppOpsManager.MODE_ALLOWED;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                usesPermissionItems.add(appDetailsItem);
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }
        // Filter items
        final List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (!TextUtils.isEmpty(searchQuery)) {
            for (AppDetailsPermissionItem appDetailsItem : usesPermissionItems)
                if (appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                    appDetailsItems.add(appDetailsItem);
        } else appDetailsItems.addAll(usesPermissionItems);
        Collections.sort(appDetailsItems, (o1, o2) -> {
            switch (sortOrderPermissions) {
                case AppDetailsFragment.SORT_BY_NAME:
                    return o1.name.compareToIgnoreCase(o2.name);
                case AppDetailsFragment.SORT_BY_DANGEROUS_PERMS:
                    return -Boolean.compare(((AppDetailsPermissionItem) o1).isDangerous, ((AppDetailsPermissionItem) o2).isDangerous);
                case AppDetailsFragment.SORT_BY_DENIED_PERMS:
                    return Boolean.compare(((AppDetailsPermissionItem) o1).isGranted, ((AppDetailsPermissionItem) o2).isGranted);
            }
            return 0;
        });
        usesPermissions.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> permissions;

    private LiveData<List<AppDetailsItem>> getPermissions() {
        if (permissions == null) {
            permissions = new MutableLiveData<>();
            new Thread(this::loadPermissions).start();
        }
        return permissions;
    }

    @WorkerThread
    private void loadPermissions() {
        if (getPackageInfo() == null || permissions == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.permissions == null) {
            // No custom permissions
            permissions.postValue(appDetailsItems);
            return;
        }
        for (PermissionInfo permissionInfo : packageInfo.permissions) {
            AppDetailsItem appDetailsItem = new AppDetailsItem(permissionInfo);
            appDetailsItem.name = permissionInfo.name;
            if (TextUtils.isEmpty(searchQuery)
                    || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                appDetailsItems.add(appDetailsItem);
        }
        Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        permissions.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> features;

    private LiveData<List<AppDetailsItem>> getFeatures() {
        if (features == null) {
            features = new MutableLiveData<>();
            new Thread(this::loadFeatures).start();
        }
        return features;
    }

    private boolean bFi = false;

    public boolean isbFi() {
        return bFi;
    }

    @WorkerThread
    private void loadFeatures() {
        if (getPackageInfo() == null || features == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.reqFeatures == null) {
            // No required features
            features.postValue(appDetailsItems);
            return;
        }
        try {
            Arrays.sort(packageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        } catch (NullPointerException e) {
            for (FeatureInfo fi : packageInfo.reqFeatures) {
                if (fi.name == null) fi.name = "OpenGL ES";
                bFi = true;
            }
            Arrays.sort(packageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        }
        for (FeatureInfo featureInfo : packageInfo.reqFeatures) {
            AppDetailsItem appDetailsItem = new AppDetailsItem(featureInfo);
            appDetailsItem.name = featureInfo.name;
            appDetailsItems.add(appDetailsItem);
        }
        features.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> configurations;

    private LiveData<List<AppDetailsItem>> getConfigurations() {
        if (configurations == null) {
            configurations = new MutableLiveData<>();
            new Thread(this::loadConfigurations).start();
        }
        return configurations;
    }

    @WorkerThread
    private void loadConfigurations() {
        if (getPackageInfo() == null || configurations == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.configPreferences != null) {
            for (ConfigurationInfo configurationInfo : packageInfo.configPreferences) {
                AppDetailsItem appDetailsItem = new AppDetailsItem(configurationInfo);
                appDetailsItems.add(appDetailsItem);
            }
        }
        configurations.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> signatures;

    private LiveData<List<AppDetailsItem>> getSignatures() {
        if (signatures == null) {
            signatures = new MutableLiveData<>();
            new Thread(this::loadSignatures).start();
        }
        return signatures;
    }

    @WorkerThread
    private void loadSignatures() {
        if (getPackageInfo() == null || signatures == null) return;
        Signature[] signatureArray = PackageUtils.getSigningInfo(packageInfo, isExternalApk);
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (signatureArray != null) {
            for (Signature signature : signatureArray) {
                AppDetailsItem appDetailsItem = new AppDetailsItem(signature);
                appDetailsItems.add(appDetailsItem);
            }
        }
        signatures.postValue(appDetailsItems);
    }

    MutableLiveData<List<AppDetailsItem>> sharedLibraries;

    private LiveData<List<AppDetailsItem>> getSharedLibraries() {
        if (sharedLibraries == null) {
            sharedLibraries = new MutableLiveData<>();
            new Thread(this::loadSharedLibraries).start();
        }
        return sharedLibraries;
    }

    @WorkerThread
    private void loadSharedLibraries() {
        if (getPackageInfo() == null || sharedLibraries == null) return;
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        if (packageInfo.applicationInfo.sharedLibraryFiles != null) {
            for (String sharedLibrary : packageInfo.applicationInfo.sharedLibraryFiles) {
                AppDetailsItem appDetailsItem = new AppDetailsItem(sharedLibrary);
                appDetailsItem.name = sharedLibrary;
                appDetailsItems.add(appDetailsItem);
            }
        }
        sharedLibraries.postValue(appDetailsItems);
    }

    /**
     * Helper class to look for interesting changes to the installed apps
     * so that the loader can be updated.
     */
    public static class PackageIntentReceiver extends BroadcastReceiver {

        final AppDetailsViewModel model;

        public PackageIntentReceiver(AppDetailsViewModel model) {
            this.model = model;
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            this.model.getApplication().registerReceiver(this, filter);
            // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            this.model.getApplication().registerReceiver(this, sdFilter);
        }

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) break;
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED:
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    if (model.packageInfo.applicationInfo.uid == uid || model.isExternalApk) {
                        Log.d("ADVM", "Package is changed.");
                        model.setIsPackageChanged();
                    }
                    break;
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                    String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    if (packages != null) {
                        for (String packageName : packages) {
                            if (packageName.equals(model.packageName)) {
                                Log.d("ADVM", "Package availability changed.");
                                model.setIsPackageChanged();
                            }
                        }
                    }
                    break;
                case Intent.ACTION_LOCALE_CHANGED:
                    Log.d("ADVM", "Locale changed.");
                    model.setIsPackageChanged();
            }
        }
    }
}
