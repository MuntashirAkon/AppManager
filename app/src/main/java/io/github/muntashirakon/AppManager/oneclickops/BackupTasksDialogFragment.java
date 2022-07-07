// SPDX-License-Identifier: GPL-3.0-or-later

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Paths;

public class BackupTasksDialogFragment extends DialogFragment {
    public static final String TAG = "BackupTasksDialogFragment";

    private OneClickOpsActivity activity;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

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
            activity.mProgressIndicator.show();
            executor.submit(() -> {
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), null, true)) {
                    if (isDetached() || Thread.currentThread().isInterrupted()) return;
                    if (item.isInstalled) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            });
        });
        // Redo existing backups for the installed apps
        view.findViewById(R.id.redo_existing_backups).setOnClickListener(v -> {
            activity.mProgressIndicator.show();
            executor.submit(() -> {
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), null, true)) {
                    if (isDetached() || Thread.currentThread().isInterrupted()) return;
                    if (item.isInstalled && item.backup != null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            });
        });
        // Backup apps without any previous backups
        view.findViewById(R.id.backup_apps_without_backup).setOnClickListener(v -> {
            activity.mProgressIndicator.show();
            executor.submit(() -> {
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), null, true)) {
                    if (isDetached() || Thread.currentThread().isInterrupted()) return;
                    if (item.isInstalled && item.backup == null) {
                        applicationItems.add(item);
                        applicationLabels.add(item.label);
                    }
                }
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            });
        });
        view.findViewById(R.id.verify_and_redo_backups).setOnClickListener(v -> {
            activity.mProgressIndicator.show();
            executor.submit(() -> {
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                Backup backup;
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), null, true)) {
                    if (isDetached() || Thread.currentThread().isInterrupted()) return;
                    backup = item.backup;
                    if (backup == null || !item.isInstalled) continue;
                    try {
                        BackupManager.getNewInstance(new UserPackagePair(item.packageName, backup.userId),
                                0).verify(backup.backupName);
                    } catch (Throwable e) {
                        applicationItems.add(item);
                        applicationLabels.add(new SpannableStringBuilder(backup.label)
                                .append(LangUtils.getSeparatorString())
                                .append(backup.backupName)
                                .append('\n')
                                .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(activity,
                                        new SpannableStringBuilder(backup.packageName)
                                                .append('\n')
                                                .append(e.getMessage())))));
                    }
                }
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            });
        });
        view.findViewById(R.id.backup_apps_with_changes).setOnClickListener(v -> {
            activity.mProgressIndicator.show();
            executor.submit(() -> {
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                List<ApplicationItem> applicationItems = new ArrayList<>();
                List<CharSequence> applicationLabels = new ArrayList<>();
                Backup backup;
                for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), null, true)) {
                    if (isDetached() || Thread.currentThread().isInterrupted()) return;
                    backup = item.backup;
                    if (backup == null) continue;
                    // Checks
                    // 0. App is installed (Skip backup)
                    if (!item.isInstalled) continue;
                    // 1. App version code and 2. last update date (Whether to backup source)
                    boolean needSourceUpdate = item.versionCode > backup.versionCode
                            || item.lastUpdateTime > backup.backupTime;
                    if (needSourceUpdate
                            // 3. Last activity date
                            || AppUsageStatsManager.getLastActivityTime(item.packageName, new UsageUtils.TimeInterval(
                            backup.backupTime, System.currentTimeMillis())) > backup.backupTime
                            // 4. Check integrity
                            || !isVerified(item, backup)) {
                        // 5. Check hash
                        try {
                            List<String> changedDirs = new ArrayList<>();
                            for (String dir : backup.getMetadata().dataDirs) {
                                String hash = AppManager.getAppsDb().fileHashDao().getHash(dir);
                                // For now, if hash is null, don't proceed to backup
                                if (hash == null) {
                                    break;
                                }
                                String newHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, Paths.get(dir));
                                if (!hash.equals(newHash)) changedDirs.add(dir);
                            }
                            // TODO: 23/4/21 Support delta backup
                        } catch (IOException ignore) {
                        }
                        applicationItems.add(item);
                        applicationLabels.add(new SpannableStringBuilder().append(backup.label)
                                .append(LangUtils.getSeparatorString())
                                .append(backup.backupName)
                                .append('\n')
                                .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(activity, backup.packageName))));
                    }
                }
                if (isDetached() || Thread.currentThread().isInterrupted()) return;
                requireActivity().runOnUiThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
            });
        });
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
                .addSelections(applicationItems)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.back_up, (dialog, which, selectedItems) -> {
                    if (isDetached()) return;
                    BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                            PackageUtils.getUserPackagePairs(selectedItems), BackupRestoreDialogFragment.MODE_BACKUP);
                    fragment.setOnActionBeginListener(mode -> activity.mProgressIndicator.show());
                    fragment.setOnActionCompleteListener((mode, failedPackages) -> activity.mProgressIndicator.hide());
                    if (isDetached()) return;
                    fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private boolean isVerified(ApplicationItem item, Backup backup) {
        try {
            BackupManager.getNewInstance(new UserPackagePair(item.packageName, backup.userId),
                    0).verify(backup.backupName);
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private ArrayList<UserPackagePair> getUserPackagePairs(List<MetadataManager.Metadata> metadataList) {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>(metadataList.size());
        for (MetadataManager.Metadata metadata : metadataList) {
            userPackagePairs.add(new UserPackagePair(metadata.packageName, metadata.userHandle));
        }
        return userPackagePairs;
    }
}
