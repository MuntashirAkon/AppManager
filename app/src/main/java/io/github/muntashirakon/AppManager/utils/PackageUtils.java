// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.usage.IStorageStatsManager;
import android.app.usage.StorageStats;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.internal.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.FileStatus;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFiles;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public final class PackageUtils {
    public static final String TAG = PackageUtils.class.getSimpleName();

    public static final File PACKAGE_STAGING_DIRECTORY = new File("/data/local/tmp");

    public static final int flagSigningInfo;
    public static final int flagSigningInfoApk;
    public static final int flagDisabledComponents;
    public static final int flagMatchUninstalled;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            //noinspection deprecation
            flagSigningInfo = PackageManager.GET_SIGNATURES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flagSigningInfoApk = PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            //noinspection deprecation
            flagSigningInfoApk = PackageManager.GET_SIGNATURES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flagDisabledComponents = PackageManager.MATCH_DISABLED_COMPONENTS;
        } else {
            //noinspection deprecation
            flagDisabledComponents = PackageManager.GET_DISABLED_COMPONENTS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flagMatchUninstalled = PackageManager.MATCH_UNINSTALLED_PACKAGES;
        } else {
            //noinspection deprecation
            flagMatchUninstalled = PackageManager.GET_UNINSTALLED_PACKAGES;
        }
    }

    @NonNull
    public static ArrayList<UserPackagePair> getUserPackagePairs(@NonNull List<ApplicationItem> applicationItems) {
        ArrayList<UserPackagePair> userPackagePairList = new ArrayList<>();
        int currentUser = Users.myUserId();
        for (ApplicationItem item : applicationItems) {
            if (item.userHandles != null) {
                for (int userHandle : item.userHandles)
                    userPackagePairList.add(new UserPackagePair(item.packageName, userHandle));
            } else {
                userPackagePairList.add(new UserPackagePair(item.packageName, currentUser));
            }
        }
        return userPackagePairList;
    }

    /**
     * List all applications stored in App Manager database as well as from the system.
     *
     * @param executor    Retrieve applications from the system using the given thread instead of the current thread.
     * @param loadBackups Load/List backup metadata
     * @return List of applications, which could be the cached version if the executor parameter is {@code null}.
     */
    @WorkerThread
    @NonNull
    public static List<ApplicationItem> getInstalledOrBackedUpApplicationsFromDb(@NonNull Context context,
                                                                                 @Nullable ExecutorService executor,
                                                                                 boolean loadBackups) {
        List<ApplicationItem> applicationItems = new ArrayList<>();
        List<App> apps = AppManager.getDb().appDao().getAll();
        if (apps.size() == 0 || executor == null) {
            // Load app list for the first time
            Log.d(TAG, "Loading apps for the first time.");
            updateInstalledOrBackedUpApplications(context, loadBackups);
            apps = AppManager.getDb().appDao().getAll();
        } else {
            // Update list of apps safely in the background
            executor.submit(() -> updateInstalledOrBackedUpApplications(context, loadBackups));
        }
        HashMap<String, Backup> backups = BackupUtils.getAllLatestBackupMetadataFromDb();
        // Get application items from apps
        for (App app : apps) {
            ApplicationItem item = new ApplicationItem();
            item.packageName = app.packageName;
            int i;
            if (app.isInstalled) {
                if ((i = applicationItems.indexOf(item)) != -1) {
                    // Item already exists, add the user handle and continue
                    ApplicationItem oldItem = applicationItems.get(i);
                    oldItem.userHandles = ArrayUtils.appendInt(oldItem.userHandles, app.userId);
                    oldItem.isInstalled = true;
                    continue;
                } else {
                    // Item doesn't exist, add the user handle
                    item.userHandles = ArrayUtils.appendInt(item.userHandles, app.userId);
                    item.isInstalled = true;
                }
            } else {
                // App not installed but may be installed in other profiles
                if (applicationItems.contains(item)) {
                    // Item exists, use the previous status
                    continue;
                } else {
                    // Item doesn't exist, don't add user handle
                    item.isInstalled = false;
                }
            }
            if (backups.containsKey(item.packageName)) {
                item.backup = backups.get(item.packageName);
                backups.remove(item.packageName);
            }
            item.flags = app.flags;
            item.uid = app.uid;
            item.debuggable = (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            item.isUser = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            item.isDisabled = !app.isEnabled;
            item.label = app.packageLabel;
            item.sdk = app.sdk;
            item.versionName = app.versionName;
            item.versionCode = app.versionCode;
            item.sharedUserId = app.sharedUserId;
            item.sha = new Pair<>(app.certName, app.certAlgo);
            item.firstInstallTime = app.firstInstallTime;
            item.lastUpdateTime = app.lastUpdateTime;
            item.hasActivities = app.hasActivities;
            item.hasSplits = app.hasSplits;
            item.blockedCount = app.rulesCount;
            item.trackerCount = app.trackerCount;
            item.lastActionTime = app.lastActionTime;
            applicationItems.add(item);
        }
        // Add rest of the backups
        for (String packageName : backups.keySet()) {
            Backup backup = backups.get(packageName);
            if (backup == null) continue;
            ApplicationItem item = new ApplicationItem();
            item.packageName = backup.packageName;
            item.backup = backup;
            item.versionName = backup.versionName;
            item.versionCode = backup.versionCode;
            item.label = backup.label;
            item.firstInstallTime = backup.backupTime;
            item.lastUpdateTime = backup.backupTime;
            item.isUser = !backup.isSystem;
            item.isDisabled = false;
            item.isInstalled = false;
            item.hasSplits = backup.hasSplits;
            applicationItems.add(item);
        }
        return applicationItems;
    }

    @WorkerThread
    public static void updateInstalledOrBackedUpApplications(@NonNull Context context, boolean loadBackups) {
        HashMap<String, Backup> backups;
        if (loadBackups) {
            try {
                backups = BackupUtils.storeAllAndGetLatestBackupMetadata();
            } catch (IOException e) {
                e.printStackTrace();
                // Backups variable should always be non-null
                backups = new HashMap<>(0);
            }
        } else {
            backups = BackupUtils.getAllLatestBackupMetadataFromDb();
        }
        // Interrupt thread on request
        if (Thread.currentThread().isInterrupted()) return;

        List<App> newApps = new ArrayList<>();
        List<Integer> newAppHashes = new ArrayList<>();
        int[] userHandles = Users.getUsersIds();
        for (int userHandle : userHandles) {
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            List<PackageInfo> packageInfoList;
            try {
                packageInfoList = PackageManagerCompat.getInstalledPackages(flagSigningInfo
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | flagDisabledComponents | flagMatchUninstalled, userHandle);
            } catch (Exception e) {
                Log.e(TAG, "Could not retrieve package info list for user " + userHandle, e);
                continue;
            }
            ApplicationInfo applicationInfo;
            Backup backup;

            for (PackageInfo packageInfo : packageInfoList) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                applicationInfo = packageInfo.applicationInfo;
                App app = App.fromPackageInfo(context, packageInfo);
                backup = backups.get(applicationInfo.packageName);
                if (backup != null) {
                    backups.remove(applicationInfo.packageName);
                }
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                    app.rulesCount = cb.entryCount();
                }
                newApps.add(app);
                newAppHashes.add(app.getHashCode());
            }
        }
        // Add rest of the backup items, i.e., items that aren't installed
        for (Backup backup : backups.values()) {
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            if (backup == null) continue;
            App app = App.fromBackup(backup);
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                app.rulesCount = cb.entryCount();
            }
            newApps.add(app);
            newAppHashes.add(app.getHashCode());
        }
        // Add new and delete old items
        List<App> oldApps = AppManager.getDb().appDao().getAll();
        List<App> updatedApps = new ArrayList<>();
        ListIterator<App> iterator = oldApps.listIterator();
        while (iterator.hasNext()) {
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            App oldApp = iterator.next();
            int index = newApps.indexOf(oldApp);
            if (index != -1) {
                // DB already has this app
                if (!newAppHashes.contains(oldApp.getHashCode())) {
                    // Change detected, replace old with new
                    updatedApps.add(newApps.get(index));
                } // else no change between two versions, the app don't have to be updated or deleted
                iterator.remove();
                newApps.remove(index);
            } // else the app is new
        }
        AppManager.getDb().appDao().delete(oldApps);
        AppManager.getDb().appDao().insert(newApps);
        AppManager.getDb().appDao().insert(updatedApps);
        if (oldApps.size() > 0) {
            // Delete broadcast
            Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_REMOVED);
            intent.setPackage(context.getPackageName());
            intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(oldApps));
            context.sendBroadcast(intent);
        }
        if (newApps.size() > 0) {
            // New apps
            Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ADDED);
            intent.setPackage(context.getPackageName());
            intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(newApps));
            context.sendBroadcast(intent);
        }
        if (updatedApps.size() > 0) {
            // Altered apps
            Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED);
            intent.setPackage(context.getPackageName());
            intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(updatedApps));
            context.sendBroadcast(intent);
        }
    }

    @NonNull
    private static String[] getPackageNamesFromApps(@NonNull List<App> apps) {
        HashSet<String> packages = new HashSet<>(apps.size());
        for (App app : apps) {
            packages.add(app.packageName);
        }
        return packages.toArray(new String[0]);
    }

    @WorkerThread
    @Nullable
    public static PackageSizeInfo getPackageSizeInfo(Context context, String packageName, int userHandle, UUID storageUuid) {
        AtomicReference<PackageSizeInfo> packageSizeInfo = new AtomicReference<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            CountDownLatch waitForStats = new CountDownLatch(1);
            try {
                AppManager.getIPackageManager().getPackageSizeInfo(packageName, userHandle, new IPackageStatsObserver.Stub() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                        try {
                            if (succeeded) packageSizeInfo.set(new PackageSizeInfo(pStats));
                        } finally {
                            waitForStats.countDown();
                        }
                    }
                });
                waitForStats.await(5, TimeUnit.SECONDS);
            } catch (RemoteException | InterruptedException e) {
                Log.e(TAG, e);
            }
        } else {
            try {
                IStorageStatsManager storageStatsManager = IStorageStatsManager.Stub.asInterface(ProxyBinder
                        .getService(Context.STORAGE_STATS_SERVICE));
                @SuppressWarnings("JavaReflectionMemberAccess")
                Method getPackageSizeInfo = StorageManager.class.getMethod("convert", UUID.class);
                String uuidString = (String) getPackageSizeInfo.invoke(null, storageUuid);
                StorageStats storageStats = storageStatsManager.queryStatsForPackage(uuidString, packageName,
                        userHandle, context.getPackageName());
                packageSizeInfo.set(new PackageSizeInfo(packageName, storageStats, userHandle));
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RemoteException e) {
                Log.e(TAG, e);
            }
        }
        return packageSizeInfo.get();
    }

    @NonNull
    public static HashMap<String, RuleType> collectComponentClassNames(String packageName, @UserIdInt int userHandle) {
        try {
            PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                            | flagDisabledComponents | flagMatchUninstalled | PackageManager.GET_SERVICES,
                    userHandle);
            return collectComponentClassNames(packageInfo);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    @NonNull
    public static HashMap<String, RuleType> collectComponentClassNames(@NonNull PackageInfo packageInfo) {
        HashMap<String, RuleType> componentClasses = new HashMap<>();
        // Add activities
        if (packageInfo.activities != null) {
            for (ActivityInfo activityInfo : packageInfo.activities) {
                if (activityInfo.targetActivity != null)
                    componentClasses.put(activityInfo.targetActivity, RuleType.ACTIVITY);
                else componentClasses.put(activityInfo.name, RuleType.ACTIVITY);
            }
        }
        // Add others
        if (packageInfo.services != null) {
            for (ComponentInfo componentInfo : packageInfo.services)
                componentClasses.put(componentInfo.name, RuleType.SERVICE);
        }
        if (packageInfo.receivers != null) {
            for (ComponentInfo componentInfo : packageInfo.receivers)
                componentClasses.put(componentInfo.name, RuleType.RECEIVER);
        }
        if (packageInfo.providers != null) {
            for (ComponentInfo componentInfo : packageInfo.providers)
                componentClasses.put(componentInfo.name, RuleType.PROVIDER);
        }
        return componentClasses;
    }

    @NonNull
    public static HashMap<String, RuleType> getFilteredComponents(String packageName, @UserIdInt int userHandle, String[] signatures) {
        HashMap<String, RuleType> filteredComponents = new HashMap<>();
        HashMap<String, RuleType> components = collectComponentClassNames(packageName, userHandle);
        for (String componentName : components.keySet()) {
            for (String signature : signatures) {
                if (componentName.startsWith(signature) || componentName.contains(signature)) {
                    filteredComponents.put(componentName, components.get(componentName));
                }
            }
        }
        return filteredComponents;
    }

    @NonNull
    public static Collection<Integer> getFilteredAppOps(String packageName, @UserIdInt int userHandle, @NonNull int[] appOps, int mode) {
        List<Integer> filteredAppOps = new ArrayList<>();
        AppOpsService appOpsService = new AppOpsService();
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        for (int appOp : appOps) {
            try {
                if (appOpsService.checkOperation(appOp, uid, packageName) != mode) {
                    filteredAppOps.add(appOp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filteredAppOps;
    }

    @NonNull
    public static HashMap<String, RuleType> getUserDisabledComponentsForPackage(String packageName, @UserIdInt int userHandle) {
        HashMap<String, RuleType> componentClasses = collectComponentClassNames(packageName, userHandle);
        HashMap<String, RuleType> disabledComponents = new HashMap<>();
        PackageManager pm = AppManager.getContext().getPackageManager();
        for (String componentName : componentClasses.keySet()) {
            if (isComponentDisabledByUser(pm, packageName, componentName))
                disabledComponents.put(componentName, componentClasses.get(componentName));
        }
        disabledComponents.putAll(ComponentUtils.getIFWRulesForPackage(packageName));
        return disabledComponents;
    }

    @SuppressLint("SwitchIntDef")
    public static boolean isComponentDisabledByUser(@NonNull PackageManager pm, @NonNull String packageName, @NonNull String componentClassName) {
        ComponentName componentName = new ComponentName(packageName, componentClassName);
        switch (pm.getComponentEnabledSetting(componentName)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                return false;
        }
    }

    @Nullable
    public static String[] getPermissionsForPackage(String packageName, @UserIdInt int userHandle)
            throws PackageManager.NameNotFoundException, RemoteException {
        PackageInfo info = PackageManagerCompat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userHandle);
        return info.requestedPermissions;
    }

    @NonNull
    public static String getPackageLabel(@NonNull PackageManager pm, String packageName) {
        try {
            @SuppressLint("WrongConstant")
            ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, flagMatchUninstalled);
            return pm.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return packageName;
    }

    @NonNull
    public static CharSequence getPackageLabel(@NonNull PackageManager pm, String packageName, int userHandle) {
        try {
            ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(packageName, 0, userHandle);
            return applicationInfo.loadLabel(pm);
        } catch (Exception ignore) {
        }
        return packageName;
    }

    @Nullable
    public static ArrayList<String> packagesToAppLabels(@NonNull PackageManager pm, @Nullable List<String> packages, List<Integer> userHandles) {
        if (packages == null) return null;
        ArrayList<String> appLabels = new ArrayList<>();
        int i = 0;
        for (String packageName : packages) {
            appLabels.add(PackageUtils.getPackageLabel(pm, packageName, userHandles.get(i)).toString());
            ++i;
        }
        return appLabels;
    }

    public static boolean isInstalled(@NonNull PackageManager packageManager, String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return applicationInfo != null;
    }

    public static int getAppUid(@NonNull PackageManager packageManager, String packageName) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return 0;
    }

    public static int getAppUid(@NonNull UserPackagePair pair) {
        try {
            return PackageManagerCompat.getApplicationInfo(pair.getPackageName(), 0, pair.getUserHandle()).uid;
        } catch (Exception ignore) {
        }
        return -1;
    }

    @NonNull
    public static String getSourceDir(@NonNull ApplicationInfo applicationInfo) {
        String sourceDir = new File(applicationInfo.publicSourceDir).getParent(); // or applicationInfo.sourceDir
        if (sourceDir == null) {
            throw new RuntimeException("Application source directory cannot be empty");
        }
        return sourceDir;
    }

    @NonNull
    public static String[] getDataDirs(@NonNull ApplicationInfo applicationInfo, boolean loadInternal,
                                       boolean loadExternal, boolean loadMediaObb) {
        ArrayList<String> dataDirs = new ArrayList<>();
        if (applicationInfo.dataDir == null) {
            throw new RuntimeException("Data directory cannot be empty.");
        }
        if (loadInternal) {
            dataDirs.add(applicationInfo.dataDir);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    !applicationInfo.dataDir.equals(applicationInfo.deviceProtectedDataDir)) {
                dataDirs.add(applicationInfo.deviceProtectedDataDir);
            }
        }
        int userHandle = Users.getUserId(applicationInfo.uid);
        OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userHandle);
        if (loadExternal) {
            ProxyFile[] externalFiles = ue.buildExternalStorageAppDataDirs(applicationInfo.packageName);
            for (ProxyFile externalFile : externalFiles) {
                if (externalFile != null && externalFile.exists())
                    dataDirs.add(externalFile.getAbsolutePath());
            }
        }
        if (loadMediaObb) {
            List<ProxyFile> externalFiles = new ArrayList<>();
            externalFiles.addAll(Arrays.asList(ue.buildExternalStorageAppMediaDirs(applicationInfo.packageName)));
            externalFiles.addAll(Arrays.asList(ue.buildExternalStorageAppObbDirs(applicationInfo.packageName)));
            for (ProxyFile externalFile : externalFiles) {
                if (externalFile != null && externalFile.exists())
                    dataDirs.add(externalFile.getAbsolutePath());
            }
        }
        return dataDirs.toArray(new String[0]);
    }

    public static String getHiddenCodePathOrDefault(String packageName, String defaultPath) {
        Runner.Result result = Runner.runCommand(RunnerUtils.CMD_PM + " dump " + packageName + " | grep codePath");
        if (result.isSuccessful()) {
            List<String> paths = result.getOutputAsList();
            if (paths.size() > 0) {
                // Get only the last path
                String codePath = paths.get(paths.size() - 1);
                int start = codePath.indexOf('=');
                if (start != -1) return codePath.substring(start + 1);
            }
        }
        return new File(defaultPath).getParent();
    }

    @NonNull
    public static List<Integer> getAppOpModes() {
        List<Integer> appOpModes = new ArrayList<>();
        appOpModes.add(AppOpsManager.MODE_ALLOWED);
        appOpModes.add(AppOpsManager.MODE_IGNORED);
        appOpModes.add(AppOpsManager.MODE_ERRORED);
        appOpModes.add(AppOpsManager.MODE_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpModes.add(AppOpsManager.MODE_FOREGROUND);
        }
        if (MiuiUtils.isMiui()) {
            appOpModes.add(AppOpsManager.MODE_ASK);
        }
        return appOpModes;
    }

    @NonNull
    public static List<Integer> getAppOps() {
        List<Integer> appOps = new ArrayList<>();
        for (int i = 0; i < AppOpsManager._NUM_OP; ++i) {
            appOps.add(i);
        }
        if (MiuiUtils.isMiui()) {
            for (int op = AppOpsManager.MIUI_OP_START + 1; op < AppOpsManager.MIUI_OP_END; ++op) {
                appOps.add(op);
            }
        }
        return appOps;
    }

    @NonNull
    public static CharSequence[] getAppOpModeNames(@NonNull List<Integer> appOpModes) {
        CharSequence[] appOpModeNames = new CharSequence[appOpModes.size()];
        for (int i = 0; i < appOpModes.size(); ++i) {
            appOpModeNames[i] = AppOpsManager.modeToName(appOpModes.get(i));
        }
        return appOpModeNames;
    }

    @NonNull
    public static CharSequence[] getAppOpNames(@NonNull List<Integer> appOps) {
        CharSequence[] appOpNames = new CharSequence[appOps.size()];
        for (int i = 0; i < appOps.size(); ++i) {
            appOpNames[i] = AppOpsManager.opToName(appOps.get(i));
        }
        return appOpNames;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    public static Signature[] getSigningInfo(@NonNull PackageInfo packageInfo, boolean isExternal) {
        if (!isExternal || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo == null) {
                    if (!isExternal) {
                        return null;
                    } // else Could be a false-negative
                } else return signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
            }
        }
        // Is an external app
        if (packageInfo.signatures == null) {
            // Could be a false-negative, try with apksig library
            String apkPath = packageInfo.applicationInfo.publicSourceDir;
            if (apkPath != null) {
                Log.w(TAG, "getSigningInfo: Using fallback method");
                return packageInfo.signatures = getSigningInfo(new File(apkPath));
            }
        }
        return packageInfo.signatures;
    }

    @Nullable
    public static Signature[] getSigningInfo(@NonNull File apkFile) {
        ApkVerifier.Builder builder = new ApkVerifier.Builder(apkFile);
        ApkVerifier apkVerifier = builder.build();
        ApkVerifier.Result apkVerifierResult;
        try {
            apkVerifierResult = apkVerifier.verify();
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
            Log.e(TAG, e);
            return null;
        }
        // Get signer certificates
        List<X509Certificate> certificates = apkVerifierResult.getSignerCertificates();
        if (certificates == null || certificates.size() == 0) return null;
        List<Signature> signatures = new ArrayList<>(certificates.size());
        for (X509Certificate certificate : certificates) {
            try {
                signatures.add(new Signature(certificate.getEncoded()));
            } catch (CertificateEncodingException e) {
                return signatures.toArray(new Signature[0]);
            }
        }
        return signatures.toArray(new Signature[0]);
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(@NonNull PackageInfo packageInfo) {
        return getSigningCertSha256Checksum(packageInfo, false);
    }

    public static boolean isSignatureDifferent(@NonNull PackageInfo newPkgInfo, @NonNull PackageInfo oldPkgInfo) {
        String[] newChecksums = getSigningCertSha256Checksum(newPkgInfo, true);
        List<String> oldChecksums = new ArrayList<>(Arrays.asList(getSigningCertSha256Checksum(oldPkgInfo)));
        // Signature is different if the number of signatures don't match
        if (newChecksums.length != oldChecksums.size()) return true;
        for (String newChecksum : newChecksums) {
            oldChecksums.remove(newChecksum);
        }
        // Old checksums should contain no values if the checksums are the same
        return oldChecksums.size() != 0;
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(PackageInfo packageInfo, boolean isExternal) {
        return getSigningCertChecksums(DigestUtils.SHA_256, packageInfo, isExternal);
    }

    @NonNull
    public static String[] getSigningCertChecksums(@DigestUtils.Algorithm String algo,
                                                   PackageInfo packageInfo, boolean isExternal) {
        Signature[] signatureArray = getSigningInfo(packageInfo, isExternal);
        ArrayList<String> checksums = new ArrayList<>();
        if (signatureArray != null) {
            for (Signature signature : signatureArray) {
                checksums.add(DigestUtils.getHexDigest(algo, signature.toByteArray()));
            }
        }
        return checksums.toArray(new String[0]);
    }

    @NonNull
    public static Spannable getSigningCertificateInfo(@NonNull Context ctx, @Nullable X509Certificate certificate)
            throws CertificateEncodingException {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (certificate == null) return builder;
        byte[] certBytes = certificate.getEncoded();
        builder.append(getPrimaryText(ctx, ctx.getString(R.string.subject) + ": "))
                .append(certificate.getSubjectX500Principal().getName()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.issuer) + ": "))
                .append(certificate.getIssuerX500Principal().getName()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.issued_date) + ": "))
                .append(certificate.getNotBefore().toString()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.expiry_date) + ": "))
                .append(certificate.getNotAfter().toString()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.type) + ": "))
                .append(certificate.getType()).append(", ")
                .append(getPrimaryText(ctx, ctx.getString(R.string.version) + ": "))
                .append(String.valueOf(certificate.getVersion())).append(", ")
                .append(getPrimaryText(ctx, ctx.getString(R.string.validity) + ": "));
        try {
            certificate.checkValidity();
            builder.append(ctx.getString(R.string.valid));
        } catch (CertificateExpiredException e) {
            builder.append(ctx.getString(R.string.expired));
        } catch (CertificateNotYetValidException e) {
            builder.append(ctx.getString(R.string.not_yet_valid));
        }
        builder.append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.serial_no) + ": "))
                .append(Utils.bytesToHex(certificate.getSerialNumber().toByteArray())).append("\n");
        // Checksums
        builder.append(getTitleText(ctx, ctx.getString(R.string.checksums))).append("\n");
        Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
        for (Pair<String, String> digest : digests) {
            builder.append(getPrimaryText(ctx, digest.first + ": ")).append(digest.second).append("\n");
        }
        // Signature
        builder.append(getTitleText(ctx, ctx.getString(R.string.app_signing_signature)))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.algorithm) + ": "))
                .append(certificate.getSigAlgName()).append("\n")
                .append(getPrimaryText(ctx, "OID: "))
                .append(certificate.getSigAlgOID()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.app_signing_signature) + ": "))
                .append(Utils.bytesToHex(certificate.getSignature())).append("\n");
        // Public key used by Google: https://github.com/google/conscrypt
        // 1. X509PublicKey (PublicKey)
        // 2. OpenSSLRSAPublicKey (RSAPublicKey)
        // 3. OpenSSLECPublicKey (ECPublicKey)
        PublicKey publicKey = certificate.getPublicKey();
        builder.append(getTitleText(ctx, ctx.getString(R.string.public_key)))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.algorithm) + ": "))
                .append(publicKey.getAlgorithm()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.format) + ": "))
                .append(publicKey.getFormat());
        if (publicKey instanceof RSAPublicKey) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            builder.append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_exponent) + ": "))
                    .append(rsaPublicKey.getPublicExponent().toString()).append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_modulus) + ": "))
                    .append(Utils.bytesToHex(rsaPublicKey.getModulus().toByteArray()));
        } else if (publicKey instanceof ECPublicKey) {
            ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
            builder.append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.dsa_affine_x) + ": "))
                    .append(ecPublicKey.getW().getAffineX().toString()).append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.dsa_affine_y) + ": "))
                    .append(ecPublicKey.getW().getAffineY().toString());
        }
        // TODO(5/10/20): Add description for each extensions
        Set<String> critSet = certificate.getCriticalExtensionOIDs();
        if (critSet != null && !critSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.critical_exts)));
            for (String oid : critSet) {
                builder.append("\n- ")
                        .append(getPrimaryText(ctx, oid + ": "))
                        .append(Utils.bytesToHex(certificate.getExtensionValue(oid)));
            }
        }
        Set<String> nonCritSet = certificate.getNonCriticalExtensionOIDs();
        if (nonCritSet != null && !nonCritSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.non_critical_exts)));
            for (String oid : nonCritSet) {
                builder.append("\n- ")
                        .append(getPrimaryText(ctx, oid + ": "))
                        .append(Utils.bytesToHex(certificate.getExtensionValue(oid)));
            }
        }
        return builder;
    }

    @NonNull
    public static Spannable getApkVerifierInfo(@Nullable ApkVerifier.Result result, Context ctx) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (result == null) return builder;
        int colorRed = ContextCompat.getColor(ctx, R.color.red);
        int colorGreen = ContextCompat.getColor(ctx, R.color.stopped);
        int warnCount = 0;
        List<CharSequence> errors = new ArrayList<>();
        for (ApkVerifier.IssueWithParams err : result.getErrors()) {
            errors.add(getColoredText(err.toString(), colorRed));
        }
        warnCount += result.getWarnings().size();
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeIgnoredSigners()) {
            String name = signer.getName();
            for (ApkVerifier.IssueWithParams err : signer.getErrors()) {
                errors.add(getColoredText(new SpannableStringBuilder(getBoldString(name + ": ")).append(err.toString()), colorRed));
            }
            warnCount += signer.getWarnings().size();
        }
        if (result.isVerified()) {
            if (warnCount == 0) {
                builder.append(getColoredText(getTitleText(ctx, "\u2714 " +
                        ctx.getString(R.string.verified)), colorGreen));
            } else {
                builder.append(getColoredText(getTitleText(ctx, "\u2714 " + ctx.getResources()
                        .getQuantityString(R.plurals.verified_with_warning, warnCount, warnCount)), colorGreen));
            }
            if (result.isSourceStampVerified()) {
                builder.append("\n\u2714 ").append(ctx.getString(R.string.source_stamp_verified));
            }
            List<CharSequence> sigSchemes = new LinkedList<>();
            if (result.isVerifiedUsingV1Scheme()) sigSchemes.add("v1");
            if (result.isVerifiedUsingV2Scheme()) sigSchemes.add("v2");
            if (result.isVerifiedUsingV3Scheme()) sigSchemes.add("v3");
            if (result.isVerifiedUsingV4Scheme()) sigSchemes.add("v4");
            builder.append("\n").append(getPrimaryText(ctx, ctx.getResources()
                    .getQuantityString(R.plurals.app_signing_signature_schemes_pl, sigSchemes.size()) + ": "));
            builder.append(TextUtils.joinSpannable(", ", sigSchemes));
        } else {
            builder.append(getColoredText(getTitleText(ctx, "\u2718 " + ctx.getString(R.string.not_verified)), colorRed));
        }
        builder.append("\n");
        // If there are errors, no certificate info will be loaded
        builder.append(TextUtils.joinSpannable("\n", errors)).append("\n");
        return builder;
    }

    public static void ensurePackageStagingDirectoryPrivileged() throws ErrnoException, RemoteException {
        File psd = new ProxyFile(PACKAGE_STAGING_DIRECTORY);
        if (!psd.isDirectory()) {
            // Recreate directory
            File parent = psd.getParentFile();
            if (parent == null) throw new IllegalStateException("Parent should be /data/local");
            if (psd.exists()) psd.delete();
            psd.mkdir();
        }
        // Change permission
        FileStatus stat = ProxyFiles.stat(psd);
        if ((stat.st_mode & 0x1FF) != 0711) {
            ProxyFiles.chmod(psd, 0711);
        }
        // Change UID, GID
        if (stat.st_uid != 2000 || stat.st_gid != 2000) {
            ProxyFiles.chown(psd, 2000, 2000);
        }
    }

    @NonNull
    public static String ensurePackageStagingDirectoryCommand() {
        String psd = PACKAGE_STAGING_DIRECTORY.getAbsolutePath();
        return String.format("( [ -d  %s ] || ( rm %s; mkdir %s && chmod 771 %s && chown 2000:2000 %s ) )", psd, psd, psd, psd, psd);
    }
}
