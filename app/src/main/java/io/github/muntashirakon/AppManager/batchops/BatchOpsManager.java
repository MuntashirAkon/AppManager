// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.AppOpsUtils;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.convert.ConvertUtils;
import io.github.muntashirakon.AppManager.backup.convert.Converter;
import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat.NetPolicy;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.io.Path;

@WorkerThread
public class BatchOpsManager {
    public static final String TAG = "BatchOpsManager";

    // Bundle args
    /**
     * {@link Integer[]} value containing app op values to be used with {@link #OP_SET_APP_OPS}.
     */
    public static final String ARG_APP_OPS = "app_ops";
    /**
     * {@link Integer} value containing app op values to be used with {@link #OP_SET_APP_OPS}.
     */
    public static final String ARG_APP_OP_MODE = "app_op_mode";
    /**
     * {@link String[]} value containing permissions to be used with {@link #OP_GRANT_PERMISSIONS}
     * and {@link #OP_REVOKE_PERMISSIONS}.
     */
    public static final String ARG_PERMISSIONS = "perms";
    /**
     * {@link Integer} value containing flags to be used with {@link #OP_BACKUP},
     * {@link #OP_RESTORE_BACKUP} and {@link #OP_DELETE_BACKUP}.
     */
    public static final String ARG_FLAGS = "flags";
    /**
     * {@link String[]} value containing backup names to be used with {@link #OP_BACKUP},
     * {@link #OP_RESTORE_BACKUP} and {@link #OP_DELETE_BACKUP}.
     */
    public static final String ARG_BACKUP_NAMES = "backup_names";
    /**
     * {@link String[]} value containing signatures, e.g., org.acra. To be used with
     * {@link #OP_BLOCK_COMPONENTS} and {@link #OP_UNBLOCK_COMPONENTS}.
     */
    public static final String ARG_SIGNATURES = "signatures";
    /**
     * {@link Integer} value, one of the {@link ImportType}s. To be used with {@link #OP_IMPORT_BACKUPS}.
     */
    public static final String ARG_BACKUP_TYPE = "backup_type";
    /**
     * {@link String} value, {@link String} representation of {@link Uri}. To be used with {@link #OP_IMPORT_BACKUPS}.
     */
    public static final String ARG_URI = "uri";
    /**
     * {@link Boolean} value denoting if the imported backups should be removed after a successful operation.
     * To be used with {@link #OP_IMPORT_BACKUPS}.
     */
    public static final String ARG_REMOVE_IMPORTED = "remove_imported";
    /**
     * {@link Integer} value. One of the {@link NetPolicy network policies}. To be used with {@link #OP_NET_POLICY}.
     */
    public static final String ARG_NET_POLICIES = "net_policies";

    @IntDef(value = {
            OP_NONE,
            OP_BACKUP_APK,
            OP_BACKUP,
            OP_BLOCK_COMPONENTS,
            OP_BLOCK_TRACKERS,
            OP_CLEAR_CACHE,
            OP_CLEAR_DATA,
            OP_DELETE_BACKUP,
            OP_DISABLE,
            OP_DISABLE_BACKGROUND,
            OP_ENABLE,
            OP_EXPORT_RULES,
            OP_FORCE_STOP,
            OP_IMPORT_BACKUPS,
            OP_NET_POLICY,
            OP_SET_APP_OPS,
            OP_GRANT_PERMISSIONS,
            OP_RESTORE_BACKUP,
            OP_REVOKE_PERMISSIONS,
            OP_UNBLOCK_COMPONENTS,
            OP_UNBLOCK_TRACKERS,
            OP_UNINSTALL,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OpType {
    }

    public static final int OP_NONE = -1;
    public static final int OP_BACKUP_APK = 0;
    public static final int OP_BACKUP = 1;
    public static final int OP_BLOCK_TRACKERS = 2;
    public static final int OP_CLEAR_DATA = 3;
    public static final int OP_DELETE_BACKUP = 4;
    public static final int OP_DISABLE = 5;
    public static final int OP_DISABLE_BACKGROUND = 6;
    public static final int OP_EXPORT_RULES = 7;
    public static final int OP_FORCE_STOP = 8;
    public static final int OP_RESTORE_BACKUP = 9;
    public static final int OP_UNBLOCK_TRACKERS = 10;
    public static final int OP_UNINSTALL = 11;
    public static final int OP_BLOCK_COMPONENTS = 12;
    public static final int OP_SET_APP_OPS = 13;
    public static final int OP_ENABLE = 14;
    public static final int OP_UNBLOCK_COMPONENTS = 15;
    public static final int OP_CLEAR_CACHE = 16;
    public static final int OP_GRANT_PERMISSIONS = 17;
    public static final int OP_REVOKE_PERMISSIONS = 18;
    public static final int OP_IMPORT_BACKUPS = 19;
    public static final int OP_NET_POLICY = 20;

    @Nullable
    public Logger mLogger;
    public final boolean mCustomLogger;

    public BatchOpsManager() {
        mCustomLogger = false;
        try {
            mLogger = new BatchOpsLogger();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BatchOpsManager(@Nullable Logger logger) {
        mLogger = logger;
        mCustomLogger = true;
    }

    private static Result lastResult;

    private UserPackagePair[] userPackagePairs;
    private Bundle args;

    public void setArgs(Bundle args) {
        this.args = args;
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull List<String> packageNames, @NonNull @UserIdInt List<Integer> userHandles) {
        if (packageNames.size() != userHandles.size()) {
            throw new IllegalArgumentException("Package names and user handles do not have the same size");
        }
        userPackagePairs = new UserPackagePair[packageNames.size()];
        for (int i = 0; i < packageNames.size(); ++i) {
            userPackagePairs[i] = new UserPackagePair(packageNames.get(i), userHandles.get(i));
        }
        return performOp(op);
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull Collection<UserPackagePair> userPackagePairs) {
        this.userPackagePairs = userPackagePairs.toArray(new UserPackagePair[0]);
        return performOp(op);
    }

    @CheckResult
    @NonNull
    private Result performOp(@OpType int op) {
        switch (op) {
            case OP_BACKUP_APK:
                return opBackupApk();
            case OP_BACKUP:
                return opBackupRestore(BackupRestoreDialogFragment.MODE_BACKUP);
            case OP_BLOCK_TRACKERS:
                return opBlockTrackers();
            case OP_CLEAR_DATA:
                return opClearData();
            case OP_DELETE_BACKUP:
                return opBackupRestore(BackupRestoreDialogFragment.MODE_DELETE);
            case OP_DISABLE:
                return opAppEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
            case OP_DISABLE_BACKGROUND:
                return opDisableBackground();
            case OP_ENABLE:
                return opAppEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            case OP_EXPORT_RULES:
                break;  // Done in the main activity
            case OP_FORCE_STOP:
                return opForceStop();
            case OP_RESTORE_BACKUP:
                return opBackupRestore(BackupRestoreDialogFragment.MODE_RESTORE);
            case OP_UNINSTALL:
                return opUninstall();
            case OP_UNBLOCK_TRACKERS:
                return opUnblockTrackers();
            case OP_BLOCK_COMPONENTS:
                return opBlockComponents();
            case OP_SET_APP_OPS:
                return opSetAppOps();
            case OP_UNBLOCK_COMPONENTS:
                return opUnblockComponents();
            case OP_CLEAR_CACHE:
                return opClearCache();
            case OP_GRANT_PERMISSIONS:
                return opGrantOrRevokePermissions(true);
            case OP_REVOKE_PERMISSIONS:
                return opGrantOrRevokePermissions(false);
            case OP_IMPORT_BACKUPS:
                return opImportBackups();
            case OP_NET_POLICY:
                return opNetPolicy();
            case OP_NONE:
                break;
        }
        return lastResult = new Result(Arrays.asList(userPackagePairs));
    }

    public void conclude() {
        if (!mCustomLogger && mLogger != null) {
            mLogger.close();
        }
    }

    /**
     * @deprecated since v2.5.22
     */
    @Deprecated
    @Nullable
    public static Result getLastResult() {
        return lastResult;
    }

    private Result opBackupApk() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        int max = userPackagePairs.length;
        Context context = AppManager.getContext();
        PackageManager pm = context.getPackageManager();
        // Initial progress
        sendProgress(context, null, max, 0);
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            pair = userPackagePairs[i];
            // Send progress
            sendProgress(context, PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserHandle()),
                    max, i + 1);
            // Do operation
            try {
                ApkUtils.backupApk(pair.getPackageName(), pair.getUserHandle());
            } catch (Exception e) {
                failedPackages.add(pair);
                log("====> op=BACKUP_APK, pkg=" + pair, e);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opBackupRestore(@BackupRestoreDialogFragment.ActionMode int mode) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        AtomicBoolean requiresRestart = new AtomicBoolean();
        try {
            String[] backupNames = args.getStringArray(ARG_BACKUP_NAMES);
            for (UserPackagePair pair : userPackagePairs) {
                executor.submit(() -> {
                    BackupManager backupManager = BackupManager.getNewInstance(pair, args.getInt(ARG_FLAGS));
                    try {
                        switch (mode) {
                            case BackupRestoreDialogFragment.MODE_BACKUP:
                                backupManager.backup(backupNames);
                                break;
                            case BackupRestoreDialogFragment.MODE_DELETE:
                                backupManager.deleteBackup(backupNames);
                                break;
                            case BackupRestoreDialogFragment.MODE_RESTORE:
                                backupManager.restore(backupNames);
                                requiresRestart.set(requiresRestart.get() | backupManager.requiresRestart());
                                break;
                        }
                    } catch (BackupException e) {
                        log("====> op=BACKUP_RESTORE, mode=" + mode + " pkg=" + pair, e);
                        synchronized (failedPackages) {
                            failedPackages.add(pair);
                        }
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=BACKUP_RESTORE, mode=" + mode, th);
        }
        executor.awaitCompletion();
        lastResult = new Result(failedPackages);
        lastResult.setRequiresRestart(requiresRestart.get());
        return lastResult;
    }

    @NonNull
    private Result opImportBackups() {
        @ImportType
        int backupType = args.getInt(ARG_BACKUP_TYPE, ImportType.OAndBackup);
        Uri uri = Objects.requireNonNull(args.getParcelable(ARG_URI));
        boolean removeImported = args.getBoolean(ARG_REMOVE_IMPORTED, false);
        int userHandle = UserHandleHidden.myUserId();
        Path[] files;
        final List<UserPackagePair> failedPkgList = new ArrayList<>();
        files = ConvertUtils.getRelevantImportFiles(uri, backupType);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        try {
            for (Path file : files) {
                executor.submit(() -> {
                    Converter converter = ConvertUtils.getConversionUtil(backupType, file);
                    try {
                        converter.convert();
                        if (removeImported) {
                            // Since the conversion was successful, remove the files for it.
                            converter.cleanup();
                        }
                    } catch (BackupException e) {
                        log("====> op=IMPORT_BACKUP, pkg=" + converter.getPackageName(), e);
                        synchronized (failedPkgList) {
                            failedPkgList.add(new UserPackagePair(converter.getPackageName(), userHandle));
                        }
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=IMPORT_BACKUP", th);
        }
        executor.awaitCompletion();
        return new Result(failedPkgList);
    }

    private Result opBlockComponents() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                ComponentUtils.blockFilteredComponents(pair, args.getStringArray(ARG_SIGNATURES));
            } catch (Exception e) {
                log("====> op=BLOCK_COMPONENTS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opBlockTrackers() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                ComponentUtils.blockTrackingComponents(pair);
            } catch (Exception e) {
                log("====> op=BLOCK_TRACKERS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opClearCache() {
        if (userPackagePairs.length == 0) {
            // No packages supplied means trim all caches
            return opTrimCaches();
        }
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                PackageManagerCompat.deleteApplicationCacheFilesAsUser(pair);
            } catch (Exception e) {
                log("====> op=CLEAR_CACHE, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opTrimCaches() {
        long size = 1024L * 1024L * 1024L * 1024L;  // 1 TB
        boolean isSuccessful;
        try {
            // TODO: 30/8/21 Iterate all volumes?
            PackageManagerCompat.freeStorageAndNotify(null /* internal */, size,
                    StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED);
            isSuccessful = true;
        } catch (Throwable e) {
            log("====> op=TRIM_CACHES", e);
            isSuccessful = false;
        }
        return lastResult = new Result(Collections.emptyList(), isSuccessful);
    }

    @NonNull
    private Result opClearData() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                PackageManagerCompat.clearApplicationUserData(pair);
            } catch (Exception e) {
                log("====> op=CLEAR_DATA, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opAppEnabledSetting(@PackageManagerCompat.EnabledState int newState) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        IPackageManager pm = PackageManagerCompat.getPackageManager();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                pm.setApplicationEnabledSetting(pair.getPackageName(), newState, 0, pair.getUserHandle(), null);
            } catch (Throwable e) {
                log("====> op=APP_ENABLED_SETTING, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opDisableBackground() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        List<UserPackagePair> appliedPackages = new ArrayList<>();
        AppOpsService appOpsService = new AppOpsService();
        for (UserPackagePair pair : userPackagePairs) {
            int uid = PackageUtils.getAppUid(pair);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appOpsService.setMode(AppOpsManager.OP_RUN_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appOpsService.setMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                appliedPackages.add(pair);
            } catch (Throwable e) {
                log("====> op=DISABLE_BACKGROUND, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        for (UserPackagePair pair : appliedPackages) {
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
                cb.setAppOp(AppOpsManager.OP_RUN_IN_BACKGROUND, AppOpsManager.MODE_IGNORED);
            }
        }
        return new Result(failedPackages);
    }

    private Result opGrantOrRevokePermissions(boolean isGrant) {
        String[] permissions = args.getStringArray(ARG_PERMISSIONS);
        List<UserPackagePair> failedPackages = new ArrayList<>();
        if (permissions.length == 1 && permissions[0].equals("*")) {
            // Wildcard detected
            for (UserPackagePair pair : userPackagePairs) {
                try {
                    permissions = PackageUtils.getPermissionsForPackage(pair.getPackageName(), pair.getUserHandle());
                    if (permissions == null) continue;
                    for (String permission : permissions) {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserHandle());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserHandle());
                        }
                    }
                } catch (Throwable e) {
                    log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                    failedPackages.add(pair);
                }
            }
        } else {
            for (UserPackagePair pair : userPackagePairs) {
                for (String permission : permissions) {
                    try {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserHandle());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserHandle());
                        }
                    } catch (Throwable e) {
                        log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                }
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opForceStop() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                PackageManagerCompat.forceStopPackage(pair.getPackageName(), pair.getUserHandle());
            } catch (Throwable e) {
                log("====> op=FORCE_STOP, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opNetPolicy() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        int netPolicies = args.getInt(ARG_NET_POLICIES, NetworkPolicyManager.POLICY_NONE);
        for (UserPackagePair pair : userPackagePairs) {
            try {
                int uid = PackageUtils.getAppUid(pair);
                NetworkPolicyManagerCompat.setUidPolicy(uid, netPolicies);
            } catch (Throwable e) {
                log("====> op=NET_POLICY, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opSetAppOps() {
        int[] appOps = args.getIntArray(ARG_APP_OPS);
        int mode = args.getInt(ARG_APP_OP_MODE, AppOpsManager.MODE_IGNORED);
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        AppOpsService appOpsService = new AppOpsService();
        if (appOps.length == 1 && appOps[0] == AppOpsManager.OP_NONE) {
            // Wildcard detected
            for (UserPackagePair pair : userPackagePairs) {
                try {
                    List<Integer> appOpList = new ArrayList<>();
                    ApplicationInfo info = PackageManagerCompat.getApplicationInfo(pair.getPackageName(), pair.getUserHandle(), 0);
                    List<OpEntry> entries = AppOpsUtils.getChangedAppOps(appOpsService, info.packageName, info.uid);
                    for (OpEntry entry : entries) {
                        appOpList.add(entry.getOp());
                    }
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsService, pair, ArrayUtils.convertToIntArray(appOpList), mode);
                } catch (Exception e) {
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
            }
        } else {
            for (UserPackagePair pair : userPackagePairs) {
                try {
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsService, pair, appOps, mode);
                } catch (RemoteException e) {
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
            }
        }
        return lastResult = new Result(failedPkgList);
    }

    private Result opUnblockComponents() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                ComponentUtils.unblockFilteredComponents(pair, args.getStringArray(ARG_SIGNATURES));
            } catch (Throwable th) {
                log("====> op=UNBLOCK_COMPONENTS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opUnblockTrackers() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                ComponentUtils.unblockTrackingComponents(pair);
            } catch (Throwable th) {
                log("====> op=UNBLOCK_TRACKERS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opUninstall() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                PackageInstallerCompat.uninstall(pair.getPackageName(), pair.getUserHandle(), false);
            } catch (Throwable e) {
                log("====> op=UNINSTALL, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private void log(@Nullable String message, @Nullable Throwable th) {
        if (mLogger != null) {
            mLogger.println(message, th);
        }
    }

    private void log(@Nullable String message) {
        if (mLogger != null) {
            mLogger.println(message);
        }
    }

    private void sendProgress(@NonNull Context context, CharSequence message, int max, int current) {
        Intent broadcastIntent = new Intent(BatchOpsService.ACTION_BATCH_OPS_PROGRESS);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_MESSAGE, message);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_MAX, max);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_CURRENT, current);
        UiThreadHandler.run(() -> context.sendBroadcast(broadcastIntent));
    }

    public static class Result {
        @NonNull
        private final ArrayList<String> mFailedPackages;
        @NonNull
        private final ArrayList<Integer> mAssociatedUserHandles;
        @NonNull
        private final List<UserPackagePair> mUserPackagePairs;
        private final boolean mIsSuccessful;

        private boolean mRequiresRestart;

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs) {
            this(failedUserPackagePairs, failedUserPackagePairs.isEmpty());
        }

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs, boolean isSuccessful) {
            mUserPackagePairs = failedUserPackagePairs;
            mFailedPackages = new ArrayList<>();
            mAssociatedUserHandles = new ArrayList<>();
            for (UserPackagePair userPackagePair : failedUserPackagePairs) {
                mFailedPackages.add(userPackagePair.getPackageName());
                mAssociatedUserHandles.add(userPackagePair.getUserHandle());
            }
            mIsSuccessful = isSuccessful;
        }

        public boolean requiresRestart() {
            return mRequiresRestart;
        }

        public void setRequiresRestart(boolean requiresRestart) {
            mRequiresRestart = requiresRestart;
        }

        public boolean isSuccessful() {
            return mIsSuccessful;
        }

        @NonNull
        public List<UserPackagePair> getUserPackagePairs() {
            return mUserPackagePairs;
        }

        @NonNull
        public ArrayList<String> getFailedPackages() {
            return mFailedPackages;
        }

        @NonNull
        @UserIdInt
        public ArrayList<Integer> getAssociatedUserHandles() {
            return mAssociatedUserHandles;
        }
    }
}
