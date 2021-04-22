package io.github.muntashirakon.AppManager.oneclickops;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class BackupTasksDialogFragment extends DialogFragment {
    public static final String TAG = "BackupTasksDialogFragment";

    private OneClickOpsActivity activity;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = (OneClickOpsActivity) requireActivity();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_backup_tasks, null);
        // Backup all installed apps
        view.findViewById(R.id.backup_all).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (item.isInstalled) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        // Redo existing backups for the installed apps
        view.findViewById(R.id.redo_existing_backups).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (item.isInstalled && item.metadata != null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        // Backup apps without any previous backups
        view.findViewById(R.id.backup_apps_without_backup).setOnClickListener(v -> {
            if (isDetached()) return;
            activity.mProgressIndicator.show();
            new Thread(() -> {
                if (isDetached()) return;
                HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                    if (isDetached()) return;
                    if (item.isInstalled && item.metadata == null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            }).start();
        });
        if (BuildConfig.DEBUG) {
            view.findViewById(R.id.verify_and_redo_backups).setOnClickListener(v -> {
                if (isDetached()) return;
                activity.mProgressIndicator.show();
                new Thread(() -> {
                    if (isDetached()) return;
                    HashMap<String, MetadataManager.Metadata> backupMetadata = BackupUtils.getAllBackupMetadata();
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    MetadataManager.Metadata metadata;
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), backupMetadata)) {
                        if (isDetached()) return;
                        metadata = item.metadata;
                        if (metadata != null) {
                            try {
                                BackupManager.getNewInstance(new UserPackagePair(item.packageName, metadata.userHandle),
                                        0).verify(metadata.backupName);
                            } catch (Throwable e) {
                                applicationItems.add(item);
                                applicationLabels.add(new SpannableStringBuilder(UIUtils.getPrimaryText(activity,
                                        metadata.label + ": " + metadata.backupName)).append('\n').append(UIUtils
                                        .getSmallerText(UIUtils.getSecondaryText(activity, new SpannableStringBuilder(
                                                metadata.packageName).append('\n').append(e.getMessage())))));
                            }
                        }
                    }
                    if (isDetached()) return;
                    requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                }).start();
            });
            view.findViewById(R.id.backup_apps_with_changes).setOnClickListener(v -> {
                // TODO(14/1/21): Backup apps with changes
            });
        } else {
            view.findViewById(R.id.verify_and_redo_backups).setVisibility(View.GONE);
            view.findViewById(R.id.backup_apps_with_changes).setVisibility(View.GONE);
        }
        return new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setTitle(R.string.back_up)
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
                .setPositiveButton(R.string.back_up, (dialog, which, selectedItems) -> {
                    if (isDetached()) return;
                    BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
                    Bundle args = new Bundle();
                    args.putParcelableArrayList(BackupDialogFragment.ARG_PACKAGE_PAIRS,
                            PackageUtils.getUserPackagePairs(selectedItems));
                    args.putInt(BackupDialogFragment.ARG_CUSTOM_MODE, BackupDialogFragment.MODE_BACKUP);
                    backupDialogFragment.setArguments(args);
                    backupDialogFragment.setOnActionBeginListener(mode -> activity.mProgressIndicator.show());
                    backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> activity.mProgressIndicator.hide());
                    if (isDetached()) return;
                    backupDialogFragment.show(getParentFragmentManager(), BackupDialogFragment.TAG);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private ArrayList<UserPackagePair> getUserPackagePairs(List<MetadataManager.Metadata> metadataList) {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>(metadataList.size());
        for (MetadataManager.Metadata metadata : metadataList) {
            userPackagePairs.add(new UserPackagePair(metadata.packageName, metadata.userHandle));
        }
        return userPackagePairs;
    }
}
