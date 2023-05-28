// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
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
import io.github.muntashirakon.widget.MaterialAlertView;

public class RestoreMultipleFragment extends Fragment {
    @NonNull
    public static RestoreMultipleFragment getInstance() {
        return new RestoreMultipleFragment();
    }

    private BackupRestoreDialogViewModel mViewModel;
    private Context mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_restore_multiple, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel.class);
        mContext = requireContext();

        MaterialAlertView messageView = view.findViewById(R.id.message);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        int supportedFlags = mViewModel.getWorstBackupFlag();
        // Inject no signatures
        supportedFlags |= BackupFlags.BACKUP_NO_SIGNATURE_CHECK;
        supportedFlags |= BackupFlags.BACKUP_CUSTOM_USERS;
        int checkedFlags = BackupFlags.fromPref().getFlags() & supportedFlags;
        int disabledFlags = 0;
        if (mViewModel.getUninstalledApps().size() > 0) {
            checkedFlags |= BackupFlags.BACKUP_APK_FILES;
            disabledFlags |= BackupFlags.BACKUP_APK_FILES;
        }
        FlagsAdapter adapter = new FlagsAdapter(mContext, checkedFlags, supportedFlags, disabledFlags);
        recyclerView.setAdapter(adapter);

        Set<CharSequence> appsWithoutBackups = mViewModel.getAppsWithoutBackups();
        if (appsWithoutBackups.size() > 0) {
            SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.backup_apps_cannot_be_restored));
            for (CharSequence appLabel : appsWithoutBackups) {
                sb.append("\n● ").append(appLabel);
            }
            messageView.setText(sb);
            messageView.setVisibility(View.VISIBLE);
        }
        view.findViewById(R.id.action_restore).setOnClickListener(v -> {
            int newFlags = adapter.getSelectedFlags();
            handleRestore(newFlags);
        });
    }

    private void handleRestore(int flags) {
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.restore)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_RESTORE;
                    operationInfo.op = BatchOpsManager.OP_RESTORE_BACKUP;
                    operationInfo.flags = flags;
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
