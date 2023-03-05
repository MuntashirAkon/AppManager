// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

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
import android.content.pm.IPackageManager;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.os.storage.StorageManagerHidden;
import android.system.ErrnoException;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.misc.OidMap;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

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
        int currentUser = UserHandleHidden.myUserId();
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
        HashMap<String, ApplicationItem> applicationItems = new HashMap<>();
        AppDb appDb = new AppDb();
        List<App> apps = appDb.getAllApplications();
        boolean loadInBackground = !(apps.size() == 0 || executor == null);
        if (!loadInBackground) {
            // Load app list for the first time
            Log.d(TAG, "Loading apps for the first time.");
            appDb.loadInstalledOrBackedUpApplications(context);
            apps = appDb.getAllApplications();
        }
        Map<String, Backup> backups = appDb.getBackups(false);
        int thisUser = UserHandleHidden.myUserId();
        // Get application items from apps
        for (App app : apps) {
            ApplicationItem item;
            ApplicationItem oldItem = applicationItems.get(app.packageName);
            if (app.isInstalled) {
                boolean newItem = oldItem == null || !oldItem.isInstalled;
                if (oldItem != null) {
                    // Item already exists
                    item = oldItem;
                } else {
                    // Item doesn't exist
                    item = new ApplicationItem();
                    applicationItems.put(app.packageName, item);
                    item.packageName = app.packageName;
                }
                item.userHandles = ArrayUtils.appendInt(item.userHandles, app.userId);
                item.isInstalled = true;
                item.openCount += app.openCount;
                item.screenTime += app.screenTime;
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime;
                }
                item.hasKeystore |= app.hasKeystore;
                item.usesSaf |= app.usesSaf;
                if (app.ssaid != null) {
                    item.ssaid = app.ssaid;
                }
                item.totalSize += app.codeSize + app.dataSize;
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage;
                if (!newItem && app.userId != thisUser) {
                    // This user has the highest priority
                    continue;
                }
            } else {
                // App not installed but may be installed in other profiles
                if (oldItem != null) {
                    // Item exists, use the previous status
                    continue;
                } else {
                    // Item doesn't exist, don't add user handle
                    item = new ApplicationItem();
                    item.packageName = app.packageName;
                    applicationItems.put(app.packageName, item);
                    item.isInstalled = false;
                    item.hasKeystore |= app.hasKeystore;
                }
            }
            item.backup = backups.remove(item.packageName);
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
        }
        // Add rest of the backups
        for (String packageName : backups.keySet()) {
            Backup backup = backups.get(packageName);
            if (backup == null) continue;
            ApplicationItem item = new ApplicationItem();
            item.packageName = backup.packageName;
            applicationItems.put(backup.packageName, item);
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
            item.hasKeystore = backup.hasKeyStore;
        }
        if (loadInBackground) {
            // Update list of apps safely in the background.
            // We need to do this here to avoid locks in AppDb
            executor.submit(() -> {
                if (loadBackups) {
                    appDb.loadInstalledOrBackedUpApplications(context);
                } else appDb.updateApplications(context);
            });
        }
        return new ArrayList<>(applicationItems.values());
    }

    @NonNull
    public static List<PackageInfo> getAllPackages(int flags) {
        List<PackageInfo> applicationInfoList = new ArrayList<>();
        for (int userId : Users.getUsersIds()) {
            try {
                applicationInfoList.addAll(PackageManagerCompat.getInstalledPackages(flags, userId));
            } catch (RemoteException ignore) {
            }
        }
        return applicationInfoList;
    }

    @NonNull
    public static List<ApplicationInfo> getAllApplications(int flags) {
        List<ApplicationInfo> applicationInfoList = new ArrayList<>();
        for (int userId : Users.getUsersIds()) {
            try {
                applicationInfoList.addAll(PackageManagerCompat.getInstalledApplications(flags, userId));
            } catch (RemoteException ignore) {
            }
        }
        return applicationInfoList;
    }

    @WorkerThread
    @Nullable
    public static PackageSizeInfo getPackageSizeInfo(@NonNull Context context, @NonNull String packageName,
                                                     @UserIdInt int userHandle, @Nullable UUID storageUuid) {
        AtomicReference<PackageSizeInfo> packageSizeInfo = new AtomicReference<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            CountDownLatch waitForStats = new CountDownLatch(1);
            try {
                IPackageManager pm;
                if (UserHandleHidden.myUserId() == userHandle) {
                    // Since GET_PACKAGE_SIZE is a normal permission, there's no need to use a privileged service
                    pm = PackageManagerCompat.getUnprivilegedPackageManager();
                } else {
                    // May return SecurityException in the ADB mode
                    pm = PackageManagerCompat.getPackageManager();
                }
                pm.getPackageSizeInfo(packageName, userHandle,
                        new IPackageStatsObserver.Stub() {
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
            } catch (RemoteException | InterruptedException | SecurityException e) {
                Log.e(TAG, e);
            }
        } else if (PermissionUtils.hasUsageStatsPermission(context)) {
            try {
                IStorageStatsManager storageStatsManager = IStorageStatsManager.Stub.asInterface(ProxyBinder
                        .getService(Context.STORAGE_STATS_SERVICE));
                String uuidString = storageUuid != null ? StorageManagerHidden.convert(storageUuid) : null;
                StorageStats storageStats = storageStatsManager.queryStatsForPackage(uuidString, packageName,
                        userHandle, context.getPackageName());
                packageSizeInfo.set(new PackageSizeInfo(packageName, storageStats, userHandle));
            } catch (Throwable e) {
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
    public static HashMap<String, RuleType> collectComponentClassNames(@Nullable PackageInfo packageInfo) {
        HashMap<String, RuleType> componentClasses = new HashMap<>();
        if (packageInfo == null) return componentClasses;
        // Add activities
        if (packageInfo.activities != null) {
            for (ActivityInfo activityInfo : packageInfo.activities) {
                if (activityInfo.targetActivity != null) {
                    // We need real class name exclusively
                    componentClasses.put(activityInfo.targetActivity, RuleType.ACTIVITY);
                }
                componentClasses.put(activityInfo.name, RuleType.ACTIVITY);
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
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat(ContextUtils.getContext());
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        for (int appOp : appOps) {
            try {
                if (appOpsManager.checkOperation(appOp, uid, packageName) != mode) {
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
    public static String[] getPermissionsForPackage(String packageName, @UserIdInt int userId)
            throws PackageManager.NameNotFoundException, RemoteException {
        PackageInfo info = PackageManagerCompat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
        return info.requestedPermissions;
    }

    @Nullable
    public static Intent getLaunchIntentForPackage(@NonNull Context context,
                                                   @NonNull String packageName,
                                                   @UserIdInt int userId) {
        if (userId == UserHandleHidden.myUserId()) {
            return context.getPackageManager().getLaunchIntentForPackage(packageName);
        }
        UserHandle userHandle = Users.getUserHandle(userId);
        if (userHandle != null) {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            try {
                if (!launcherApps.isPackageEnabled(packageName, userHandle)) {
                    return null;
                }
                List<LauncherActivityInfo> activityInfoList = launcherApps.getActivityList(packageName, userHandle);
                if (activityInfoList.size() > 0) {
                    Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    launchIntent.setComponent(activityInfoList.get(0).getComponentName());
                    return launchIntent;
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        return null;
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
    public static ArrayList<CharSequence> packagesToAppLabels(@NonNull PackageManager pm, @Nullable List<String> packages, List<Integer> userHandles) {
        if (packages == null) return null;
        ArrayList<CharSequence> appLabels = new ArrayList<>();
        int i = 0;
        for (String packageName : packages) {
            appLabels.add(PackageUtils.getPackageLabel(pm, packageName, userHandles.get(i)).toString());
            ++i;
        }
        return appLabels;
    }

    public static int getAppUid(@Nullable ApplicationInfo applicationInfo) {
        return applicationInfo != null ? applicationInfo.uid : 0;
    }

    public static int getAppUid(@NonNull UserPackagePair pair) {
        try {
            return PackageManagerCompat.getApplicationInfo(pair.getPackageName(), 0, pair.getUserHandle()).uid;
        } catch (Exception ignore) {
        }
        return -1;
    }

    public static boolean isTestOnlyApp(@NonNull ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
    }

    @NonNull
    public static String getSourceDir(@NonNull ApplicationInfo applicationInfo) {
        String sourceDir = new File(applicationInfo.publicSourceDir).getParent(); // or applicationInfo.sourceDir
        if (sourceDir == null) {
            throw new RuntimeException("Application source directory cannot be empty");
        }
        return sourceDir;
    }

    @Nullable
    public static String getHiddenCodePathOrDefault(@NonNull ApplicationInfo applicationInfo) {
        Runner.Result result = Runner.runCommand(RunnerUtils.CMD_PM + " dump " + applicationInfo.packageName + " | grep codePath");
        if (result.isSuccessful()) {
            List<String> paths = result.getOutputAsList();
            if (paths.size() > 0) {
                // Get only the last path
                String codePath = paths.get(paths.size() - 1);
                int start = codePath.indexOf('=');
                if (start != -1) return codePath.substring(start + 1).trim();
            }
        }
        if (applicationInfo.sourceDir == null) {
            return null;
        }
        if (applicationInfo.packageName.equals("android")) {
            return applicationInfo.sourceDir;
        }
        return new File(applicationInfo.sourceDir).getParent();
    }

    @NonNull
    public static CharSequence[] getAppOpModeNames(@NonNull List<Integer> appOpModes) {
        CharSequence[] appOpModeNames = new CharSequence[appOpModes.size()];
        for (int i = 0; i < appOpModes.size(); ++i) {
            appOpModeNames[i] = AppOpsManagerCompat.modeToName(appOpModes.get(i));
        }
        return appOpModeNames;
    }

    @NonNull
    public static CharSequence[] getAppOpNames(@NonNull List<Integer> appOps) {
        CharSequence[] appOpNames = new CharSequence[appOps.size()];
        for (int i = 0; i < appOps.size(); ++i) {
            appOpNames[i] = AppOpsManagerCompat.opToName(appOps.get(i));
        }
        return appOpNames;
    }

    /**
     * Whether the app may be using Play App Signing i.e. letting Google manage the app's signing keys.
     *
     * @param applicationInfo {@link PackageManager#GET_META_DATA} must be used while fetching application info.
     * @see <a href="https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Capp-signing-process">Use Play App Signing</a>
     */
    public static boolean usesPlayAppSigning(@NonNull ApplicationInfo applicationInfo) {
        return applicationInfo.metaData != null
                && "STAMP_TYPE_DISTRIBUTION_APK".equals(applicationInfo.metaData
                .getString("com.android.stamp.type"))
                && "https://play.google.com/store".equals(applicationInfo.metaData
                .getString("com.android.stamp.source"));
    }

    @Nullable
    public static Signature[] getSigningInfo(@NonNull PackageInfo packageInfo, boolean isExternal) {
        SignerInfo signerInfo = getSignerInfo(packageInfo, isExternal);
        if (signerInfo == null) return null;
        return signerInfo.getAllSignatures();
    }

    @SuppressWarnings("deprecation")
    @Nullable
    public static SignerInfo getSignerInfo(@NonNull PackageInfo packageInfo, boolean isExternal) {
        if (!isExternal || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo == null) {
                    if (!isExternal) {
                        return null;
                    } // else Could be a false-negative
                } else {
                    return new SignerInfo(signingInfo);
                }
            }
        }
        // Is an external app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || packageInfo.signatures == null) {
            // Could be a false-negative, try with apksig library
            String apkPath = packageInfo.applicationInfo.publicSourceDir;
            if (apkPath != null) {
                Log.w(TAG, "getSigningInfo: Using fallback method");
                SignerInfo signerInfo = getSignerInfo(new File(apkPath));
                if (signerInfo != null) {
                    packageInfo.signatures = signerInfo.getCurrentSignatures();
                }
                return signerInfo;
            }
        }
        return new SignerInfo(packageInfo.signatures);
    }

    @Nullable
    private static SignerInfo getSignerInfo(@NonNull File apkFile) {
        ApkVerifier apkVerifier = new ApkVerifier.Builder(apkFile)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .build();
        try {
            return new SignerInfo(apkVerifier.verify());
        } catch (CertificateEncodingException | IOException | ApkFormatException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(@NonNull PackageInfo packageInfo) {
        return getSigningCertSha256Checksum(packageInfo, false);
    }

    public static boolean isSignatureDifferent(@NonNull PackageInfo newPkgInfo, @NonNull PackageInfo oldPkgInfo) {
        SignerInfo newSignerInfo = getSignerInfo(newPkgInfo, true);
        SignerInfo oldSignerInfo = getSignerInfo(oldPkgInfo, false);
        if (newSignerInfo == null && oldSignerInfo == null) {
            // No signers
            return false;
        }
        if (newSignerInfo == null || oldSignerInfo == null) {
            // One of them is signed, other doesn't
            return true;
        }
        String[] newChecksums;
        List<String> oldChecksums;
        newChecksums = getSigningCertChecksums(DigestUtils.SHA_256, newSignerInfo);
        oldChecksums = Arrays.asList(getSigningCertChecksums(DigestUtils.SHA_256, oldSignerInfo));
        if (newSignerInfo.hasMultipleSigners()) {
            // For multiple signers, all signatures must match.
            if (newChecksums.length != oldChecksums.size()) {
                // Signature is different if the number of signatures don't match
                return true;
            }
            for (String newChecksum : newChecksums) {
                oldChecksums.remove(newChecksum);
            }
            // Old checksums should contain no values if the checksums are the same
            return oldChecksums.size() != 0;
        }
        // For single signer, there could be one or more extra certificates for rotation.
        if (newChecksums.length == 0 && oldChecksums.size() == 0) {
            // No signers
            return false;
        }
        if (newChecksums.length == 0 || oldChecksums.size() == 0) {
            // One of them is signed, other doesn't
            return true;
        }
        // Check if the user is downgrading or reinstalling
        long oldVersionCode = PackageInfoCompat.getLongVersionCode(oldPkgInfo);
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPkgInfo);
        if (oldVersionCode >= newVersionCode) {
            // Downgrading to an older version or reinstalling. Match only the first signature
            return !newChecksums[0].equals(oldChecksums.get(0));
        }
        // Updating or reinstalling. Match only one signature
        for (String newChecksum : newChecksums) {
            if (oldChecksums.contains(newChecksum)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(PackageInfo packageInfo, boolean isExternal) {
        return getSigningCertChecksums(DigestUtils.SHA_256, packageInfo, isExternal);
    }

    @NonNull
    public static String[] getSigningCertChecksums(@DigestUtils.Algorithm String algo,
                                                   PackageInfo packageInfo, boolean isExternal) {
        SignerInfo signerInfo = getSignerInfo(packageInfo, isExternal);
        return getSigningCertChecksums(algo, signerInfo);
    }

    @NonNull
    public static String[] getSigningCertChecksums(@DigestUtils.Algorithm String algo,
                                                   @Nullable SignerInfo signerInfo) {
        Signature[] signatureArray = signerInfo == null ? null : signerInfo.getAllSignatures();
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
        final String separator = LangUtils.getSeparatorString();
        byte[] certBytes = certificate.getEncoded();
        builder.append(getStyledKeyValue(ctx, R.string.subject, certificate.getSubjectX500Principal().getName(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.issuer, certificate.getIssuerX500Principal().getName(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.issued_date, certificate.getNotBefore().toString(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.expiry_date, certificate.getNotAfter().toString(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.type, certificate.getType(), separator))
                .append(", ")
                .append(getStyledKeyValue(ctx, R.string.version, String.valueOf(certificate.getVersion()), separator))
                .append(", ");
        int validity;
        try {
            certificate.checkValidity();
            validity = R.string.valid;
        } catch (CertificateExpiredException e) {
            validity = R.string.expired;
        } catch (CertificateNotYetValidException e) {
            validity = R.string.not_yet_valid;
        }
        builder.append(getStyledKeyValue(ctx, R.string.validity, ctx.getText(validity), separator))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.serial_no) + separator))
                .append(getMonospacedText(Utils.bytesToHex(certificate.getSerialNumber().toByteArray())))
                .append("\n");
        // Checksums
        builder.append(getTitleText(ctx, ctx.getString(R.string.checksums))).append("\n");
        Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
        for (Pair<String, String> digest : digests) {
            builder.append(getPrimaryText(ctx, digest.first + separator))
                    .append(getMonospacedText(digest.second))
                    .append("\n");
        }
        // Signature
        builder.append(getTitleText(ctx, ctx.getString(R.string.app_signing_signature)))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.algorithm, certificate.getSigAlgName(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, "OID", certificate.getSigAlgOID(), separator))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.app_signing_signature) + separator))
                .append(getMonospacedText(Utils.bytesToHex(certificate.getSignature()))).append("\n");
        // Public key used by Google: https://github.com/google/conscrypt
        // 1. X509PublicKey (PublicKey)
        // 2. OpenSSLRSAPublicKey (RSAPublicKey)
        // 3. OpenSSLECPublicKey (ECPublicKey)
        PublicKey publicKey = certificate.getPublicKey();
        builder.append(getTitleText(ctx, ctx.getString(R.string.public_key)))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.algorithm, publicKey.getAlgorithm(), separator))
                .append("\n")
                .append(getStyledKeyValue(ctx, R.string.format, publicKey.getFormat(), separator));
        if (publicKey instanceof RSAPublicKey) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            builder.append("\n")
                    .append(getStyledKeyValue(ctx, R.string.rsa_exponent, rsaPublicKey.getPublicExponent().toString(), separator))
                    .append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_modulus) + separator))
                    .append(getMonospacedText(Utils.bytesToHex(rsaPublicKey.getModulus().toByteArray())));
        } else if (publicKey instanceof ECPublicKey) {
            ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
            builder.append("\n")
                    .append(getStyledKeyValue(ctx, R.string.dsa_affine_x, ecPublicKey.getW().getAffineX().toString(), separator))
                    .append("\n")
                    .append(getStyledKeyValue(ctx, R.string.dsa_affine_y, ecPublicKey.getW().getAffineY().toString(), separator));
        }
        // TODO(5/10/20): Add description for each extensions
        Set<String> critSet = certificate.getCriticalExtensionOIDs();
        if (critSet != null && !critSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.critical_exts)));
            for (String oid : critSet) {
                String oidName = OidMap.getName(oid);
                builder.append("\n- ")
                        .append(getPrimaryText(ctx, (oidName != null ? oidName : oid) + separator))
                        .append(getMonospacedText(Utils.bytesToHex(certificate.getExtensionValue(oid))));
            }
        }
        Set<String> nonCritSet = certificate.getNonCriticalExtensionOIDs();
        if (nonCritSet != null && !nonCritSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.non_critical_exts)));
            for (String oid : nonCritSet) {
                String oidName = OidMap.getName(oid);
                builder.append("\n- ")
                        .append(getPrimaryText(ctx, (oidName != null ? oidName : oid) + separator))
                        .append(getMonospacedText(Utils.bytesToHex(certificate.getExtensionValue(oid))));
            }
        }
        return builder;
    }

    @NonNull
    public static Spannable getApkVerifierInfo(@Nullable ApkVerifier.Result result, Context ctx) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (result == null) return builder;
        int colorFailure = ColorCodes.getFailureColor(ctx);
        int colorSuccess = ColorCodes.getSuccessColor(ctx);
        int warnCount = 0;
        List<CharSequence> errors = new ArrayList<>();
        for (ApkVerifier.IssueWithParams err : result.getErrors()) {
            errors.add(getColoredText(err.toString(), colorFailure));
        }
        warnCount += result.getWarnings().size();
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeIgnoredSigners()) {
            String name = signer.getName();
            for (ApkVerifier.IssueWithParams err : signer.getErrors()) {
                errors.add(getColoredText(new SpannableStringBuilder(getBoldString(name + LangUtils.getSeparatorString())).append(err.toString()), colorFailure));
            }
            warnCount += signer.getWarnings().size();
        }
        if (result.isVerified()) {
            if (warnCount == 0) {
                builder.append(getColoredText(getTitleText(ctx, "✔ " +
                        ctx.getString(R.string.verified)), colorSuccess));
            } else {
                builder.append(getColoredText(getTitleText(ctx, "✔ " + ctx.getResources()
                        .getQuantityString(R.plurals.verified_with_warning, warnCount, warnCount)), colorSuccess));
            }
            if (result.isSourceStampVerified()) {
                builder.append("\n✔ ").append(ctx.getString(R.string.source_stamp_verified));
            }
            List<CharSequence> sigSchemes = new LinkedList<>();
            if (result.isVerifiedUsingV1Scheme()) sigSchemes.add("v1");
            if (result.isVerifiedUsingV2Scheme()) sigSchemes.add("v2");
            if (result.isVerifiedUsingV3Scheme()) sigSchemes.add("v3");
            if (result.isVerifiedUsingV4Scheme()) sigSchemes.add("v4");
            builder.append("\n").append(getPrimaryText(ctx, ctx.getResources()
                    .getQuantityString(R.plurals.app_signing_signature_schemes_pl, sigSchemes.size()) + LangUtils.getSeparatorString()));
            builder.append(TextUtilsCompat.joinSpannable(", ", sigSchemes));
        } else {
            builder.append(getColoredText(getTitleText(ctx, "✘ " + ctx.getString(R.string.not_verified)), colorFailure));
        }
        builder.append("\n");
        // If there are errors, no certificate info will be loaded
        builder.append(TextUtilsCompat.joinSpannable("\n", errors)).append("\n");
        return builder;
    }

    public static void ensurePackageStagingDirectoryPrivileged() throws ErrnoException {
        Path psd = Paths.get(PACKAGE_STAGING_DIRECTORY);
        if (!psd.isDirectory()) {
            // Recreate directory
            Path parent = psd.getParentFile();
            if (parent == null) {
                throw new IllegalStateException("Parent should be /data/local");
            }
            if (psd.exists()) psd.delete();
            psd.mkdir();
        }
        // Change permission
        ExtendedFile f = Objects.requireNonNull(psd.getFile());
        if ((f.getMode() & 0x1FF) != 0711) {
            f.setMode(0711);
        }
        // Change UID, GID
        UidGidPair uidGidPair = f.getUidGid();
        if (uidGidPair.uid != 2000 || uidGidPair.gid != 2000) {
            f.setUidGid(2000, 2000);
        }
    }

    @NonNull
    public static String ensurePackageStagingDirectoryCommand() {
        String psd = PACKAGE_STAGING_DIRECTORY.getAbsolutePath();
        return String.format("( [ -d  %s ] || ( rm %s; mkdir %s && chmod 771 %s && chown 2000:2000 %s ) )", psd, psd, psd, psd, psd);
    }
}
