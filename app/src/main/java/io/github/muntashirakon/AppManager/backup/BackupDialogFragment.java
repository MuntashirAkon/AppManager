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
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupDialogFragment extends DialogFragment {
    public static final String TAG = "BackupDialogFragment";
    public static final String ARG_PACKAGE_PAIRS = "ARG_PACKAGE_PAIRS";
    public static final String ARG_CUSTOM_MODE = "ARG_CUSTOM_MODE";

    @IntDef(flag = true, value = {
            MODE_BACKUP,
            MODE_RESTORE,
            MODE_DELETE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionMode {
    }

    public static final int MODE_BACKUP = 1;
    public static final int MODE_RESTORE = 1 << 1;
    public static final int MODE_DELETE = 1 << 2;

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
    @ActionMode
    private int mode = MODE_BACKUP;
    private List<UserPackagePair> targetPackages;
    private int baseBackupCount = 0;
    private boolean permsGranted = false;
    private FragmentActivity activity;

    private final BackupFlags flags = BackupFlags.fromPref();
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (actionCompleteInterface != null) {
                BatchOpsManager.Result result = BatchOpsManager.getLastResult();
                actionCompleteInterface.onActionComplete(mode, result != null ? result.getFailedPackages().toArray(new String[0]) : new String[0]);
            }
            activity.unregisterReceiver(mBatchOpsBroadCastReceiver);
        }
    };

    public void setOnActionCompleteListener(@NonNull ActionCompleteInterface actionCompleteInterface) {
        this.actionCompleteInterface = actionCompleteInterface;
    }

    public void setOnActionBeginListener(@NonNull ActionBeginInterface actionBeginInterface) {
        this.actionBeginInterface = actionBeginInterface;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = requireActivity();
        storagePermission.request(granted -> permsGranted = granted);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        Bundle args = requireArguments();
        targetPackages = args.getParcelableArrayList(ARG_PACKAGE_PAIRS);
        int customModes = args.getInt(ARG_CUSTOM_MODE, MODE_BACKUP | MODE_RESTORE | MODE_DELETE);
        if (targetPackages == null) return super.onCreateDialog(savedInstanceState);

        if (targetPackages.size() == 1) {
            // Check for all meta, not just the base since we are going to display every backups
            if (MetadataManager.hasAnyMetadata(targetPackages.get(0).getPackageName())) {
                ++baseBackupCount;
            }
        } else {
            // Check if only for base meta
            for (UserPackagePair userPackagePair : targetPackages) {
                if (MetadataManager.hasBaseMetadata(userPackagePair.getPackageName())) {
                    ++baseBackupCount;
                }
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(targetPackages.size() == 1 ? PackageUtils.getPackageLabel(activity
                        .getPackageManager(), targetPackages.get(0).getPackageName())
                        : getString(R.string.backup_options))
                .setMultiChoiceItems(BackupFlags.getFormattedFlagNames(activity),
                        flags.flagsToCheckedItems(),
                        (dialog, index, isChecked) -> {
                            if (isChecked) flags.addFlag(BackupFlags.backupFlags.get(index));
                            else flags.removeFlag(BackupFlags.backupFlags.get(index));
                        })
                .setPositiveButton(R.string.backup, null)
                .setNegativeButton(R.string.restore, null)
                .setNeutralButton(R.string.delete_backup, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button positiveButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutralButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            if ((customModes & MODE_BACKUP) != 0) {
                positiveButton.setOnClickListener(v -> {
                    mode = MODE_BACKUP;
                    if (permsGranted) handleCustomUsers();
                });
            } else {
                positiveButton.setVisibility(View.GONE);
            }
            // Display restore and delete only if backups of all the selected package exist
            if ((customModes & MODE_RESTORE) != 0 && baseBackupCount == targetPackages.size()) {
                negativeButton.setOnClickListener(v -> {
                    mode = MODE_RESTORE;
                    if (permsGranted) handleCustomUsers();
                });
            } else {
                negativeButton.setVisibility(View.GONE);
            }
            if ((customModes & MODE_DELETE) != 0 && baseBackupCount == targetPackages.size()) {
                neutralButton.setOnClickListener(v -> {
                    mode = MODE_DELETE;
                    if (permsGranted) handleCustomUsers();
                });
            } else {
                neutralButton.setVisibility(View.GONE);
            }
        });
        return alertDialog;
    }

    public void handleCustomUsers() {
        if (flags.backupCustomUsers()) {
            new Thread(() -> {
                List<UserInfo> users = Users.getUsers();
                if (users != null && users.size() > 1) {
                    CharSequence[] userNames = new String[users.size()];
                    List<Integer> userHandles = new ArrayList<>(users.size());
                    int i = 0;
                    for (UserInfo info : users) {
                        userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                        userHandles.add(info.id);
                        ++i;
                    }
                    activity.runOnUiThread(() -> new SearchableMultiChoiceDialogBuilder<>(activity, userHandles, userNames)
                            .setTitle(R.string.select_user)
                            .setSelections(Collections.singletonList(Users.getCurrentUserHandle()))
                            .setPositiveButton(R.string.ok, (dialog, which, selectedUsers) -> {
                                List<UserPackagePair> newTargetPackages = new ArrayList<>();
                                List<String> visitedPackages = new ArrayList<>();
                                for (UserPackagePair pair : targetPackages) {
                                    if (visitedPackages.contains(pair.getPackageName())) continue;
                                    for (int user : selectedUsers) {
                                        newTargetPackages.add(new UserPackagePair(pair.getPackageName(), user));
                                    }
                                    visitedPackages.add(pair.getPackageName());
                                }
                                targetPackages = newTargetPackages;
                                handleMode();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show());
                } else {
                    activity.runOnUiThread(this::handleMode);
                }
            }).start();
        } else handleMode();
    }

    private void handleMode() {
        switch (mode) {
            case MODE_DELETE:
                handleDelete();
                break;
            case MODE_RESTORE:
                handleRestore();
                break;
            case MODE_BACKUP:
            default:
                handleBackup();
        }
    }

    private void handleDelete() {
        @BatchOpsManager.OpType int op = BatchOpsManager.OP_DELETE_BACKUP;
        if (targetPackages.size() == 1) {
            // Only a single package is requested, display a list of existing backups to
            // choose which of them are to be deleted
            // TODO(21/9/20): Replace with a custom alert dialog to display more info.
            MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(targetPackages.get(0).getPackageName());
            String[] backupNames = new String[metadata.length];
            String[] readableBackupNames = new String[metadata.length];
            boolean[] choices = new boolean[metadata.length];
            Arrays.fill(choices, false);
            String backupName;
            String userHandle;
            for (int i = 0; i < backupNames.length; ++i) {
                backupNames[i] = metadata[i].backupName;
                backupName = BackupUtils.getShortBackupName(backupNames[i]);
                userHandle = String.valueOf(metadata[i].userHandle);
                readableBackupNames[i] = backupName == null ? "Base backup for user " + userHandle : backupName + " for user " + userHandle;
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(PackageUtils.getPackageLabel(activity.getPackageManager(), targetPackages.get(0).getPackageName()))
                    .setMultiChoiceItems(readableBackupNames, choices,
                            (dialog, which, isChecked) -> choices[which] = isChecked)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_backup, (dialog, which) -> {
                        List<String> newBackupNames = new ArrayList<>(backupNames.length);
                        for (int i = 0; i < backupNames.length; ++i) {
                            if (choices[i] && backupNames[i] != null) {
                                newBackupNames.add(backupNames[i]);
                            }
                        }
                        // backupNames arguments must not be null!!
                        startOperation(op, newBackupNames.toArray(new String[0]));
                    })
                    .show();
        } else if (baseBackupCount == targetPackages.size()) {
            // We shouldn't even check this since the restore option will only be visible
            // if backup of all the packages exist
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.delete_backup)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, (dialog, which) -> startOperation(op, null))
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            Log.e(TAG, "Delete: Why are we even here? Backup count " + baseBackupCount);
        }
    }

    private void handleRestore() {
        @BatchOpsManager.OpType int op = BatchOpsManager.OP_RESTORE_BACKUP;
        if (targetPackages.size() == 1) {
            // Only a single package is requested, display a list of existing backups to
            // choose which one to restore
            // TODO(21/9/20): Replace with a custom alert dialog to display more info.
            MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(targetPackages.get(0).getPackageName());
            String[] backupNames = new String[metadata.length];
            AtomicInteger selectedItem = new AtomicInteger(-1);
            String[] readableBackupNames = new String[metadata.length];
            String backupName;
            int userHandle;
            int choice = -1;
            int currentUserHandle = Users.getCurrentUserHandle();
            for (int i = 0; i < backupNames.length; ++i) {
                backupNames[i] = metadata[i].backupName;
                backupName = BackupUtils.getShortBackupName(backupNames[i]);
                userHandle = metadata[i].userHandle;
                if (backupName == null && userHandle == currentUserHandle) {
                    choice = i;
                    selectedItem.set(i);
                }
                readableBackupNames[i] = backupName == null ? "Base backup for user " + userHandle : backupName + " for user " + userHandle;
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(PackageUtils.getPackageLabel(activity.getPackageManager(), targetPackages.get(0).getPackageName()))
                    .setSingleChoiceItems(readableBackupNames, choice, (dialog, which) -> selectedItem.set(which))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.restore, (dialog, which) -> {
                        if (selectedItem.get() != -1) {
                            // Do operation only if something is selected
                            // Send a singleton array consisting of the chosen backup name.
                            // Here, the backup name is the full name of the folder itself,
                            // not just the “short” ie. the user handle is not stripped and
                            // the argument must not be null.
                            startOperation(op, new String[]{backupNames[selectedItem.get()]});
                        }
                    })
                    .show();
        } else if (baseBackupCount == targetPackages.size()) {
            // We shouldn't even check this since the restore option will only be visible
            // if backup of all the packages exist
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.restore)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, (dialog, which) -> startOperation(op, null))
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            Log.e(TAG, "Restore: Why are we even here? Backup count " + baseBackupCount);
        }
    }

    private void handleBackup() {
        @BatchOpsManager.OpType int op = BatchOpsManager.OP_BACKUP;
        if (flags.backupMultiple()) {
            // Multiple backup is requested, no need to warn users about backups since the
            // user has a choice between overwriting the existing backup or create a new one
            // TODO(18/9/20): Add overwrite option
            new TextInputDialogBuilder(activity, R.string.input_backup_name)
                    .setTitle(R.string.backup)
                    .setHelperText(R.string.input_backup_name_description)
                    .setPositiveButton(R.string.ok, (dialog, which, backupName, isChecked) -> {
                        if (!TextUtils.isEmpty(backupName)) {
                            //noinspection ConstantConditions
                            startOperation(op, new String[]{backupName.toString()});
                        } else startOperation(op, null);
                    })
                    .show();
        } else {
            // Base backup requested
            if (baseBackupCount > 0) {
                // One or more app has backups, warn users
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.backup)
                        .setMessage(getResources().getQuantityString(R.plurals.backup_exists_are_you_sure, baseBackupCount))
                        .setPositiveButton(R.string.yes, (dialog, which) -> startOperation(op, null))
                        .setNegativeButton(R.string.no, null)
                        .show();
            } else {
                // No need to warn users, proceed to backup
                startOperation(op, null);
            }
        }
    }

    private void startOperation(int op, @Nullable String[] backupNames) {
        if (actionBeginInterface != null) actionBeginInterface.onActionBegin(mode);
        activity.registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
        // Start batch ops service
        Intent intent = new Intent(activity, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(targetPackages);
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        Bundle args = new Bundle();
        args.putInt(BatchOpsManager.ARG_FLAGS, flags.getFlags());
        args.putStringArray(BatchOpsManager.ARG_BACKUP_NAMES, backupNames);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(activity, intent);
        dismiss();
    }
}
