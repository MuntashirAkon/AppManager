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

package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class BatchOpsManager {
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
            OP_SET_APP_OPS,
            OP_RESTORE_BACKUP,
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

    private final Runner runner;
    private final Handler handler;

    public BatchOpsManager() {
        this.runner = Runner.getInstance();
        this.handler = new Handler(Looper.getMainLooper());
    }

    private static Result lastResult;

    private UserPackagePair[] userPackagePairs;
    private Bundle args;

    public void setArgs(Bundle args) {
        this.args = args;
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull Collection<String> packageNames) {
        int userHandle = Users.getCurrentUserHandle();
        return performOp(op, new ArrayList<>(packageNames), userHandle);
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull List<String> packageNames, int userHandle) {
        List<Integer> userHandles = new ArrayList<>(packageNames.size());
        for (String ignore : packageNames) userHandles.add(userHandle);
        return performOp(op, packageNames, userHandles);
    }

    @CheckResult
    @NonNull
    public Result performOp(@OpType int op, @NonNull List<String> packageNames, @NonNull List<Integer> userHandles) {
        this.runner.clear();
        if (packageNames.size() != userHandles.size()) {
            throw new IllegalArgumentException("Package names and user handles do not have the same size");
        }
        userPackagePairs = new UserPackagePair[packageNames.size()];
        for (int i = 0; i < packageNames.size(); ++i) {
            userPackagePairs[i] = new UserPackagePair(packageNames.get(i), userHandles.get(i));
        }
        switch (op) {
            case OP_BACKUP_APK:
                return opBackupApk();
            case OP_BACKUP:
                return opBackupRestore(BackupDialogFragment.MODE_BACKUP);
            case OP_BLOCK_TRACKERS:
                return opBlockTrackers();
            case OP_CLEAR_DATA:
                return opClearData();
            case OP_DELETE_BACKUP:
                return opBackupRestore(BackupDialogFragment.MODE_DELETE);
            case OP_DISABLE:
                return opDisable();
            case OP_DISABLE_BACKGROUND:
                return opDisableBackground();
            case OP_ENABLE:
                return opEnable();
            case OP_EXPORT_RULES:
                break;  // Done in the main activity
            case OP_FORCE_STOP:
                return opForceStop();
            case OP_RESTORE_BACKUP:
                return opBackupRestore(BackupDialogFragment.MODE_RESTORE);
            case OP_UNINSTALL:
                return opUninstall();
            case OP_UNBLOCK_TRACKERS:
                return opUnblockTrackers();
            case OP_BLOCK_COMPONENTS:
                return opBlockComponents();
            case OP_SET_APP_OPS:
                return opIgnoreAppOps();
            case OP_UNBLOCK_COMPONENTS:
                return opUnblockComponents();
            case OP_CLEAR_CACHE:
                return opClearCache();
            case OP_NONE:
                break;
        }
        return lastResult = new Result(Arrays.asList(userPackagePairs));
    }

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
            sendProgress(context, PackageUtils.getPackageLabel(pm, pair.getPackageName(),
                    pair.getUserHandle()).toString(), max, i + 1);
            // Do operation
            if (!ApkUtils.backupApk(pair.getPackageName(), pair.getUserHandle())) {
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opBackupRestore(@BackupDialogFragment.ActionMode int mode) {
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
            sendProgress(context, PackageUtils.getPackageLabel(pm, pair.getPackageName(),
                    pair.getUserHandle()).toString(), max, i + 1);
            // Do operation
            String[] backupNames = args.getStringArray(ARG_BACKUP_NAMES);
            BackupManager backupManager = BackupManager.getNewInstance(pair.getPackageName(), args.getInt(ARG_FLAGS));
            switch (mode) {
                case BackupDialogFragment.MODE_BACKUP:
                    if (!backupManager.backup(backupNames)) failedPackages.add(pair);
                    break;
                case BackupDialogFragment.MODE_DELETE:
                    if (!backupManager.deleteBackup(backupNames)) failedPackages.add(pair);
                    break;
                case BackupDialogFragment.MODE_RESTORE:
                    if (!backupManager.restore(backupNames)) failedPackages.add(pair);
                    break;
            }
        }
        return lastResult = new Result(failedPackages);
    }

    private Result opBlockComponents() {
        final List<UserPackagePair> failedPkgList = ComponentUtils.blockFilteredComponents(
                Arrays.asList(userPackagePairs), args.getStringArray(ARG_SIGNATURES));
        return lastResult = new Result(failedPkgList);
    }

    private Result opBlockTrackers() {
        final List<UserPackagePair> failedPkgList = ComponentUtils.blockTrackingComponents(Arrays.asList(userPackagePairs));
        return lastResult = new Result(failedPkgList);
    }

    @NonNull
    private Result opClearCache() {
        AtomicBoolean isSuccessful = new AtomicBoolean(true);
        List<UserPackagePair> failedPackages = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            Runner.Result result = RunnerUtils.clearPackageCache(pair.getPackageName(), pair.getUserHandle());
            if (!result.isSuccessful()) {
                isSuccessful.set(false);
                failedPackages.add(pair);
            }
        }
        return lastResult = new Result(failedPackages);
    }

    @NonNull
    private Result opClearData() {
        for (UserPackagePair pair : userPackagePairs) {
            addCommand(pair.getPackageName(), pair.getUserHandle(), String.format(Locale.ROOT,
                    RunnerUtils.CMD_CLEAR_PACKAGE_DATA, RunnerUtils.userHandleToUser(
                            pair.getUserHandle()), pair.getPackageName()));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisable() {
        for (UserPackagePair pair : userPackagePairs) {
            addCommand(pair.getPackageName(), pair.getUserHandle(), String.format(Locale.ROOT,
                    RunnerUtils.CMD_DISABLE_PACKAGE, RunnerUtils.userHandleToUser(
                            pair.getUserHandle()), pair.getPackageName()));
        }
        return runOpAndFetchResults();
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
            } catch (RemoteException e) {
                e.printStackTrace();
                failedPackages.add(pair);
            }
        }
        for (UserPackagePair pair : appliedPackages) {
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
                cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opEnable() {
        for (UserPackagePair pair : userPackagePairs) {
            addCommand(pair.getPackageName(), pair.getUserHandle(), String.format(Locale.ROOT,
                    RunnerUtils.CMD_ENABLE_PACKAGE, RunnerUtils.userHandleToUser(pair.getUserHandle()),
                    pair.getPackageName()));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opForceStop() {
        for (UserPackagePair pair : userPackagePairs) {
            addCommand(pair.getPackageName(), pair.getUserHandle(), String.format(Locale.ROOT,
                    RunnerUtils.CMD_FORCE_STOP_PACKAGE, RunnerUtils.userHandleToUser(
                            pair.getUserHandle()), pair.getPackageName()), false);
        }
        return runOpAndFetchResults();
    }

    private Result opIgnoreAppOps() {
        final List<UserPackagePair> failedPkgList = ExternalComponentsImporter
                .setModeToFilteredAppOps(Arrays.asList(userPackagePairs),
                        args.getIntArray(ARG_APP_OPS),
                        args.getInt(ARG_APP_OP_MODE, AppOpsManager.MODE_IGNORED));
        return lastResult = new Result(failedPkgList);
    }

    private Result opUnblockComponents() {
        final List<UserPackagePair> failedPkgList = ComponentUtils.unblockFilteredComponents(Arrays.asList(userPackagePairs), args.getStringArray(ARG_SIGNATURES));
        return lastResult = new Result(failedPkgList);
    }

    private Result opUnblockTrackers() {
        final List<UserPackagePair> failedPkgList = ComponentUtils.unblockTrackingComponents(Arrays.asList(userPackagePairs));
        return lastResult = new Result(failedPkgList);
    }

    @NonNull
    private Result opUninstall() {
        for (UserPackagePair pair : userPackagePairs) {
            addCommand(pair.getPackageName(), pair.getUserHandle(), String.format(Locale.ROOT,
                    RunnerUtils.CMD_UNINSTALL_PACKAGE_WITH_DATA, RunnerUtils.userHandleToUser(
                            pair.getUserHandle()), pair.getPackageName()));
        }
        return runOpAndFetchResults();
    }

    private void sendProgress(@NonNull Context context, String message, int max, int current) {
        Intent broadcastIntent = new Intent(BatchOpsService.ACTION_BATCH_OPS_PROGRESS);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_MESSAGE, message);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_MAX, max);
        broadcastIntent.putExtra(BatchOpsService.EXTRA_PROGRESS_CURRENT, current);
        handler.post(() -> context.sendBroadcast(broadcastIntent));
    }

    private void addCommand(String packageName, int userHandle, String command) {
        addCommand(packageName, userHandle, command, true);
    }

    private void addCommand(String packageName, int userHandle, String command, boolean isDevNull) {
        runner.addCommand(String.format(Locale.ROOT, "%s %s || echo %s %d", command, isDevNull ? "> /dev/null 2>&1" : "", packageName, userHandle));
    }

    @NonNull
    private Result runOpAndFetchResults() {
        Runner.Result result = runner.runCommand();
        List<UserPackagePair> userPackagePairs = new ArrayList<>();
        String[] packageAndUser;
        for (String line : result.getOutputAsList()) {
            packageAndUser = line.split(" ");
            if (packageAndUser.length == 2) {
                userPackagePairs.add(new UserPackagePair(packageAndUser[0], Integer.parseInt(packageAndUser[1])));
            }
        }
        return lastResult = new Result(userPackagePairs);
    }

    public static class Result {
        @NonNull
        private final ArrayList<String> failedPackages;
        @NonNull
        private final ArrayList<Integer> associatedUserHandles;
        @NonNull
        private final List<UserPackagePair> userPackagePairs;

        public Result(@NonNull List<UserPackagePair> userPackagePairs) {
            this.userPackagePairs = userPackagePairs;
            failedPackages = new ArrayList<>();
            associatedUserHandles = new ArrayList<>();
            for (UserPackagePair userPackagePair : userPackagePairs) {
                failedPackages.add(userPackagePair.getPackageName());
                associatedUserHandles.add(userPackagePair.getUserHandle());
            }
        }

        public boolean isSuccessful() {
            return failedPackages.size() == 0;
        }

        @NonNull
        public List<UserPackagePair> getUserPackagePairs() {
            return userPackagePairs;
        }

        @NonNull
        public ArrayList<String> getFailedPackages() {
            return failedPackages;
        }

        @NonNull
        public ArrayList<Integer> getAssociatedUserHandles() {
            return associatedUserHandles;
        }
    }
}
