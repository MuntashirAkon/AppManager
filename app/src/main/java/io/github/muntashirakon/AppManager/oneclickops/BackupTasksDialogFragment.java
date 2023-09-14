// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.io.Paths;

public class BackupTasksDialogFragment extends DialogFragment {
    public static final String TAG = "BackupTasksDialogFragment";

    private OneClickOpsActivity mActivity;
    private Future<?> mFuture;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mActivity = (OneClickOpsActivity) requireActivity();
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_backup_tasks, null);
        // Backup all installed apps
        view.findViewById(R.id.backup_all).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (item.isInstalled) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        // Redo existing backups for the installed apps
        view.findViewById(R.id.redo_existing_backups).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (item.isInstalled && item.backup != null) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        // Backup apps without any previous backups
        view.findViewById(R.id.backup_apps_without_backup).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (item.isInstalled && item.backup == null) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        view.findViewById(R.id.verify_and_redo_backups).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    Backup backup;
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
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
                                    .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(mActivity,
                                            new SpannableStringBuilder(backup.packageName)
                                                    .append('\n')
                                                    .append(e.getMessage())))));
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        view.findViewById(R.id.backup_apps_with_changes).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
                    Backup backup;
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        backup = item.backup;
                        if (backup == null) continue;
                        // Checks
                        // 0. App is installed (Skip backup)
                        if (!item.isInstalled) continue;
                        // 1. App version code and 2. last update date (Whether to back up source)
                        boolean needSourceUpdate = item.versionCode > backup.versionCode
                                || item.lastUpdateTime > backup.backupTime;
                        if (needSourceUpdate
                                // 3. Last activity date
                                || (hasUsageAccess && AppUsageStatsManager.getLastActivityTime(item.packageName,
                                new UsageUtils.TimeInterval(backup.backupTime, System.currentTimeMillis())) > backup.backupTime)
                                // 4. Check integrity
                                || !isVerified(item, backup)) {
                            // 5. Check hash
                            try {
                                List<String> changedDirs = new ArrayList<>();
                                for (String dir : backup.getMetadata().dataDirs) {
                                    String hash = AppsDb.getInstance().fileHashDao().getHash(dir);
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
                                    .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(mActivity, backup.packageName))));
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        return new MaterialAlertDialogBuilder(mActivity)
                .setView(view)
                .setTitle(R.string.back_up)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @UiThread
    private void runMultiChoiceDialog(List<ApplicationItem> applicationItems, List<CharSequence> applicationLabels) {
        if (isDetached()) return;
        mActivity.progressIndicator.hide();
        new SearchableMultiChoiceDialogBuilder<>(mActivity, applicationItems, applicationLabels)
                .addSelections(applicationItems)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.back_up, (dialog, which, selectedItems) -> {
                    if (isDetached()) return;
                    BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                            PackageUtils.getUserPackagePairs(selectedItems), BackupRestoreDialogFragment.MODE_BACKUP);
                    fragment.setOnActionBeginListener(mode -> mActivity.progressIndicator.show());
                    fragment.setOnActionCompleteListener((mode, failedPackages) -> mActivity.progressIndicator.hide());
                    if (isDetached()) return;
                    fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroy() {
        if (mFuture != null) {
            mFuture.cancel(true);
        }
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
}
