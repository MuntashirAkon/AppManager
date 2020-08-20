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
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.DeadSystemException;
import android.text.TextUtils;
import android.util.Log;

import com.google.classysharkandroid.utils.UriUtils;

import java.io.File;
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

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppDetailsViewModel extends AndroidViewModel {
    public static final String APK_FILE = "apk_file.apk";

    private PackageManager mPackageManager;
    private PackageInfo packageInfo;
    private String packageName;
    private ComponentsBlocker blocker;
    private PackageIntentReceiver receiver;
    private String apkPath;

    private int flagSigningInfo;
    private int flagDisabledComponents;

    private @AppDetailsFragment.SortOrder int sortOrderComponents = AppDetailsFragment.SORT_BY_NAME;
    private @AppDetailsFragment.SortOrder int sortOrderAppOps = AppDetailsFragment.SORT_BY_NAME;
    private @AppDetailsFragment.SortOrder int sortOrderPermissions = AppDetailsFragment.SORT_BY_NAME;

    private String searchQuery;
    private boolean waitForBlocker;
    private boolean isExternalApk = false;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        Log.d("ADVM", "New constructor called.");
        mPackageManager = application.getPackageManager();
        receiver = new PackageIntentReceiver(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            flagDisabledComponents = PackageManager.MATCH_DISABLED_COMPONENTS;
        else flagDisabledComponents = PackageManager.GET_DISABLED_COMPONENTS;
        waitForBlocker = true;
    }

    @Override
    public void onCleared() {
        Log.d("ADVM", "On Clear called for " + packageName);
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
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
        if (apkPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(apkPath).delete();
        }
        super.onCleared();
    }

    public void setPackageUri(@NonNull Uri packageUri) {
        Log.d("ADVM", "Package Uri is being set");
        isExternalApk = true;
        flagSigningInfo = PackageManager.GET_SIGNATURES;  // Fix signature bug of Android
        apkPath = UriUtils.pathUriCache(getApplication(), packageUri, APK_FILE);
    }

    public void setPackageName(String packageName) {
        if (this.packageName != null) return;
        Log.d("ADVM", "Package name is being set for " + packageName);
        this.packageName = packageName;
        if (isExternalApk) return;
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlocker = true;
                if (blocker != null) {
                    // To prevent commit if a mutable instance was created in the middle,
                    // set the instance read only again
                    blocker.setReadOnly();
                    blocker.close();
                }
                blocker = ComponentsBlocker.getInstance(packageName);
                waitForBlocker = false;
                ComponentsBlocker.class.notifyAll();
            }
        }).start();
    }

    public String getPackageName() {
        return packageName;
    }

    @SuppressLint("SwitchIntDef")
    public void setSortOrder(@AppDetailsFragment.SortOrder int sortOrder, @AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES:
            case AppDetailsFragment.SERVICES:
            case AppDetailsFragment.RECEIVERS:
            case AppDetailsFragment.PROVIDERS: sortOrderComponents = sortOrder; break;
            case AppDetailsFragment.APP_OPS: sortOrderAppOps = sortOrder; break;
            case AppDetailsFragment.USES_PERMISSIONS: sortOrderPermissions = sortOrder; break;
        }
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    MutableLiveData<Integer> ruleApplicationStatus;
    public static final int RULE_APPLIED     = 0;
    public static final int RULE_NOT_APPLIED = 1;
    public static final int RULE_NO_RULE     = 2;
    public LiveData<Integer> getRuleApplicationStatus() {
        if (ruleApplicationStatus == null) {
            ruleApplicationStatus = new MutableLiveData<>();
            new Thread(this::setRuleApplicationStatus).start();
        }
        return ruleApplicationStatus;
    }

    /**
     * This function should always be called inside a thread
     */
    public void setRuleApplicationStatus() {
        if (packageName == null) return;
        if (isExternalApk) {
            ruleApplicationStatus.postValue(RULE_NO_RULE);
            return;
        }
        synchronized (ComponentsBlocker.class) {
            waitForBlockerOrExit();
            final AtomicInteger newRuleApplicationStatus = new AtomicInteger();
            newRuleApplicationStatus.set(blocker.isRulesApplied() ? RULE_APPLIED : RULE_NOT_APPLIED);
            if (blocker.componentCount() == 0) newRuleApplicationStatus.set(RULE_NO_RULE);
            ruleApplicationStatus.postValue(newRuleApplicationStatus.get());
        }
    }

    public void updateRulesForComponent(String componentName, RulesStorageManager.Type type) {
        if (isExternalApk) return;
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlockerOrExit();
                blocker.setMutable();
                if (blocker.hasComponent(componentName)) { // Remove from the list
                    blocker.removeComponent(componentName);
                } else { // Add to the list
                    blocker.addComponent(componentName, type);
                }
                // Apply rules if global blocking enable or already applied
                //noinspection ConstantConditions
                if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
                        || (ruleApplicationStatus != null && ruleApplicationStatus.getValue() == RULE_APPLIED)) {
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
        }).start();
    }

    public boolean setPermission(String permissionName, boolean isGranted) {
        if (isExternalApk) return false;
        if (isGranted) {
            if (!RunnerUtils.grantPermission(packageName, permissionName).isSuccessful())
                return false;
        } else {
            if (!RunnerUtils.revokePermission(packageName, permissionName).isSuccessful())
                return false;
        }
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlockerOrExit();
                blocker.setMutable();
                blocker.setPermission(permissionName, isGranted);
                blocker.commit();
                blocker.setReadOnly();
                ComponentsBlocker.class.notifyAll();
            }
        }).start();
        return true;
    }

    public boolean revokeDangerousPermissions() {
        if (isExternalApk) return false;
        AppDetailsPermissionItem permissionItem;
        List<String> revokedPermissions = new ArrayList<>();
        boolean isSuccessful = true;
        for (int i = 0; i<usesPermissionItems.size(); ++i) {
            permissionItem = usesPermissionItems.get(i);
            if (permissionItem.isDangerous && permissionItem.isGranted) {
                if (RunnerUtils.revokePermission(packageName, permissionItem.name).isSuccessful()) {
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
            synchronized (ComponentsBlocker.class) {
                waitForBlockerOrExit();
                blocker.setMutable();
                for (String permName: revokedPermissions)
                    blocker.setPermission(permName, false);
                blocker.commit();
                blocker.setReadOnly();
                ComponentsBlocker.class.notifyAll();
            }
        }).start();
        return isSuccessful;
    }

    AppOpsService mAppOpsService;
    public boolean setAppOp(int op, int mode) {
        if (isExternalApk) return false;
        if (mAppOpsService == null) mAppOpsService = new AppOpsService();
        try {
            // Set mode
            mAppOpsService.setMode(op, -1, packageName, mode);
            new Thread(() -> {
                synchronized (ComponentsBlocker.class) {
                    waitForBlockerOrExit();
                    blocker.setMutable();
                    blocker.setAppOp(String.valueOf(op), mode);
                    blocker.commit();
                    blocker.setReadOnly();
                    ComponentsBlocker.class.notifyAll();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean resetAppOps() {
        if (isExternalApk) return false;
        if (mAppOpsService != null) {
            try {
                mAppOpsService.resetAllModes(-1, packageName);
                appOpItems.clear();
                appOpItems = null;
                loadAppOps();
                // Save values to the blocking rules
                new Thread(() -> {
                    synchronized (ComponentsBlocker.class) {
                        waitForBlockerOrExit();
                        List<RulesStorageManager.Entry> appOpEntries = blocker.getAll(RulesStorageManager.Type.APP_OP);
                        blocker.setMutable();
                        for (RulesStorageManager.Entry entry: appOpEntries)
                            blocker.removeEntry(entry);
                        blocker.commit();
                        blocker.setReadOnly();
                        ComponentsBlocker.class.notifyAll();
                    }
                }).start();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean ignoreDangerousAppOps() {
        if (isExternalApk) return false;
        AppDetailsItem appDetailsItem;
        OpEntry opEntry;
        String permName;
        final List<Integer> opItems = new ArrayList<>();
        final String modeName = AppOpsManager.modeToName(AppOpsManager.MODE_IGNORED);
        boolean isSuccessful = true;
        if (mAppOpsService == null) mAppOpsService = new AppOpsService();
        for (int i = 0; i<appOpItems.size(); ++i) {
            appDetailsItem = appOpItems.get(i);
            opEntry = (OpEntry) appDetailsItem.vanillaItem;
            try {
                permName = AppOpsManager.opToPermission(opEntry.getOp());
                if (permName != null) {
                    PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
                    int basePermissionType;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        basePermissionType = permissionInfo.getProtection();
                    } else {
                        basePermissionType = permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                    }
                    if (basePermissionType == PermissionInfo.PROTECTION_DANGEROUS) {
                        // Set mode
                        try {
                            mAppOpsService.setMode(opEntry.getOp(), -1, packageName, AppOpsManager.MODE_IGNORED);
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
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException | IndexOutOfBoundsException ignore) {}
        }
        // Save values to the blocking rules
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlockerOrExit();
                blocker.setMutable();
                for (int op: opItems)
                    blocker.setAppOp(String.valueOf(op), AppOpsManager.MODE_IGNORED);
                blocker.commit();
                blocker.setReadOnly();
                ComponentsBlocker.class.notifyAll();
            }
        }).start();
        return isSuccessful;
    }

    public void applyRules() {
        if (isExternalApk) return;
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlockerOrExit();
                boolean oldIsRulesApplied = blocker.isRulesApplied();
                blocker.setMutable();
                blocker.applyRules(!oldIsRulesApplied);
                blocker.commit();
                blocker.setReadOnly();
                reloadComponents();
                setRuleApplicationStatus();
            }
        }).start();
    }

    public LiveData<List<AppDetailsItem>> get(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES: return getActivities();
            case AppDetailsFragment.SERVICES: return getServices();
            case AppDetailsFragment.RECEIVERS: return getReceivers();
            case AppDetailsFragment.PROVIDERS: return getProviders();
            case AppDetailsFragment.APP_OPS: return getAppOps();
            case AppDetailsFragment.USES_PERMISSIONS: return getUsesPermissions();
            case AppDetailsFragment.PERMISSIONS: return getPermissions();
            case AppDetailsFragment.FEATURES: return getFeatures();
            case AppDetailsFragment.CONFIGURATIONS: return getConfigurations();
            case AppDetailsFragment.SIGNATURES: return getSignatures();
            case AppDetailsFragment.SHARED_LIBRARIES: return getSharedLibraries();
            case AppDetailsFragment.APP_INFO: return getAppInfo();
            case AppDetailsFragment.NONE:
        }
        return null;
    }

    public void load(@AppDetailsFragment.Property int property) {
        switch (property) {
            case AppDetailsFragment.ACTIVITIES: loadActivities(); break;
            case AppDetailsFragment.SERVICES: loadServices(); break;
            case AppDetailsFragment.RECEIVERS: loadReceivers(); break;
            case AppDetailsFragment.PROVIDERS: loadProviders(); break;
            case AppDetailsFragment.APP_OPS: loadAppOps(); break;
            case AppDetailsFragment.USES_PERMISSIONS: loadUsesPermissions(); break;
            case AppDetailsFragment.PERMISSIONS: loadPermissions(); break;
            case AppDetailsFragment.FEATURES: loadFeatures(); break;
            case AppDetailsFragment.CONFIGURATIONS: loadConfigurations(); break;
            case AppDetailsFragment.SIGNATURES: loadSignatures(); break;
            case AppDetailsFragment.SHARED_LIBRARIES: loadSharedLibraries(); break;
            case AppDetailsFragment.APP_INFO: loadAppInfo();
            case AppDetailsFragment.NONE:
                break;
        }
    }

    private MutableLiveData<Boolean> isPackageExist = new MutableLiveData<>();
    public LiveData<Boolean> getIsPackageExist() {
        if (isPackageExist.getValue() == null) isPackageExist.setValue(true);
        return isPackageExist;
    }

    private @NonNull MutableLiveData<Boolean> isPackageChanged = new MutableLiveData<>();
    public LiveData<Boolean> getIsPackageChanged() {
        if (isPackageChanged.getValue() == null) {
            isPackageChanged.setValue(false);
        }
        return isPackageChanged;
    }

    public void setIsPackageChanged() {
        setPackageInfo(true);
    }

    public boolean getIsExternalApk() {
        return isExternalApk;
    }

    private void waitForBlockerOrExit() {
        if (isExternalApk) return;
        if (blocker == null) {
            try {
                while (waitForBlocker) ComponentsBlocker.class.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void reloadComponents() {
        loadActivities();
        loadServices();
        loadReceivers();
        loadProviders();
    }

    public void setPackageInfo(boolean reload) {
        if (!isExternalApk && packageName == null) return;
        // Wait for component blocker to appear
        synchronized (ComponentsBlocker.class) {
            waitForBlockerOrExit();
        }
        if (!reload && packageInfo != null) return;
        try {
            if (isExternalApk) {
                packageInfo = mPackageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                        | PackageManager.GET_SHARED_LIBRARY_FILES);
                if (packageInfo == null) throw new PackageManager.NameNotFoundException("Package cannot be parsed");
                packageInfo.applicationInfo.sourceDir = apkPath;
                packageInfo.applicationInfo.publicSourceDir = apkPath;
                setPackageName(packageInfo.packageName);
            } else {
                packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                        | PackageManager.GET_SHARED_LIBRARY_FILES);
            }
            isPackageExist.postValue(true);
        } catch (PackageManager.NameNotFoundException e) {
            isPackageExist.postValue(false);
        } catch (Exception e) {
            e.printStackTrace();
            //noinspection ConstantConditions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && e instanceof DeadSystemException) {
                // For some packages this exception might occur
                setPackageInfo(false);
                return;
            }
        }
        isPackageChanged.postValue(true);
    }

    public PackageInfo getPackageInfo() {
        if (packageInfo == null) setPackageInfo(false);
        return packageInfo;
    }

    private MutableLiveData<List<AppDetailsItem>> appInfo;
    private LiveData<List<AppDetailsItem>> getAppInfo() {
        if (appInfo == null) {
            appInfo = new MutableLiveData<>();
            loadAppInfo();
        }
        return appInfo;
    }

    private void loadAppInfo() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || appInfo == null) return;
            AppDetailsItem appDetailsItem = new AppDetailsItem(packageInfo);
            appDetailsItem.name = packageName;
            List<AppDetailsItem> appDetailsItems = Collections.singletonList(appDetailsItem);
            appInfo.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> activities;
    private LiveData<List<AppDetailsItem>> getActivities() {
        if (activities == null) {
            activities = new MutableLiveData<>();
            loadActivities();
        }
        return activities;
    }

    private void loadActivities() {
        if (activities == null) return;
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || activities == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                    appDetailsItem.name = activityInfo.targetActivity == null ? activityInfo.name : activityInfo.targetActivity;
                    if (!isExternalApk) appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                    appDetailsItem.isTracker = ComponentUtils.isTracker(activityInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            activities.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> services;
    private LiveData<List<AppDetailsItem>> getServices() {
        if (services == null) {
            services = new MutableLiveData<>();
            loadServices();
        }
        return services;
    }

    private void loadServices() {
        if (services == null) return;
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || services == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.services != null) {
                for (ServiceInfo serviceInfo : packageInfo.services) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(serviceInfo);
                    appDetailsItem.name = serviceInfo.name;
                    if (!isExternalApk) appDetailsItem.isBlocked = blocker.hasComponent(serviceInfo.name);
                    appDetailsItem.isTracker = ComponentUtils.isTracker(serviceInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            services.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> receivers;
    private LiveData<List<AppDetailsItem>> getReceivers() {
        if (receivers == null) {
            receivers = new MutableLiveData<>();
            loadReceivers();
        }
        return receivers;
    }

    private void loadReceivers() {
        if (receivers == null) return;
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || receivers == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.receivers != null) {
                for (ActivityInfo activityInfo : packageInfo.receivers) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                    appDetailsItem.name = activityInfo.name;
                    if (!isExternalApk) appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                    appDetailsItem.isTracker = ComponentUtils.isTracker(activityInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            receivers.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> providers;
    private LiveData<List<AppDetailsItem>> getProviders() {
        if (providers == null) {
            providers = new MutableLiveData<>();
            loadProviders();
        }
        return providers;
    }

    private void loadProviders() {
        if (providers == null) return;
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || providers == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.providers != null) {
                for (ProviderInfo providerInfo : packageInfo.providers) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(providerInfo);
                    appDetailsItem.name = providerInfo.name;
                    if (!isExternalApk) appDetailsItem.isBlocked = blocker.hasComponent(providerInfo.name);
                    appDetailsItem.isTracker = ComponentUtils.isTracker(providerInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            providers.postValue(appDetailsItems);
        }).start();
    }

    @SuppressLint("SwitchIntDef")
    private void sortComponents(List<AppDetailsItem> appDetailsItems) {
        if (sortOrderComponents != AppDetailsFragment.SORT_BY_NAME)
            Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        Collections.sort(appDetailsItems, (o1, o2) -> {
            switch (sortOrderComponents) {
                case AppDetailsFragment.SORT_BY_NAME:
                    return o1.name.compareToIgnoreCase(o2.name);
                case AppDetailsFragment.SORT_BY_BLOCKED:
                    return -Utils.compareBooleans(((AppDetailsComponentItem) o1).isBlocked, ((AppDetailsComponentItem) o2).isBlocked);
                case AppDetailsFragment.SORT_BY_TRACKERS:
                    return -Utils.compareBooleans(((AppDetailsComponentItem) o1).isTracker, ((AppDetailsComponentItem) o2).isTracker);
            }
            return 0;
        });
    }

    MutableLiveData<List<AppDetailsItem>> appOps;
    List<AppDetailsItem> appOpItems;
    private LiveData<List<AppDetailsItem>> getAppOps() {
        if (appOps == null) {
            appOps = new MutableLiveData<>();
            loadAppOps();
        }
        return appOps;
    }

    public void setAppOp(AppDetailsItem appDetailsItem) {
        new Thread(() -> {
            for (int i = 0; i < appOpItems.size(); ++i) {
                if (appOpItems.get(i).name.equals(appDetailsItem.name)) {
                    appOpItems.set(i, appDetailsItem);
                    break;
                }
            }
        }).start();
    }

    @SuppressLint("SwitchIntDef")
    private void loadAppOps() {
        new Thread(() -> {
            if (packageName == null || appOps == null) return;
            if (appOpItems == null) {
                appOpItems = new ArrayList<>();
                if (!isExternalApk && (AppPref.isRootEnabled() || AppPref.isAdbEnabled())) {
                    if (mAppOpsService == null) mAppOpsService = new AppOpsService();
                    try {
                        List<PackageOps> packageOpsList = mAppOpsService.getOpsForPackage(-1, packageName, null);
                        List<OpEntry> opEntries = new ArrayList<>();
                        if (packageOpsList.size() == 1)
                            opEntries.addAll(packageOpsList.get(0).getOps());
                        // Include defaults
                        final int[] ops = {2, 11, 12, 15, 22, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 41, 42, 44, 45,
                                46, 47, 48, 49, 50, 58, 61, 63, 65, 69};
                        for (int op : ops) {
                            opEntries.add(new OpEntry(op, android.app.AppOpsManager.MODE_ALLOWED,
                                    0, 0, 0, 0, null));
                        }
//                        packageOpsList = mAppOpsService.getOpsForPackage(-1, packageName, AppOpsManager.sAlwaysShownOp);
//                        if (packageOpsList.size() == 1)
//                            opEntries.addAll(packageOpsList.get(0).getOps());
                        if (opEntries.size() > 0) {
                            Set<String> uniqueSet = new HashSet<>();
                            for (OpEntry opEntry : opEntries) {
                                String opName = AppOpsManager.opToName(opEntry.getOp());
                                if (uniqueSet.contains(opName)) continue;
                                AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry);
                                appDetailsItem.name = opName;
                                uniqueSet.add(opName);
                                appOpItems.add(appDetailsItem);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            final List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (!TextUtils.isEmpty(searchQuery)) {
                for (AppDetailsItem appDetailsItem: appOpItems)
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
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> usesPermissions;
    CopyOnWriteArrayList<AppDetailsPermissionItem> usesPermissionItems;
    private LiveData<List<AppDetailsItem>> getUsesPermissions() {
        if (usesPermissions == null) {
            usesPermissions = new MutableLiveData<>();
            loadUsesPermissions();
        }
        return usesPermissions;
    }

    public void setUsesPermission(String permissionName, boolean isGranted) {
        new Thread(() -> {
            AppDetailsPermissionItem permissionItem;
            for (int i = 0; i < usesPermissionItems.size(); ++i) {
                permissionItem = usesPermissionItems.get(i);
                if (permissionItem.name.equals(permissionName)) {
                    permissionItem.isGranted = isGranted;
                    usesPermissionItems.set(i, permissionItem);
                    break;
                }
            }
        }).start();
    }

    @SuppressLint("SwitchIntDef")
    private void loadUsesPermissions() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || usesPermissions == null) return;
            if (usesPermissionItems == null) {
                usesPermissionItems = new CopyOnWriteArrayList<>();
                if (packageInfo.requestedPermissions != null) {
                    for (int i = 0; i < packageInfo.requestedPermissions.length; ++i) {
                        try {
                            PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(
                                    packageInfo.requestedPermissions[i], PackageManager.GET_META_DATA);
                            AppDetailsPermissionItem appDetailsItem = new AppDetailsPermissionItem(permissionInfo);
                            appDetailsItem.name = packageInfo.requestedPermissions[i];
                            appDetailsItem.flags = packageInfo.requestedPermissionsFlags[i];
                            int basePermissionType;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                basePermissionType = permissionInfo.getProtection();
                            } else {
                                basePermissionType = permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                            }
                            appDetailsItem.isDangerous = basePermissionType == PermissionInfo.PROTECTION_DANGEROUS;
                            appDetailsItem.isGranted = (appDetailsItem.flags & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                            usesPermissionItems.add(appDetailsItem);
                        } catch (PackageManager.NameNotFoundException ignore) {}
                    }
                }
            }
            // Filter items
            final List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (!TextUtils.isEmpty(searchQuery)) {
                for (AppDetailsPermissionItem appDetailsItem: usesPermissionItems)
                    if (appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
            } else appDetailsItems.addAll(usesPermissionItems);
            Collections.sort(appDetailsItems, (o1, o2) -> {
                switch (sortOrderPermissions) {
                    case AppDetailsFragment.SORT_BY_NAME:
                        return o1.name.compareToIgnoreCase(o2.name);
                    case AppDetailsFragment.SORT_BY_DANGEROUS_PERMS:
                        return -Utils.compareBooleans(((AppDetailsPermissionItem) o1).isDangerous, ((AppDetailsPermissionItem) o2).isDangerous);
                    case AppDetailsFragment.SORT_BY_DENIED_PERMS:
                        return Utils.compareBooleans(((AppDetailsPermissionItem) o1).isGranted, ((AppDetailsPermissionItem) o2).isGranted);
                }
                return 0;
            });
            usesPermissions.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> permissions;
    private LiveData<List<AppDetailsItem>> getPermissions() {
        if (permissions == null) {
            permissions = new MutableLiveData<>();
            loadPermissions();
        }
        return permissions;
    }

    private void loadPermissions() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || permissions == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.permissions != null) {
                for(PermissionInfo permissionInfo: packageInfo.permissions) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(permissionInfo);
                    appDetailsItem.name = permissionInfo.name;
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
                Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
            }
            permissions.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> features;
    private LiveData<List<AppDetailsItem>> getFeatures() {
        if (features == null) {
            features = new MutableLiveData<>();
            loadFeatures();
        }
        return features;
    }

    private boolean bFi = false;
    public boolean isbFi() {
        return bFi;
    }

    private void loadFeatures() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || features == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.reqFeatures != null) {
                try {
                    Arrays.sort(packageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                } catch (NullPointerException e) {
                    for (FeatureInfo fi : packageInfo.reqFeatures) {
                        if (fi.name == null) fi.name = "OpenGL ES";
                        bFi = true;
                    }
                    Arrays.sort(packageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                }
                for(FeatureInfo featureInfo: packageInfo.reqFeatures) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(featureInfo);
                    appDetailsItem.name = featureInfo.name;
                    appDetailsItems.add(appDetailsItem);
                }
            }
            features.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> configurations;
    private LiveData<List<AppDetailsItem>> getConfigurations() {
        if (configurations == null) {
            configurations = new MutableLiveData<>();
            loadConfigurations();
        }
        return configurations;
    }

    private void loadConfigurations() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || configurations == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.configPreferences != null) {
                for(ConfigurationInfo configurationInfo: packageInfo.configPreferences) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(configurationInfo);
                    appDetailsItems.add(appDetailsItem);
                }
            }
            configurations.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> signatures;
    private LiveData<List<AppDetailsItem>> getSignatures() {
        if (signatures == null) {
            signatures = new MutableLiveData<>();
            loadSignatures();
        }
        return signatures;
    }

    private void loadSignatures() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || signatures == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            Signature[] signatureArray;
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo signingInfo = packageInfo.signingInfo;
                signatureArray = signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
            } else signatureArray = packageInfo.signatures;
            if (signatureArray != null) {
                for(Signature signature: signatureArray) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(signature);
                    appDetailsItems.add(appDetailsItem);
                }
            }
            signatures.postValue(appDetailsItems);
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> sharedLibraries;
    private LiveData<List<AppDetailsItem>> getSharedLibraries() {
        if (sharedLibraries == null) {
            sharedLibraries = new MutableLiveData<>();
            loadSharedLibraries();
        }
        return sharedLibraries;
    }

    private void loadSharedLibraries() {
        new Thread(() -> {
            setPackageInfo(false);
            if (packageInfo == null || sharedLibraries == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.applicationInfo.sharedLibraryFiles != null) {
                for(String sharedLibrary: packageInfo.applicationInfo.sharedLibraryFiles) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(sharedLibrary);
                    appDetailsItem.name = sharedLibrary;
                    appDetailsItems.add(appDetailsItem);
                }
            }
            sharedLibraries.postValue(appDetailsItems);
        }).start();
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
                        for (String packageName: packages) {
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
