package io.github.muntashirakon.AppManager.viewmodels;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.fragments.AppDetailsFragment;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.storage.compontents.TrackerComponentUtils;
import io.github.muntashirakon.AppManager.types.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.types.AppDetailsItem;
import io.github.muntashirakon.AppManager.types.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class AppDetailsViewModel extends AndroidViewModel {
    private PackageManager mPackageManager;
    private PackageInfo packageInfo;
    private String packageName;
    private Handler handler;

    public AppDetailsViewModel(@NonNull Application application) {
        super(application);
        mPackageManager = application.getPackageManager();
        handler = new Handler(application.getMainLooper());
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    public String getPackageName() {
        return packageName;
    }

    public void setPackageInfo() {
        if (packageName == null) return;
        try {
            int apiCompatFlags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                apiCompatFlags = PackageManager.GET_SIGNING_CERTIFICATES;
            else apiCompatFlags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                apiCompatFlags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            else apiCompatFlags |= PackageManager.GET_DISABLED_COMPONENTS;

            packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | apiCompatFlags | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        } catch (PackageManager.NameNotFoundException ignore) {}
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

    MutableLiveData<List<AppDetailsItem>> activities;
    private LiveData<List<AppDetailsItem>> getActivities() {
        if (activities == null) {
            activities = new MutableLiveData<>();
            loadActivities();
        }
        return activities;
    }

    private void loadActivities() {
        new Thread(() -> {
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), packageName)) {
                if (packageInfo.activities != null) {
                    for (ActivityInfo activityInfo : packageInfo.activities) {
                        AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                        appDetailsItem.name = activityInfo.targetActivity == null ? activityInfo.name : activityInfo.targetActivity;
                        appDetailsItem.isBlocked = cb.hasComponent(activityInfo.name);
                        appDetailsItem.isTracker = TrackerComponentUtils.isTracker(activityInfo.name);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
            }
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
        new Thread(() -> {
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), packageName)) {
                if (packageInfo.services != null) {
                    for (ServiceInfo serviceInfo : packageInfo.services) {
                        AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(serviceInfo);
                        appDetailsItem.name = serviceInfo.name;
                        appDetailsItem.isBlocked = cb.hasComponent(serviceInfo.name);
                        appDetailsItem.isTracker = TrackerComponentUtils.isTracker(serviceInfo.name);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
            }
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
        new Thread(() -> {
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), packageName)) {
                if (packageInfo.receivers != null) {
                    for (ActivityInfo activityInfo : packageInfo.receivers) {
                        AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(activityInfo);
                        appDetailsItem.name = activityInfo.name;
                        appDetailsItem.isBlocked = cb.hasComponent(activityInfo.name);
                        appDetailsItem.isTracker = TrackerComponentUtils.isTracker(activityInfo.name);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
            }
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
        new Thread(() -> {
            setPackageInfo();
            if (packageInfo == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), packageName)) {
                if (packageInfo.providers != null) {
                    for (ProviderInfo providerInfo : packageInfo.providers) {
                        AppDetailsComponentItem appDetailsItem = new AppDetailsComponentItem(providerInfo);
                        appDetailsItem.name = providerInfo.name;
                        appDetailsItem.isBlocked = cb.hasComponent(providerInfo.name);
                        appDetailsItem.isTracker = TrackerComponentUtils.isTracker(providerInfo.name);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
            }
            handler.post(() -> providers.postValue(appDetailsItems));
        }).start();
    }

    MutableLiveData<List<AppDetailsItem>> appOps;
    AppOpsService appOpsService;
    private LiveData<List<AppDetailsItem>> getAppOps() {
        if (appOps == null) {
            appOps = new MutableLiveData<>();
            loadAppOps();
        }
        return appOps;
    }

    private void loadAppOps() {
        new Thread(() -> {
            if (packageName == null) return;
            List<AppDetailsItem> appDetailsItems = new ArrayList<>();
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
                            appDetailsItems.add(appDetailsItem);
                            uniqueSet.add(opEntry.getOpStr());
                        }
                    }
                } catch (Exception ignored) {}
            }
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
                        appDetailsItems.add(appDetailsItem);
                    } catch (PackageManager.NameNotFoundException ignore) {}
                }
            }
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
