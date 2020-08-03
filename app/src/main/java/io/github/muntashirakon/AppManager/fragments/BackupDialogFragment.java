package io.github.muntashirakon.AppManager.fragments;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

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
import io.github.muntashirakon.AppManager.storage.backup.BackupStorageManager;

import static io.github.muntashirakon.AppManager.usage.Utils.getPackageLabel;
import static io.github.muntashirakon.AppManager.utils.Utils.requestExternalStoragePermissions;

public class BackupDialogFragment extends DialogFragment {
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

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        Bundle args = requireArguments();
        packageNames = args.getStringArrayList(ARG_PACKAGES);
        if (packageNames == null) return super.onCreateDialog(savedInstanceState);
        boolean[] checkedItems = new boolean[5];
        Arrays.fill(checkedItems, true);
        // Set external data to false
        checkedItems[2] = false;
        return new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                .setTitle(packageNames.size() == 1 ? getPackageLabel(activity
                        .getPackageManager(), packageNames.get(0)) : getString(R.string.backup_options))
                .setMultiChoiceItems(R.array.backup_flags, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) flags |= (1 << which);
                    else flags &= ~(1 << which);
                })
                .setPositiveButton(R.string.backup, (dialog, which) -> {
                    mode = MODE_BACKUP;
                    if (requestExternalStoragePermissions(activity)) {
                        handleBackup();
                    }
                })
                .setNegativeButton(R.string.restore, (dialog, which) -> {
                    mode = MODE_RESTORE;
                    if (requestExternalStoragePermissions(activity)) {
                        handleRestore();
                    }
                })
                .setNeutralButton(R.string.delete_backup, (dialog, which) -> {
                    mode = MODE_DELETE;
                    if (requestExternalStoragePermissions(activity)) {
                        handleDelete();
                    }
                })
                .create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            switch (mode) {
                case MODE_BACKUP: handleBackup(); break;
                case MODE_DELETE: handleDelete(); break;
                case MODE_RESTORE: handleRestore(); break;
            }
        }
    }

    public void handleBackup() {
        BatchOpsManager batchOpsManager = new BatchOpsManager(activity);
        batchOpsManager.setFlags(flags);
        new Thread(() -> {
            if (!batchOpsManager.performOp(BatchOpsManager.OP_BACKUP, new ArrayList<>(packageNames)).isSuccessful()) {
                final List<String> failedPackages = batchOpsManager.getLastResult().failedPackages();
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                        .setTitle(getResources().getQuantityString(R.plurals.alert_failed_to_backup, failedPackages.size(), failedPackages.size()))
                        .setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, failedPackages), null)
                        .setNegativeButton(android.R.string.ok, null)
                        .show());
            } else {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public void handleRestore() {
        BatchOpsManager batchOpsManager = new BatchOpsManager(activity);
        batchOpsManager.setFlags(flags);
        new Thread(() -> {
            if (!batchOpsManager.performOp(BatchOpsManager.OP_RESTORE_BACKUP, new ArrayList<>(packageNames)).isSuccessful()) {
                final List<String> failedPackages = batchOpsManager.getLastResult().failedPackages();
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                        .setTitle(getResources().getQuantityString(R.plurals.alert_failed_to_restore, failedPackages.size(), failedPackages.size()))
                        .setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, failedPackages), null)
                        .setNegativeButton(android.R.string.ok, null)
                        .show());
            } else {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public void handleDelete() {
        BatchOpsManager batchOpsManager = new BatchOpsManager(activity);
        batchOpsManager.setFlags(flags);
        new Thread(() -> {
            if (!batchOpsManager.performOp(BatchOpsManager.OP_DELETE_BACKUP, new ArrayList<>(packageNames)).isSuccessful()) {
                final List<String> failedPackages = batchOpsManager.getLastResult().failedPackages();
                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                        .setTitle(getResources().getQuantityString(R.plurals.alert_failed_to_delete_backup, failedPackages.size(), failedPackages.size()))
                        .setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, failedPackages), null)
                        .setNegativeButton(android.R.string.ok, null)
                        .show());
            } else {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
