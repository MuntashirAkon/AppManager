package io.github.muntashirakon.AppManager.oneclickops;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class RestoreTasksDialogFragment extends DialogFragment {
    public static final String TAG = "RestoreTasksDialogFragment";

    private OneClickOpsActivity activity;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = (OneClickOpsActivity) requireActivity();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_restore_tasks, null);
        // Restore all apps
        view.findViewById(R.id.restore_all).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (item.metadata != null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        // Restore not installed
        view.findViewById(R.id.restore_not_installed).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (!item.isInstalled && item.metadata != null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        // Restore latest versions only
        view.findViewById(R.id.restore_latest).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (item.isInstalled && item.metadata != null
                            && item.versionCode < item.metadata.versionCode) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(view)
                .setTitle(R.string.restore)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @UiThread
    private void runMultiChoiceDialog(List<ApplicationItem> applicationItems, List<CharSequence> applicationLabels) {
        if (isDetached()) return;
        activity.mProgressIndicator.hide();
        new SearchableMultiChoiceDialogBuilder<>(activity, applicationItems, applicationLabels)
                .setSelections(applicationItems)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.restore, (dialog, which, selectedItems) -> {
                    if (isDetached()) return;
                    BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
                    Bundle args = new Bundle();
                    args.putParcelableArrayList(BackupDialogFragment.ARG_PACKAGE_PAIRS,
                            PackageUtils.getUserPackagePairs(selectedItems));
                    args.putInt(BackupDialogFragment.ARG_CUSTOM_MODE, BackupDialogFragment.MODE_RESTORE);
                    backupDialogFragment.setArguments(args);
                    backupDialogFragment.setOnActionBeginListener(mode -> activity.mProgressIndicator.show());
                    backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> activity.mProgressIndicator.hide());
                    if (isDetached()) return;
                    backupDialogFragment.show(getParentFragmentManager(), BackupDialogFragment.TAG);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
