package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.runner.Runner;

public class BatchOpsManager {
    @IntDef(value = {
            OP_BACKUP_APK,
            OP_BACKUP_DATA,
            OP_CLEAR_DATA,
            OP_DISABLE,
            OP_DISABLE_BACKGROUND,
            OP_EXPORT_RULES,
            OP_KILL,
            OP_UNINSTALL
    })
    public @interface OpType {}
    public static final int OP_BACKUP_APK = 0;
    public static final int OP_BACKUP_DATA = 1;
    public static final int OP_CLEAR_DATA = 2;
    public static final int OP_DISABLE = 3;
    public static final int OP_DISABLE_BACKGROUND = 4;
    public static final int OP_EXPORT_RULES = 5;
    public static final int OP_KILL = 6;
    public static final int OP_UNINSTALL = 7;

    private Runner runner;
    public BatchOpsManager(Context context) {
        this.runner = Runner.getInstance(context);
    }

    private List<String> packageNames;
    private @OpType int op;
    private Result lastResult;

    @NonNull
    public Result performOp(@OpType int op, List<String> packageNames) {
        this.runner.clear();
        this.op = op;
        this.packageNames = packageNames;
        switch (op) {
            case OP_BACKUP_APK:  // TODO
            case OP_BACKUP_DATA:  // TODO
                break;
            case OP_CLEAR_DATA:
                return opClearData();
            case OP_DISABLE:
                return opDisable();
            case OP_DISABLE_BACKGROUND:  // TODO
            case OP_EXPORT_RULES:  // TODO
            case OP_KILL: // TODO
                break;
            case OP_UNINSTALL:
                return opUninstall();
        }
        lastResult = new Result() {
            @Override
            public int getOp() {
                return op;
            }

            @Override
            public boolean isSuccessful() {
                return false;
            }

            @Override
            public List<String> failedPackages() {
                return null;
            }
        };
        return lastResult;
    }

    public Result getLastResult() {
        return lastResult;
    }

    @NonNull
    private Result opUninstall() {
        for(String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, "pm uninstall --user 0 %s", packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisable() {
        for(String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, "pm disable %s", packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opClearData() {
        for(String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, "pm clear %s", packageName));
        }
        return runOpAndFetchResults();
    }

    private void addCommand(String packageName, String command) {
        runner.addCommand(String.format(Locale.ROOT, "%s > /dev/null 2>&1 || echo %s", command, packageName));
    }

    @NonNull
    private Result runOpAndFetchResults() {
        Runner.Result result = runner.run();
        lastResult = new Result() {
            @Override
            public int getOp() {
                return op;
            }

            @Override
            public boolean isSuccessful() {
                return TextUtils.isEmpty(result.getOutput());
            }

            @Override
            public List<String> failedPackages() {
                return result.getOutputAsList();
            }
        };
        return lastResult;
    }

    public interface Result {
        @OpType int getOp();
        boolean isSuccessful();
        List<String> failedPackages();
    }
}
