// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.Context;
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
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.os.BundleCompat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.behavior.DexOptOptions;
import io.github.muntashirakon.AppManager.apk.behavior.DexOptimizer;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.convert.ConvertUtils;
import io.github.muntashirakon.AppManager.backup.convert.Converter;
import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat.NetPolicy;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

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
    /**
     * {@link DexOptOptions}, to be used with {@link #OP_DEXOPT}.
     */
    public static final String ARG_OPTIONS = "options";

    @IntDef(value = {
            OP_NONE,
            OP_BACKUP_APK,
            OP_BACKUP,
            OP_BLOCK_COMPONENTS,
            OP_BLOCK_TRACKERS,
            OP_CLEAR_CACHE,
            OP_CLEAR_DATA,
            OP_DELETE_BACKUP,
            OP_DEXOPT,
            OP_DISABLE_BACKGROUND,
            OP_EXPORT_RULES,
            OP_FORCE_STOP,
            OP_FREEZE,
            OP_GRANT_PERMISSIONS,
            OP_IMPORT_BACKUPS,
            OP_NET_POLICY,
            OP_REVOKE_PERMISSIONS,
            OP_RESTORE_BACKUP,
            OP_SET_APP_OPS,
            OP_UNBLOCK_COMPONENTS,
            OP_UNBLOCK_TRACKERS,
            OP_UNINSTALL,
            OP_UNFREEZE,
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
    public static final int OP_FREEZE = 5;
    public static final int OP_DISABLE_BACKGROUND = 6;
    public static final int OP_EXPORT_RULES = 7;
    public static final int OP_FORCE_STOP = 8;
    public static final int OP_RESTORE_BACKUP = 9;
    public static final int OP_UNBLOCK_TRACKERS = 10;
    public static final int OP_UNINSTALL = 11;
    public static final int OP_BLOCK_COMPONENTS = 12;
    public static final int OP_SET_APP_OPS = 13;
    public static final int OP_UNFREEZE = 14;
    public static final int OP_UNBLOCK_COMPONENTS = 15;
    public static final int OP_CLEAR_CACHE = 16;
    public static final int OP_GRANT_PERMISSIONS = 17;
    public static final int OP_REVOKE_PERMISSIONS = 18;
    public static final int OP_IMPORT_BACKUPS = 19;
    public static final int OP_NET_POLICY = 20;
    public static final int OP_DEXOPT = 21;

    private static final String GROUP_ID = BuildConfig.APPLICATION_ID + ".notification_group.BATCH_OPS";

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

    private UserPackagePair[] mUserPackagePairs;
    @Nullable
    private ProgressHandler mProgressHandler;
    private Bundle mArgs;

    public void setArgs(Bundle args) {
        mArgs = args;
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull List<String> packageNames,
                            @NonNull @UserIdInt List<Integer> userHandles,
                            @Nullable ProgressHandler progressHandler) {
        if (packageNames.size() != userHandles.size()) {
            throw new IllegalArgumentException("Package names and user handles do not have the same size");
        }
        mUserPackagePairs = new UserPackagePair[packageNames.size()];
        for (int i = 0; i < packageNames.size(); ++i) {
            mUserPackagePairs[i] = new UserPackagePair(packageNames.get(i), userHandles.get(i));
        }
        mProgressHandler = progressHandler;
        return performOp(op);
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull Collection<UserPackagePair> userPackagePairs,
                            @Nullable ProgressHandler progressHandler) {
        mUserPackagePairs = userPackagePairs.toArray(new UserPackagePair[0]);
        mProgressHandler = progressHandler;
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
            case OP_FREEZE:
                return opFreeze(true);
            case OP_DISABLE_BACKGROUND:
                return opDisableBackground();
            case OP_UNFREEZE:
                return opFreeze(false);
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
            case OP_DEXOPT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return opPerformDexOpt();
                }
                return new Result(Collections.emptyList(), false);
            case OP_NONE:
                break;
        }
        return new Result(Arrays.asList(mUserPackagePairs));
    }

    public void conclude() {
        if (!mCustomLogger && mLogger != null) {
            mLogger.close();
        }
    }

    private Result opBackupApk() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        int max = mUserPackagePairs.length;
        // Initial progress
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        Context context = ContextUtils.getContext();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            pair = mUserPackagePairs[i];
            updateProgress(lastProgress, i + 1);
            // Do operation
            try {
                ApkUtils.backupApk(context, pair.getPackageName(), pair.getUserId());
            } catch (Exception e) {
                failedPackages.add(pair);
                log("====> op=BACKUP_APK, pkg=" + pair, e);
            }
        }
        return new Result(failedPackages);
    }

    private Result opBackupRestore(@BackupRestoreDialogFragment.ActionMode int mode) {
        switch (mode) {
            case BackupRestoreDialogFragment.MODE_BACKUP:
                return backup();
            case BackupRestoreDialogFragment.MODE_RESTORE:
                return restoreBackups();
            case BackupRestoreDialogFragment.MODE_DELETE:
                return deleteBackups();
        }
        return new Result(Arrays.asList(mUserPackagePairs));
    }

    private Result backup() {
        List<UserPackagePair> failedPackages = Collections.synchronizedList(new ArrayList<>());
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();
        CharSequence operationName = context.getString(R.string.backup_restore);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        AtomicInteger i = new AtomicInteger(0);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        try {
            String[] backupNames = mArgs.getStringArray(ARG_BACKUP_NAMES);
            for (UserPackagePair pair : mUserPackagePairs) {
                CharSequence appLabel = PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserId());
                executor.submit(() -> {
                    synchronized (i) {
                        i.set(i.get() + 1);
                        updateProgress(lastProgress, i.get());
                    }
                    CharSequence title = context.getString(R.string.backing_up_app, appLabel);
                    ProgressHandler subProgressHandler = newSubProgress(operationName, title);
                    BackupManager backupManager = BackupManager.getNewInstance(pair, mArgs.getInt(ARG_FLAGS));
                    try {
                        backupManager.backup(backupNames, subProgressHandler);
                    } catch (BackupException e) {
                        log("====> op=BACKUP_RESTORE, mode=BACKUP pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                    if (subProgressHandler != null) {
                        ThreadUtils.postOnMainThread(() -> subProgressHandler.onResult(null));
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=BACKUP_RESTORE, mode=BACKUP", th);
        }
        executor.awaitCompletion();
        return new Result(failedPackages);
    }

    private Result restoreBackups() {
        List<UserPackagePair> failedPackages = Collections.synchronizedList(new ArrayList<>());
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();
        CharSequence operationName = context.getString(R.string.backup_restore);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        AtomicBoolean requiresRestart = new AtomicBoolean();
        AtomicInteger i = new AtomicInteger(0);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        String[] backupNames = mArgs.getStringArray(ARG_BACKUP_NAMES);
        for (UserPackagePair pair : mUserPackagePairs) {
            executor.submit(() -> {
                synchronized (i) {
                    i.set(i.get() + 1);
                    updateProgress(lastProgress, i.get());
                }
                CharSequence appLabel = PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserId());
                CharSequence title = context.getString(R.string.restoring_app, appLabel);
                ProgressHandler subProgressHandler = newSubProgress(operationName, title);
                BackupManager backupManager = BackupManager.getNewInstance(pair, mArgs.getInt(ARG_FLAGS));
                try {
                    backupManager.restore(backupNames, subProgressHandler);
                    requiresRestart.set(requiresRestart.get() | backupManager.requiresRestart());
                } catch (Throwable e) {
                    log("====> op=BACKUP_RESTORE, mode=RESTORE pkg=" + pair, e);
                    failedPackages.add(pair);
                }
                if (subProgressHandler != null) {
                    ThreadUtils.postOnMainThread(() -> subProgressHandler.onResult(null));
                }
            });
        }
        executor.awaitCompletion();
        Result result = new Result(failedPackages);
        result.setRequiresRestart(requiresRestart.get());
        return result;
    }

    private Result deleteBackups() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        String[] backupNames = mArgs.getStringArray(ARG_BACKUP_NAMES);
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            BackupManager backupManager = BackupManager.getNewInstance(pair, mArgs.getInt(ARG_FLAGS));
            try {
                backupManager.deleteBackup(backupNames);
            } catch (BackupException e) {
                log("====> op=BACKUP_RESTORE, mode=DELETE pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opImportBackups() {
        @ImportType
        int backupType = mArgs.getInt(ARG_BACKUP_TYPE, ImportType.OAndBackup);
        Uri uri = Objects.requireNonNull(BundleCompat.getParcelable(mArgs, ARG_URI, Uri.class));
        boolean removeImported = mArgs.getBoolean(ARG_REMOVE_IMPORTED, false);
        int userHandle = UserHandleHidden.myUserId();
        Path[] files;
        final List<UserPackagePair> failedPkgList = Collections.synchronizedList(new ArrayList<>());
        Path backupPath = Paths.get(uri);
        if (!backupPath.isDirectory()) {
            log("====> op=IMPORT_BACKUP, Not a directory.");
            return new Result(Collections.emptyList(), false);
        }
        files = ConvertUtils.getRelevantImportFiles(backupPath, backupType);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        fixProgress(files.length);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AtomicInteger i = new AtomicInteger(0);
        try {
            for (Path file : files) {
                executor.submit(() -> {
                    synchronized (i) {
                        i.set(i.get() + 1);
                        updateProgress(lastProgress, i.get());
                    }
                    Converter converter = ConvertUtils.getConversionUtil(backupType, file);
                    try {
                        converter.convert();
                        if (removeImported) {
                            // Since the conversion was successful, remove the files for it.
                            converter.cleanup();
                        }
                    } catch (BackupException e) {
                        log("====> op=IMPORT_BACKUP, pkg=" + converter.getPackageName(), e);
                        failedPkgList.add(new UserPackagePair(converter.getPackageName(), userHandle));
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
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                ComponentUtils.blockFilteredComponents(pair, mArgs.getStringArray(ARG_SIGNATURES));
            } catch (Exception e) {
                log("====> op=BLOCK_COMPONENTS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    private Result opBlockTrackers() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                ComponentUtils.blockTrackingComponents(pair);
            } catch (Exception e) {
                log("====> op=BLOCK_TRACKERS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opClearCache() {
        if (mUserPackagePairs.length == 0) {
            // No packages supplied means trim all caches
            return opTrimCaches();
        }
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                PackageManagerCompat.deleteApplicationCacheFilesAsUser(pair);
            } catch (Exception e) {
                log("====> op=CLEAR_CACHE, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
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
        return new Result(Collections.emptyList(), isSuccessful);
    }

    @NonNull
    private Result opClearData() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                PackageManagerCompat.clearApplicationUserData(pair);
            } catch (Exception e) {
                log("====> op=CLEAR_DATA, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opFreeze(boolean freeze) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                if (freeze) {
                    FreezeUtils.freeze(pair.getPackageName(), pair.getUserId());
                } else {
                    FreezeUtils.unfreeze(pair.getPackageName(), pair.getUserId());
                }
            } catch (Throwable e) {
                log("====> op=APP_FREEZE, pkg=" + pair + ", freeze = " + freeze, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opDisableBackground() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            int uid = PackageUtils.getAppUid(pair);
            if (uid == -1) {
                failedPackages.add(pair);
                continue;
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appOpsManager.setMode(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appOpsManager.setMode(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        cb.setAppOp(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, AppOpsManager.MODE_IGNORED);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cb.setAppOp(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, AppOpsManager.MODE_IGNORED);
                    }
                }
            } catch (Throwable e) {
                log("====> op=DISABLE_BACKGROUND, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    private Result opGrantOrRevokePermissions(boolean isGrant) {
        String[] permissions = mArgs.getStringArray(ARG_PERMISSIONS);
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        if (permissions.length == 1 && permissions[0].equals("*")) {
            // Wildcard detected
            int i = 0;
            for (UserPackagePair pair : mUserPackagePairs) {
                updateProgress(lastProgress, ++i);
                try {
                    permissions = PackageUtils.getPermissionsForPackage(pair.getPackageName(), pair.getUserId());
                    if (permissions == null) continue;
                    for (String permission : permissions) {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserId());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserId());
                        }
                    }
                } catch (Throwable e) {
                    log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                    failedPackages.add(pair);
                }
            }
        } else {
            int i = 0;
            for (UserPackagePair pair : mUserPackagePairs) {
                updateProgress(lastProgress, ++i);
                for (String permission : permissions) {
                    try {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserId());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserId());
                        }
                    } catch (Throwable e) {
                        log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                }
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opForceStop() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                PackageManagerCompat.forceStopPackage(pair.getPackageName(), pair.getUserId());
            } catch (Throwable e) {
                log("====> op=FORCE_STOP, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    private Result opNetPolicy() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int netPolicies = mArgs.getInt(ARG_NET_POLICIES, NetworkPolicyManager.POLICY_NONE);
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                int uid = PackageUtils.getAppUid(pair);
                NetworkPolicyManagerCompat.setUidPolicy(uid, netPolicies);
            } catch (Throwable e) {
                log("====> op=NET_POLICY, pkg=" + pair, e);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    private Result opSetAppOps() {
        int[] appOps = mArgs.getIntArray(ARG_APP_OPS);
        int mode = mArgs.getInt(ARG_APP_OP_MODE, AppOpsManager.MODE_IGNORED);
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        if (appOps.length == 1 && appOps[0] == AppOpsManagerCompat.OP_NONE) {
            // Wildcard detected
            int i = 0;
            for (UserPackagePair pair : mUserPackagePairs) {
                updateProgress(lastProgress, ++i);
                try {
                    List<Integer> appOpList = new ArrayList<>();
                    ApplicationInfo info = PackageManagerCompat.getApplicationInfo(pair.getPackageName(),
                            PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, pair.getUserId());
                    List<AppOpsManagerCompat.OpEntry> entries = AppOpsManagerCompat.getConfiguredOpsForPackage(
                            appOpsManager, info.packageName, info.uid);
                    for (AppOpsManagerCompat.OpEntry entry : entries) {
                        appOpList.add(entry.getOp());
                    }
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsManager, pair,
                            ArrayUtils.convertToIntArray(appOpList), mode);
                } catch (Exception e) {
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
            }
        } else {
            int i = 0;
            for (UserPackagePair pair : mUserPackagePairs) {
                updateProgress(lastProgress, ++i);
                try {
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsManager, pair, appOps, mode);
                } catch (RemoteException e) {
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
            }
        }
        return new Result(failedPkgList);
    }

    private Result opUnblockComponents() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                ComponentUtils.unblockFilteredComponents(pair, mArgs.getStringArray(ARG_SIGNATURES));
            } catch (Throwable th) {
                log("====> op=UNBLOCK_COMPONENTS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    private Result opUnblockTrackers() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            try {
                ComponentUtils.unblockTrackingComponents(pair);
            } catch (Throwable th) {
                log("====> op=UNBLOCK_TRACKERS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opUninstall() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AccessibilityMultiplexer accessibility = AccessibilityMultiplexer.getInstance();
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            // Try to use accessibility in unprivileged mode
            accessibility.enableUninstall(true);
        }
        int i = 0;
        for (UserPackagePair pair : mUserPackagePairs) {
            updateProgress(lastProgress, ++i);
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            if (!installer.uninstall(pair.getPackageName(), pair.getUserId(), false)) {
                log("====> op=UNINSTALL, pkg=" + pair);
                failedPackages.add(pair);
            }
        }
        accessibility.enableUninstall(false);
        return new Result(failedPackages);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private Result opPerformDexOpt() {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        DexOptOptions options = BundleCompat.getParcelable(mArgs, ARG_OPTIONS, DexOptOptions.class);
        IPackageManager pm = PackageManagerCompat.getPackageManager();
        if (mUserPackagePairs.length > 0) {
            // Override options.packages with this list
            Set<String> packages = new HashSet<>(mUserPackagePairs.length);
            for (UserPackagePair pair : mUserPackagePairs) {
                packages.add(pair.getPackageName());
            }
            options.packages = packages.toArray(new String[0]);
        } else if (options.packages == null) {
            // Include all packages
            try {
                options.packages = pm.getAllPackages().toArray(new String[0]);
            } catch (RemoteException e) {
                log("====> op=DEXOPT", e);
                return new Result(failedPackages, false);
            }
        }
        fixProgress(options.packages.length);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (String packageName : options.packages) {
            updateProgress(lastProgress, ++i);
            DexOptimizer dexOptimizer = new DexOptimizer(pm, packageName);
            if (options.compilerFiler != null) {
                boolean result = true;
                if (options.clearProfileData) {
                    result &= dexOptimizer.clearApplicationProfileData();
                }
                result &= dexOptimizer.performDexOptMode(options.checkProfiles, options.compilerFiler,
                        options.forceCompilation, options.bootComplete, null);
                if (!result) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=dexopt-mode", dexOptimizer.getLastError());
                    failedPackages.add(new UserPackagePair(packageName, 0));
                    continue;
                }
            }
            if (options.compileLayouts && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                boolean result = true;
                if (options.clearProfileData) {
                    result &= dexOptimizer.clearApplicationProfileData();
                }
                result &= dexOptimizer.compileLayouts();
                if (!result) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=compile-layouts", dexOptimizer.getLastError());
                    failedPackages.add(new UserPackagePair(packageName, 0));
                    continue;
                }
            }
            if (options.forceDexOpt) {
                if (!dexOptimizer.forceDexOpt()) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=force-dexopt", dexOptimizer.getLastError());
                    failedPackages.add(new UserPackagePair(packageName, 0));
                }
            }
        }
        return new Result(failedPackages);
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

    private void updateProgress(float last, int current) {
        if (mProgressHandler == null) {
            return;
        }
        // Current progress = last progress + current
        mProgressHandler.postUpdate(last + current);
    }

    private void fixProgress(int appendMax) {
        if (mProgressHandler == null) {
            return;
        }
        int max = Math.max(mProgressHandler.getLastMax(), 0) + appendMax;
        float current = mProgressHandler.getLastProgress();
        mProgressHandler.postUpdate(max, current);
    }

    @Nullable
    private ProgressHandler newSubProgress(@Nullable CharSequence operationName, @Nullable CharSequence title) {
        if (mProgressHandler == null) {
            return null;
        }
        Object message = mProgressHandler.getLastMessage();
        if (message == null) {
            return null;
        }
        ProgressHandler p = mProgressHandler.newSubProgressHandler();
        if (p instanceof NotificationProgressHandler) {
            NotificationInfo parentNotificationInfo = (NotificationInfo) message;
            NotificationInfo notificationInfo = new NotificationInfo(parentNotificationInfo)
                    .setOperationName(operationName)
                    .setTitle(title);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationInfo.setGroupId(GROUP_ID);
            }
            ThreadUtils.postOnMainThread(() -> p.onProgressStart(-1, 0, notificationInfo));
        }
        return p;
    }

    public static class Result {
        @NonNull
        private final ArrayList<String> mFailedPackages;
        @NonNull
        private final ArrayList<Integer> mAssociatedUserHandles;
        private final boolean mIsSuccessful;

        private boolean mRequiresRestart;

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs) {
            this(failedUserPackagePairs, failedUserPackagePairs.isEmpty());
        }

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs, boolean isSuccessful) {
            mFailedPackages = new ArrayList<>();
            mAssociatedUserHandles = new ArrayList<>();
            for (UserPackagePair userPackagePair : failedUserPackagePairs) {
                mFailedPackages.add(userPackagePair.getPackageName());
                mAssociatedUserHandles.add(userPackagePair.getUserId());
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
