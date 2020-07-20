package io.github.muntashirakon.AppManager.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
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
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.fragments.AppDetailsFragment;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.storage.compontents.TrackerComponentUtils;
import io.github.muntashirakon.AppManager.types.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.types.AppDetailsItem;
import io.github.muntashirakon.AppManager.types.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppDetailsViewModel extends AndroidViewModel {
    private PackageManager mPackageManager;
    private PackageInfo packageInfo;
    private String packageName;
    private Handler handler;
    private ComponentsBlocker blocker;

    private int flagSigningInfo;
    private int flagDisabledComponents;

    private @AppDetailsFragment.SortOrder int sortOrderComponents = AppDetailsFragment.SORT_BY_NAME;
    private @AppDetailsFragment.SortOrder int sortOrderAppOps = AppDetailsFragment.SORT_BY_NAME;
    private @AppDetailsFragment.SortOrder int sortOrderPermissions = AppDetailsFragment.SORT_BY_NAME;

    private String searchQuery;
    private boolean waitForBlocker;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        Log.d("ADVM", "New constructor called.");
        mPackageManager = application.getPackageManager();
        handler = new Handler(application.getMainLooper());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            flagDisabledComponents = PackageManager.MATCH_DISABLED_COMPONENTS;
        else flagDisabledComponents = PackageManager.GET_DISABLED_COMPONENTS;
        waitForBlocker = true;
    }

    @Override
    protected void onCleared() {
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
        super.onCleared();
    }

    public void setPackageName(String packageName) {
        if (this.packageName != null) return;
        Log.d("ADVM", "Package name is being set for " + packageName);
        this.packageName = packageName;
        new Thread(() -> {
            synchronized (ComponentsBlocker.class) {
                waitForBlocker = true;
                if (blocker != null) {
                    // To prevent commit if a mutable instance was created in the middle,
                    // set the instance read only again
                    blocker.setReadOnly();
                    blocker.close();
                }
                blocker = ComponentsBlocker.getInstance(getApplication(), packageName);
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
        synchronized (ComponentsBlocker.class) {
            if (packageName == null) return;
            waitForBlockerOrExit();
            final AtomicInteger newRuleApplicationStatus = new AtomicInteger();
            newRuleApplicationStatus.set(blocker.isRulesApplied() ? RULE_APPLIED : RULE_NOT_APPLIED);
            if (blocker.componentCount() == 0) newRuleApplicationStatus.set(RULE_NO_RULE);
            handler.post(() -> ruleApplicationStatus.postValue(newRuleApplicationStatus.get()));
        }
    }

    public void updateRulesForComponent(String componentName, RulesStorageManager.Type type) {
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
                if ((Boolean) AppPref.get(AppPref.PREF_GLOBAL_BLOCKING_ENABLED,
                        AppPref.TYPE_BOOLEAN) || blocker.isRulesApplied()) {
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
        // TODO: revoke all dangerous permissions and reload
        return false;
    }

    AppOpsService mAppOpsService;
    public boolean setAppOp(int op, int mode) {
        if (mAppOpsService == null) mAppOpsService = new AppOpsService(getApplication());
        try {
            // Set mode
            mAppOpsService.setMode(op, -1, packageName, mode);
            // Verify changes
            if (!mAppOpsService.checkOperation(op, -1, packageName).equals(AppOpsManager.modeToName(mode)))
                return false;
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
        // TODO: reset app ops and reload
        return false;
    }

    public boolean ignoreDangerousAppOps() {
        // TODO: ignore all dangerous app ops and reload
        return false;
    }

    public void applyRules() {
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
            case AppDetailsFragment.NONE:
        }
    }

    private void waitForBlockerOrExit() {
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
        handler.post(() -> {
            loadActivities();
            loadServices();
            loadReceivers();
            loadProviders();
        });
    }

    private void setPackageInfo() {
        if (packageName == null) return;
        // Wait for component blocker to appear
        synchronized (ComponentsBlocker.class) {
            waitForBlockerOrExit();
        }
        if (packageInfo != null) return;
        try {
            packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                    | PackageManager.GET_SHARED_LIBRARY_FILES);
        } catch (PackageManager.NameNotFoundException ignore) {
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                    appDetailsItem.name = activityInfo.targetActivity == null ? activityInfo.name : activityInfo.targetActivity;
                    appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                    appDetailsItem.isTracker = TrackerComponentUtils.isTracker(activityInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            handler.post(() -> activities.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.services != null) {
                for (ServiceInfo serviceInfo : packageInfo.services) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(serviceInfo);
                    appDetailsItem.name = serviceInfo.name;
                    appDetailsItem.isBlocked = blocker.hasComponent(serviceInfo.name);
                    appDetailsItem.isTracker = TrackerComponentUtils.isTracker(serviceInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            handler.post(() -> services.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.receivers != null) {
                for (ActivityInfo activityInfo : packageInfo.receivers) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                    appDetailsItem.name = activityInfo.name;
                    appDetailsItem.isBlocked = blocker.hasComponent(activityInfo.name);
                    appDetailsItem.isTracker = TrackerComponentUtils.isTracker(activityInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            handler.post(() -> receivers.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.providers != null) {
                for (ProviderInfo providerInfo : packageInfo.providers) {
                    AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(providerInfo);
                    appDetailsItem.name = providerInfo.name;
                    appDetailsItem.isBlocked = blocker.hasComponent(providerInfo.name);
                    appDetailsItem.isTracker = TrackerComponentUtils.isTracker(providerInfo.name);
                    if (TextUtils.isEmpty(searchQuery)
                            || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                        appDetailsItems.add(appDetailsItem);
                }
            }
            sortComponents(appDetailsItems);
            handler.post(() -> providers.postValue(appDetailsItems));
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
    AppOpsService appOpsService;
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
                }
            }
        }).start();
    }

    @SuppressLint("SwitchIntDef")
    private void loadAppOps() {
        new Thread(() -> {
            if (packageName == null) return;
            if (appOpItems == null) {
                appOpItems = new ArrayList<>();
                if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                    appOpsService = new AppOpsService(getApplication());
                    try {
                        List<AppOpsManager.PackageOps> packageOpsList = appOpsService.getOpsForPackage(-1, packageName, null);
                        List<AppOpsManager.OpEntry> opEntries = new ArrayList<>();
                        if (packageOpsList.size() == 1)
                            opEntries.addAll(packageOpsList.get(0).getOps());
                        packageOpsList = appOpsService.getOpsForPackage(-1, packageName, AppOpsManager.sAlwaysShownOp);
                        if (packageOpsList.size() == 1)
                            opEntries.addAll(packageOpsList.get(0).getOps());
                        if (opEntries.size() > 0) {
                            Set<String> uniqueSet = new HashSet<>();
                            for (AppOpsManager.OpEntry opEntry : opEntries) {
                                if (uniqueSet.contains(opEntry.getOpStr())) continue;
                                AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry);
                                appDetailsItem.name = opEntry.getOpStr();
                                uniqueSet.add(opEntry.getOpStr());
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
                        Integer o1Op = ((AppOpsManager.OpEntry) o1.vanillaItem).getOp();
                        Integer o2Op = ((AppOpsManager.OpEntry) o2.vanillaItem).getOp();
                        return o1Op.compareTo(o2Op);
                    case AppDetailsFragment.SORT_BY_DENIED_APP_OPS:
                        // A slight hack to sort it this way: ignore > foreground > deny > default[ > ask] > allow
                        return -((AppOpsManager.OpEntry) o1.vanillaItem).getMode().compareToIgnoreCase(((AppOpsManager.OpEntry) o2.vanillaItem).getMode());
                }
                return 0;
            });
            handler.post(() -> appOps.postValue(appDetailsItems));
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> usesPermissions;
    private LiveData<List<AppDetailsItem>> getUsesPermissions() {
        if (usesPermissions == null) {
            usesPermissions = new MutableLiveData<>();
            loadUsesPermissions();
        }
        return usesPermissions;
    }

    @SuppressLint("SwitchIntDef")
    private void loadUsesPermissions() {
        new Thread(() -> {
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
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
                        if (TextUtils.isEmpty(searchQuery)
                                || appDetailsItem.name.toLowerCase(Locale.ROOT).contains(searchQuery))
                            appDetailsItems.add(appDetailsItem);
                    } catch (PackageManager.NameNotFoundException ignore) {}
                }
            }
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
            handler.post(() -> usesPermissions.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
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
            handler.post(() -> permissions.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.reqFeatures != null) {
                try {
                    Arrays.sort(packageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                } catch (NullPointerException e) {
                    for (FeatureInfo fi : packageInfo.reqFeatures) {
                        if (fi.name == null) fi.name = "_MAJOR";
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
            handler.post(() -> features.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.configPreferences != null) {
                for(ConfigurationInfo configurationInfo: packageInfo.configPreferences) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(configurationInfo);
                    appDetailsItems.add(appDetailsItem);
                }
            }
            handler.post(() -> configurations.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            Signature[] signatureArray;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo signingInfo = packageInfo.signingInfo;
                signatureArray = signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
            } else {
                signatureArray = packageInfo.signatures;
            }
            if (signatureArray != null) {
                for(Signature signature: signatureArray) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(signature);
                    appDetailsItems.add(appDetailsItem);
                }
            }
            handler.post(() -> signatures.postValue(appDetailsItems));
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
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            if (packageInfo.applicationInfo.sharedLibraryFiles != null) {
                for(String sharedLibrary: packageInfo.applicationInfo.sharedLibraryFiles) {
                    AppDetailsItem appDetailsItem = new AppDetailsItem(sharedLibrary);
                    appDetailsItem.name = sharedLibrary;
                    appDetailsItems.add(appDetailsItem);
                }
            }
            handler.post(() -> sharedLibraries.postValue(appDetailsItems));
        }).start();
    }
}
