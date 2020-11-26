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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class BatchOpsManager {
    // Bundle args
    /**
     * {@link Integer[]} value containing app op values to be used with {@link #OP_IGNORE_APP_OPS}
     * and {@link #OP_RESET_APP_OPS}.
     */
    public static final String ARG_APP_OPS = "app_ops";
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
            OP_IGNORE_APP_OPS,
            OP_RESET_APP_OPS,
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
    public static final int OP_IGNORE_APP_OPS = 13;
    public static final int OP_ENABLE = 14;
    public static final int OP_UNBLOCK_COMPONENTS = 15;
    public static final int OP_RESET_APP_OPS = 16;
    public static final int OP_CLEAR_CACHE = 17;

    private final Runner runner;
    private final Handler handler;

    public BatchOpsManager() {
        this.runner = Runner.getInstance();
        this.handler = new Handler(Looper.getMainLooper());
    }

    private Collection<String> packageNames;
    private Result lastResult;
    private Bundle args;

    public void setArgs(Bundle args) {
        this.args = args;
    }

    @NonNull
    public Result performOp(@OpType int op, Collection<String> packageNames) {
        this.runner.clear();
        this.packageNames = packageNames;
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
            case OP_IGNORE_APP_OPS:
                return opIgnoreAppOps();
            case OP_UNBLOCK_COMPONENTS:
                return opUnblockComponents();
            case OP_RESET_APP_OPS:
                return opResetAppOps();
            case OP_CLEAR_CACHE:
                return opClearCache();
            case OP_NONE:
                break;
        }
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return false;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return new ArrayList<>();
            }
        };
    }

    @Nullable
    public Result getLastResult() {
        return lastResult;
    }

    private Result opBackupApk() {
        List<String> failedPackages = new ArrayList<>();
        int userHandle = Users.getCurrentUserHandle();
        int max = packageNames.size();
        int i = 0;
        Context context = AppManager.getContext();
        PackageManager pm = context.getPackageManager();
        // Initial progress
        sendProgress(context, null, max, 0);
        for (String packageName : packageNames) {
            // Send progress
            sendProgress(context, PackageUtils.getPackageLabel(pm, packageName), max, ++i);
            // Do operation
            if (!ApkUtils.backupApk(packageName, userHandle)) failedPackages.add(packageName);
        }
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPackages.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPackages;
            }
        };
    }

    private Result opBackupRestore(@BackupDialogFragment.ActionMode int mode) {
        List<String> failedPackages = new ArrayList<>();
        int max = packageNames.size();
        int i = 0;
        Context context = AppManager.getContext();
        PackageManager pm = context.getPackageManager();
        // Initial progress
        sendProgress(context, null, max, 0);
        for (String packageName : packageNames) {
            // Send progress
            sendProgress(context, PackageUtils.getPackageLabel(pm, packageName), max, ++i);
            // Do operation
            String[] backupNames = args.getStringArray(ARG_BACKUP_NAMES);
            BackupManager backupManager = BackupManager.getNewInstance(packageName, args.getInt(ARG_FLAGS));
            switch (mode) {
                case BackupDialogFragment.MODE_BACKUP:
                    if (!backupManager.backup(backupNames)) {
                        failedPackages.add(packageName);
                    }
                    break;
                case BackupDialogFragment.MODE_DELETE:
                    if (!backupManager.deleteBackup(backupNames)) failedPackages.add(packageName);
                    break;
                case BackupDialogFragment.MODE_RESTORE:
                    if (!backupManager.restore(backupNames)) failedPackages.add(packageName);
                    break;
            }
        }
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPackages.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPackages;
            }
        };
    }

    private Result opBlockComponents() {
        final List<String> failedPkgList = ComponentUtils.blockFilteredComponents(packageNames, args.getStringArray(ARG_SIGNATURES), Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    private Result opBlockTrackers() {
        final List<String> failedPkgList = ComponentUtils.blockTrackingComponents(packageNames, Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    @NonNull
    private Result opClearCache() {
        AtomicBoolean isSuccessful = new AtomicBoolean(true);
        List<String> failedPackages = new ArrayList<>();
        for (String packageName : packageNames) {
            Runner.Result result = RunnerUtils.clearPackageCache(packageName);
            if (!result.isSuccessful()) {
                isSuccessful.set(false);
                failedPackages.add(packageName);
            }
        }
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return isSuccessful.get();
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPackages;
            }
        };
    }

    @NonNull
    private Result opClearData() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_CLEAR_PACKAGE_DATA,
                    RunnerUtils.userHandleToUser(Users.getCurrentUserHandle()), packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisable() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_DISABLE_PACKAGE,
                    RunnerUtils.userHandleToUser(Users.getCurrentUserHandle()), packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisableBackground() {
        int userHandle = Users.getCurrentUserHandle();
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_SET_MODE_INT, userHandle, packageName, AppOpsManager.OP_RUN_IN_BACKGROUND, AppOpsManager.MODE_IGNORED));
        }
        Result result = runOpAndFetchResults();
        List<String> failedPackages = result.failedPackages();
        for (String packageName : packageNames) {
            if (!failedPackages.contains(packageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                    cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                }
            }
        }
        return result;
    }

    @NonNull
    private Result opEnable() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_ENABLE_PACKAGE,
                    RunnerUtils.userHandleToUser(Users.getCurrentUserHandle()), packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opForceStop() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_FORCE_STOP_PACKAGE,
                    RunnerUtils.userHandleToUser(Users.getCurrentUserHandle()), packageName), false);
        }
        return runOpAndFetchResults();
    }

    private Result opIgnoreAppOps() {
        final List<String> failedPkgList = ExternalComponentsImporter.denyFilteredAppOps(packageNames, args.getIntArray(ARG_APP_OPS), Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    private Result opResetAppOps() {
        final List<String> failedPkgList = ExternalComponentsImporter.defaultFilteredAppOps(packageNames, args.getIntArray(ARG_APP_OPS), Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    private Result opUnblockComponents() {
        final List<String> failedPkgList = ComponentUtils.unblockFilteredComponents(packageNames, args.getStringArray(ARG_SIGNATURES), Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    private Result opUnblockTrackers() {
        final List<String> failedPkgList = ComponentUtils.unblockTrackingComponents(packageNames, Users.getCurrentUserHandle());
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    @NonNull
    private Result opUninstall() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_UNINSTALL_PACKAGE_WITH_DATA,
                    RunnerUtils.userHandleToUser(Users.getCurrentUserHandle()), packageName));
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

    private void addCommand(String packageName, String command) {
        addCommand(packageName, command, true);
    }

    private void addCommand(String packageName, String command, boolean isDevNull) {
        runner.addCommand(String.format(Locale.ROOT, "%s %s || echo %s", command, isDevNull ? "> /dev/null 2>&1" : "", packageName));
    }

    @NonNull
    private Result runOpAndFetchResults() {
        Runner.Result result = runner.runCommand();
        lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return TextUtils.isEmpty(result.getOutput());
            }

            @NonNull
            @Override
            public List<String> failedPackages() {
                return result.getOutputAsList();
            }
        };
        return lastResult;
    }

    public interface Result {
        boolean isSuccessful();

        @NonNull
        List<String> failedPackages();
    }
}
