package io.github.muntashirakon.AppManager.backup;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.Utils.requestExternalStoragePermissions;

public class BackupDialogFragment extends DialogFragment {
    public static final String TAG = "BackupDialogFragment";
    public static final String ARG_PACKAGES = "ARG_PACKAGES";

    @IntDef(value = {
            MODE_BACKUP,
            MODE_RESTORE,
            MODE_DELETE
    })
    public @interface ActionMode {}
    public static final int MODE_BACKUP = 864;
    public static final int MODE_RESTORE = 169;
    public static final int MODE_DELETE = 642;

    private @BackupStorageManager.BackupFlags int flags = BackupStorageManager.BACKUP_APK
            | BackupStorageManager.BACKUP_DATA | BackupStorageManager.BACKUP_EXCLUDE_CACHE
            | BackupStorageManager.BACKUP_RULES;
    private @ActionMode int mode = MODE_BACKUP;
    private List<String> packageNames;
    FragmentActivity activity;

    public interface ActionCompleteInterface {
        void onActionComplete(@ActionMode int mode, @NonNull String[] failedPackages);
    }

    private @Nullable ActionCompleteInterface listener;

    public void setOnActionCompleteListener(@NonNull ActionCompleteInterface actionCompleteListener) {
        this.listener = actionCompleteListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        Bundle args = requireArguments();
        packageNames = args.getStringArrayList(ARG_PACKAGES);
        if (packageNames == null) return super.onCreateDialog(savedInstanceState);
        boolean[] checkedItems = new boolean[6];
        Arrays.fill(checkedItems, true);
        // Set external data to false
        checkedItems[2] = false;
        // Set skip signature checks to false
        checkedItems[5] = false;
        return new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                .setTitle(packageNames.size() == 1 ? PackageUtils.getPackageLabel(activity
                        .getPackageManager(), packageNames.get(0)) : getString(R.string.backup_options))
                .setMultiChoiceItems(R.array.backup_flags, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) flags |= (1 << which);
                    else flags &= ~(1 << which);
                })
                .setPositiveButton(R.string.backup, (dialog, which) -> {
                    mode = MODE_BACKUP;
                    if (requestExternalStoragePermissions(activity)) {
                        handleMode();
                    }
                })
                .setNegativeButton(R.string.restore, (dialog, which) -> {
                    mode = MODE_RESTORE;
                    if (requestExternalStoragePermissions(activity)) {
                        handleMode();
                    }
                })
                .setNeutralButton(R.string.delete_backup, (dialog, which) -> {
                    mode = MODE_DELETE;
                    if (requestExternalStoragePermissions(activity)) {
                        handleMode();
                    }
                })
                .create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) handleMode();
    }

    public void handleMode() {
        BatchOpsManager batchOpsManager = new BatchOpsManager(activity);
        batchOpsManager.setFlags(flags);
        @BatchOpsManager.OpType int op;
        switch (mode) {
            case MODE_DELETE: op = BatchOpsManager.OP_DELETE_BACKUP; break;
            case MODE_RESTORE: op = BatchOpsManager.OP_RESTORE_BACKUP; break;
            case MODE_BACKUP:
            default: op = BatchOpsManager.OP_BACKUP;
        }
        new Thread(() -> {
            batchOpsManager.performOp(op, new ArrayList<>(packageNames));
            if (listener != null) {
                activity.runOnUiThread(() -> listener.onActionComplete(mode,
                        batchOpsManager.getLastResult().failedPackages().toArray(new String[0])));
            }
        }).start();
    }
}
