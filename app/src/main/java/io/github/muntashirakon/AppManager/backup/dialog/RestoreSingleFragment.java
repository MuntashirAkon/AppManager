// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;

public class RestoreSingleFragment extends Fragment {
    public static RestoreSingleFragment getInstance() {
        return new RestoreSingleFragment();
    }

    private BackupRestoreDialogViewModel mViewModel;
    private Context mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_restore_single, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel.class);
        mContext = requireContext();

        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        MaterialButton restoreButton = view.findViewById(R.id.action_restore);
        MaterialButton deleteButton = view.findViewById(R.id.action_delete);
        MaterialButton moreButton = view.findViewById(R.id.more);

        recyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        BackupAdapter adapter = new BackupAdapter(mContext, mViewModel.getBackupInfo().getBackups(),
                (metadata, selectionCount, added) -> {
                    restoreButton.setEnabled(selectionCount == 1);
                    deleteButton.setEnabled(selectionCount > 0);
                });
        recyclerView.setAdapter(adapter);

        restoreButton.setOnClickListener(v -> handleRestore(adapter.getSelectedBackups().get(0)));
        deleteButton.setOnClickListener(v -> handleDelete(adapter.getSelectedBackups()));
        moreButton.setOnClickListener(v -> {
            int total = adapter.selectionCount();
            int frozenCount = adapter.getFrozenBackupSelectionCount();

            PopupMenu popupMenu = new PopupMenu(mContext, v);
            Menu menu = popupMenu.getMenu();
            MenuItem freezeMenuItem = menu.add(R.string.freeze);
            MenuItem unfreezeMenuItem = menu.add(R.string.unfreeze);

            freezeMenuItem.setEnabled((total - frozenCount) > 0);
            unfreezeMenuItem.setEnabled(frozenCount > 0);

            freezeMenuItem.setOnMenuItemClickListener(item -> {
                List<MetadataManager.Metadata> selectedBackups = adapter.getSelectedBackups();
                for (MetadataManager.Metadata metadata : selectedBackups) {
                    if (metadata.backupFile != null) {
                        try {
                            metadata.backupFile.freeze();
                            ++adapter.mFrozenBackupSelectionCount;
                        } catch (IOException ignore) {
                        }
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                return true;
            });
            unfreezeMenuItem.setOnMenuItemClickListener(item -> {
                List<MetadataManager.Metadata> selectedBackups = adapter.getSelectedBackups();
                for (MetadataManager.Metadata metadata : selectedBackups) {
                    if (metadata.backupFile != null) {
                        try {
                            metadata.backupFile.unfreeze();
                            --adapter.mFrozenBackupSelectionCount;
                        } catch (IOException ignore) {
                        }
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                return true;
            });
            popupMenu.show();
        });
    }

    private void handleRestore(@NonNull MetadataManager.Metadata selectedBackup) {
        BackupFlags flags = selectedBackup.flags;
        BackupFlags enabledFlags = BackupFlags.fromPref();
        enabledFlags.setFlags(flags.getFlags() & enabledFlags.getFlags());
        List<Integer> supportedBackupFlags = BackupFlags.getBackupFlagsAsArray(flags.getFlags());
        // Inject no signatures
        supportedBackupFlags.add(BackupFlags.BACKUP_NO_SIGNATURE_CHECK);
        supportedBackupFlags.add(BackupFlags.BACKUP_CUSTOM_USERS);
        List<Integer> disabledFlags = new ArrayList<>();
        if (!mViewModel.getBackupInfo().isInstalled()) {
            enabledFlags.addFlag(BackupFlags.BACKUP_APK_FILES);
            disabledFlags.add(BackupFlags.BACKUP_APK_FILES);
        }
        new SearchableFlagsDialogBuilder<>(mContext, supportedBackupFlags, BackupFlags.getFormattedFlagNames(mContext, supportedBackupFlags), enabledFlags.getFlags())
                .setTitle(R.string.backup_options)
                .addDisabledItems(disabledFlags)
                .setPositiveButton(R.string.restore, (dialog, which, selections) -> {
                    int newFlags = 0;
                    for (int flag : selections) {
                        newFlags |= flag;
                    }
                    enabledFlags.setFlags(newFlags);

                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_RESTORE;
                    operationInfo.op = BatchOpsManager.OP_RESTORE_BACKUP;
                    operationInfo.flags = enabledFlags.getFlags();
                    operationInfo.backupNames = new String[]{selectedBackup.backupName};
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleDelete(List<MetadataManager.Metadata> selectedBackups) {
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.delete_backup)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    List<String> newBackupNames = new ArrayList<>(selectedBackups.size());
                    for (MetadataManager.Metadata backup : selectedBackups) {
                        newBackupNames.add(backup.backupName);
                    }
                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_DELETE;
                    operationInfo.op = BatchOpsManager.OP_DELETE_BACKUP;
                    operationInfo.backupNames = newBackupNames.toArray(new String[0]);
                    mViewModel.prepareForOperation(operationInfo);
                })
                .show();
    }

    private static class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.ViewHolder> {
        public interface OnSelectionListener {
            void onSelectionChanged(@Nullable MetadataManager.Metadata metadata, int selectionCount, boolean added);
        }

        private final int mLayoutId;
        @NonNull
        private final List<MetadataManager.Metadata> mBackups = new ArrayList<>();
        @NonNull
        private final List<Integer> mSelectedPositions = new ArrayList<>();
        @NonNull
        private final OnSelectionListener mSelectionListener;

        private int mFrozenBackupSelectionCount = 0;

        @SuppressLint("RestrictedApi")
        public BackupAdapter(@NonNull Context context, @NonNull List<MetadataManager.Metadata> backups,
                             @NonNull OnSelectionListener selectionListener) {
            mSelectionListener = selectionListener;
            mLayoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.multiChoiceItemLayout,
                    com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice);
            mSelectionListener.onSelectionChanged(null, mSelectedPositions.size(), false);
            for (int i = 0; i < backups.size(); ++i) {
                MetadataManager.Metadata backup = backups.get(i);
                mBackups.add(backup);
                if (backup.isBaseBackup()) {
                    mSelectedPositions.add(i);
                    if (backup.isFrozen()) {
                        ++mFrozenBackupSelectionCount;
                    }
                    mSelectionListener.onSelectionChanged(backup, mSelectedPositions.size(), true);
                }
            }
            notifyItemRangeInserted(0, mBackups.size());
        }

        public int selectionCount() {
            return mSelectedPositions.size();
        }

        public int getFrozenBackupSelectionCount() {
            return mFrozenBackupSelectionCount;
        }

        @NonNull
        public List<MetadataManager.Metadata> getSelectedBackups() {
            List<MetadataManager.Metadata> selectedBackups = new ArrayList<>();
            for (int position : mSelectedPositions) {
                selectedBackups.add(mBackups.get(position));
            }
            return selectedBackups;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MetadataManager.Metadata metadata = mBackups.get(position);
            boolean isSelected = mSelectedPositions.contains(position);
            holder.item.setChecked(isSelected);
            holder.item.setText(metadata.toLocalizedString(holder.item.getContext()));
            holder.item.setOnClickListener(v -> {
                if (isSelected) {
                    // Now unselected
                    mSelectedPositions.remove((Integer) position);
                    if (metadata.isFrozen()) {
                        --mFrozenBackupSelectionCount;
                    }
                    mSelectionListener.onSelectionChanged(metadata, mSelectedPositions.size(), false);
                } else {
                    // Now selected
                    mSelectedPositions.add(position);
                    if (metadata.isFrozen()) {
                        ++mFrozenBackupSelectionCount;
                    }
                    mSelectionListener.onSelectionChanged(metadata, mSelectedPositions.size(), true);
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return mBackups.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
                // textAppearanceBodyLarge
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTextColor(UIUtils.getTextColorSecondary(item.getContext()));
            }
        }
    }
}
