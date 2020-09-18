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

package io.github.muntashirakon.AppManager.backup;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
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
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionMode {
    }

    public static final int MODE_BACKUP = 864;
    public static final int MODE_RESTORE = 169;
    public static final int MODE_DELETE = 642;

    private BackupFlags flags = BackupFlags.fromPref();
    @ActionMode
    private int mode = MODE_BACKUP;
    private List<String> packageNames;
    private int backupCount = 0;
    private FragmentActivity activity;
    private BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (actionCompleteInterface != null) {
                BatchOpsManager.Result result = new BatchOpsManager().getLastResult();
                actionCompleteInterface.onActionComplete(mode, result != null ? result.failedPackages().toArray(new String[0]) : new String[0]);
            }
            activity.unregisterReceiver(mBatchOpsBroadCastReceiver);
        }
    };

    public interface ActionCompleteInterface {
        void onActionComplete(@ActionMode int mode, @NonNull String[] failedPackages);
    }

    public interface ActionBeginInterface {
        void onActionBegin(@ActionMode int mode);
    }

    @Nullable
    private ActionCompleteInterface actionCompleteInterface;
    @Nullable
    private ActionBeginInterface actionBeginInterface;

    public void setOnActionCompleteListener(@NonNull ActionCompleteInterface actionCompleteInterface) {
        this.actionCompleteInterface = actionCompleteInterface;
    }

    public void setOnActionBeginListener(@NonNull ActionBeginInterface actionBeginInterface) {
        this.actionBeginInterface = actionBeginInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        Bundle args = requireArguments();
        packageNames = args.getStringArrayList(ARG_PACKAGES);
        if (packageNames == null) return super.onCreateDialog(savedInstanceState);
        // Check if backup exists for all apps
        for (String packageName : packageNames) {
            if (MetadataManager.hasMetadata(packageName)) {
                ++backupCount;
            }
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(packageNames.size() == 1 ? PackageUtils.getPackageLabel(activity
                        .getPackageManager(), packageNames.get(0)) : getString(R.string.backup_options))
                .setMultiChoiceItems(R.array.backup_flags, flags.flagsToCheckedItems(),
                        (dialog, flag, isChecked) -> {
                            if (isChecked) flags.addFlag(flag);
                            else flags.removeFlag(flag);
                        })
                .setPositiveButton(R.string.backup, (dialog, which) -> {
                    mode = MODE_BACKUP;
                    if (requestExternalStoragePermissions(activity)) {
                        handleMode();
                    }
                });
        if (backupCount == packageNames.size()) {
            builder.setNegativeButton(R.string.restore, (dialog, which) -> {
                mode = MODE_RESTORE;
                if (requestExternalStoragePermissions(activity)) {
                    handleMode();
                }
            }).setNeutralButton(R.string.delete_backup, (dialog, which) -> {
                mode = MODE_DELETE;
                if (requestExternalStoragePermissions(activity)) {
                    handleMode();
                }
            });
        }
        return builder.create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) handleMode();
    }

    public void handleMode() {
        @BatchOpsManager.OpType int op;
        switch (mode) {
            case MODE_DELETE:
                // TODO(11/9/20): Display a list of backups if only a single package is requested
                // TODO(18/9/20): Display a confirmation prompt
                op = BatchOpsManager.OP_DELETE_BACKUP;
                startOperation(op, null);
                break;
            case MODE_RESTORE:
                // TODO(11/9/20): Display a list of backups if only a single package is requested
                // TODO(18/9/20): Display a confirmation prompt
                op = BatchOpsManager.OP_RESTORE_BACKUP;
                startOperation(op, null);
                break;
            case MODE_BACKUP:
            default:
                op = BatchOpsManager.OP_BACKUP;
                if (flags.backupMultiple()) {
                    View view = activity.getLayoutInflater().inflate(R.layout.dialog_input_backup_name, null);
                    // TODO(18/9/20): Add overwrite option
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.backup)
                            .setView(view)
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                Editable backupName = ((TextInputEditText) view.findViewById(R.id.input_backup_name)).getText();
                                if (!TextUtils.isEmpty(backupName)) {
                                    //noinspection ConstantConditions backupName is never null here
                                    startOperation(op, new String[]{backupName.toString()});
                                } else startOperation(op, null);
                            })
                            .show();
                } else {
                    if (backupCount > 0) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.backup)
                                .setMessage(getResources().getQuantityString(R.plurals.backup_exists_are_you_sure, backupCount))
                                .setPositiveButton(R.string.yes, (dialog, which) -> startOperation(op, null))
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else {
                        startOperation(op, null);
                    }
                }
        }
    }

    private void startOperation(int op, @Nullable String[] backupNames) {
        if (actionBeginInterface != null) actionBeginInterface.onActionBegin(mode);
        activity.registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
        // Start batch ops service
        Intent intent = new Intent(activity, BatchOpsService.class);
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, new ArrayList<>(packageNames));
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        Bundle args = new Bundle();
        args.putInt(BatchOpsManager.ARG_FLAGS, flags.getFlags());
        args.putStringArray(BatchOpsManager.ARG_BACKUP_NAMES, backupNames);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(activity, intent);
    }
}
