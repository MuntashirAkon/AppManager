package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.fragments.BackupDialogFragment;
import io.github.muntashirakon.AppManager.storage.backup.BackupStorageManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.utils.RunnerUtils;

// TODO: Will be converted to service one day
public class BatchOpsManager {
    @IntDef(value = {
            OP_BACKUP_APK,
            OP_BACKUP,
            OP_BLOCK_TRACKERS,
            OP_CLEAR_DATA,
            OP_DELETE_BACKUP,
            OP_DISABLE,
            OP_DISABLE_BACKGROUND,
            OP_EXPORT_RULES,
            OP_KILL,
            OP_RESTORE_BACKUP,
            OP_UNINSTALL
    })
    public @interface OpType {}
    public static final int OP_BACKUP_APK = 0;
    public static final int OP_BACKUP = 1;
    public static final int OP_BLOCK_TRACKERS = 2;
    public static final int OP_CLEAR_DATA = 3;
    public static final int OP_DELETE_BACKUP = 4;
    public static final int OP_DISABLE = 5;
    public static final int OP_DISABLE_BACKGROUND = 6;
    public static final int OP_EXPORT_RULES = 7;
    public static final int OP_KILL = 8;
    public static final int OP_RESTORE_BACKUP = 9;
    public static final int OP_UNINSTALL = 10;

    private Runner runner;
    private Context context;
    public BatchOpsManager(Context context) {
        this.context = context;
        this.runner = Runner.getInstance();
    }

    private List<String> packageNames;
    private int flags = 0;  // Currently only for backup/restore
    private Result lastResult;

    public void setFlags(int flags) {
        this.flags = flags;
    }

    @NonNull
    public Result performOp(@OpType int op, List<String> packageNames) {
        this.runner.clear();
        this.packageNames = packageNames;
        switch (op) {
            case OP_BACKUP_APK: return opBackupApk();
            case OP_BACKUP: return opBackupRestore(BackupDialogFragment.MODE_BACKUP);
            case OP_BLOCK_TRACKERS: return opBlockTrackers();
            case OP_CLEAR_DATA: return opClearData();
            case OP_DELETE_BACKUP: return opBackupRestore(BackupDialogFragment.MODE_DELETE);
            case OP_DISABLE: return opDisable();
            case OP_DISABLE_BACKGROUND: return opDisableBackground();
            case OP_EXPORT_RULES: break;  // Done in the main activity
            case OP_KILL: return opKill();
            case OP_RESTORE_BACKUP: return opBackupRestore(BackupDialogFragment.MODE_RESTORE);
            case OP_UNINSTALL: return opUninstall();
        }
        lastResult = new Result() {
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
        return lastResult;
    }

    public Result getLastResult() {
        return lastResult;
    }

    private Result opBackupApk() {
        List<String> failedPackages = new ArrayList<>();
        for (String packageName: packageNames) {
            if (!BackupStorageManager.backupApk(packageName))
                failedPackages.add(packageName);
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
        for (String packageName: packageNames) {
            try (BackupStorageManager backupStorageManager = BackupStorageManager.getInstance(packageName)) {
                backupStorageManager.setFlags(flags);
                switch (mode) {
                    case BackupDialogFragment.MODE_BACKUP:
                        if (!backupStorageManager.backup()) failedPackages.add(packageName);
                        break;
                    case BackupDialogFragment.MODE_DELETE:
                        if (!backupStorageManager.delete_backup()) failedPackages.add(packageName);
                        break;
                    case BackupDialogFragment.MODE_RESTORE:
                        if (!backupStorageManager.restore()) failedPackages.add(packageName);
                        break;
                }
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

    private Result opBlockTrackers() {
        final List<String> failedPkgList = ExternalComponentsImporter.applyFromTrackingComponents(context, packageNames);
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
    private Result opClearData() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_CLEAR_PACKAGE_DATA, packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisable() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_DISABLE_PACKAGE, packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisableBackground() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_SET_MODE_INT, packageName, 63, AppOpsManager.MODE_IGNORED));
        }
        Result result = runOpAndFetchResults();
        List<String> failedPackages = result.failedPackages();
        for (String packageName: packageNames) {
            if (!failedPackages.contains(packageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                    cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                }
            }
        }
        return result;
    }

    @NonNull
    private Result opKill() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_PID_PACKAGE, packageName), false);
        }
        Result result = runOpAndFetchResults();
        List<String> pidOrPackageNames = result.failedPackages();
        runner.clear();
        for (int i = 0; i<packageNames.size(); ++i) {
            if (!pidOrPackageNames.get(i).equals(packageNames.get(i))) {
                addCommand(packageNames.get(i), String.format(Locale.ROOT, RunnerUtils.CMD_KILL_SIG9, pidOrPackageNames.get(i)));
            }
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opUninstall() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_UNINSTALL_PACKAGE, packageName));
        }
        return runOpAndFetchResults();
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
        @NonNull List<String> failedPackages();
    }
}
