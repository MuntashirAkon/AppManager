// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.widget.MaterialAlertView;

public class BackupFragment extends Fragment {
    public static BackupFragment getInstance() {
        return new BackupFragment();
    }

    private BackupRestoreDialogViewModel mViewModel;
    private Context mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_backup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel.class);
        mContext = requireContext();

        MaterialAlertView messageView = view.findViewById(R.id.message);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        int supportedFlags = BackupFlags.getSupportedBackupFlags();
        // Remove unsupported flags
        supportedFlags &= ~BackupFlags.BACKUP_NO_SIGNATURE_CHECK;
        FlagsAdapter adapter = new FlagsAdapter(mContext, BackupFlags.fromPref().getFlags(), supportedFlags);
        recyclerView.setAdapter(adapter);

        Set<CharSequence> uninstalledApps = mViewModel.getUninstalledApps();
        if (uninstalledApps.size() > 0) {
            SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.backup_apps_cannot_be_backed_up));
            for (CharSequence appLabel : uninstalledApps) {
                sb.append("\n● ").append(appLabel);
            }
            messageView.setText(sb);
            messageView.setVisibility(View.VISIBLE);
        }
        view.findViewById(R.id.action_backup).setOnClickListener(v -> {
            BackupFlags newFlags = new BackupFlags(adapter.getSelectedFlags());
            handleBackup(newFlags);
        });
    }

    private void handleBackup(@NonNull BackupFlags flags) {
        BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
        operationInfo.mode = BackupRestoreDialogFragment.MODE_BACKUP;
        operationInfo.flags = flags.getFlags();
        operationInfo.op = BatchOpsManager.OP_BACKUP;
        if (flags.backupMultiple()) {
            // Multiple backup is requested, no need to warn users about backups since the
            // user has a choice between overwriting the existing backup or create a new one
            // TODO(18/9/20): Add overwrite option
            new TextInputDialogBuilder(mContext, R.string.input_backup_name)
                    .setTitle(R.string.backup)
                    .setHelperText(R.string.input_backup_name_description)
                    .setPositiveButton(R.string.ok, (dialog, which, backupName, isChecked) -> {
                        if (!TextUtils.isEmpty(backupName)) {
                            //noinspection ConstantConditions
                            operationInfo.backupNames = new String[]{backupName.toString()};
                        } else {
                            // No backup specified, remove the associated flag
                            operationInfo.flags &= ~BackupFlags.BACKUP_MULTIPLE;
                        }
                        mViewModel.prepareForOperation(operationInfo);
                    })
                    .show();
        } else {
            // Base backup requested
            int baseBackupCount = mViewModel.getBackupInfoList().size() - mViewModel.getAppsWithoutBackups().size();
            if (baseBackupCount > 0) {
                // One or more app has backups, warn users
                new MaterialAlertDialogBuilder(mContext)
                        .setTitle(R.string.backup)
                        .setMessage(getResources().getQuantityString(R.plurals.backup_exists_are_you_sure, baseBackupCount))
                        .setPositiveButton(R.string.yes, (dialog, which) -> mViewModel.prepareForOperation(operationInfo))
                        .setNegativeButton(R.string.no, null)
                        .show();
            } else {
                // No need to warn users, proceed to back up
                mViewModel.prepareForOperation(operationInfo);
            }
        }
    }
}
