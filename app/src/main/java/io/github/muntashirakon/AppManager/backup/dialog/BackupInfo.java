// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.MetadataManager;

class BackupInfo {
    @NonNull
    public final String packageName;
    @NonNull
    public final ArraySet<Integer> userIds = new ArraySet<>();

    private CharSequence mAppLabel;
    @NonNull
    private List<MetadataManager.Metadata> mBackups = Collections.emptyList();
    private boolean mInstalled;
    private boolean mHasBaseBackup;

    BackupInfo(@NonNull String packageName, int userId) {
        this.packageName = packageName;
        this.userIds.add(userId);
        mAppLabel = packageName;
    }

    @NonNull
    public CharSequence getAppLabel() {
        return mAppLabel;
    }

    public void setAppLabel(@NonNull CharSequence appLabel) {
        mAppLabel = appLabel;
    }

    @NonNull
    public List<MetadataManager.Metadata> getBackups() {
        return mBackups;
    }

    public void setBackups(@NonNull List<MetadataManager.Metadata> backups) {
        mBackups = backups;
    }

    public boolean hasBaseBackup() {
        return mHasBaseBackup;
    }

    public void setHasBaseBackup(boolean hasBaseBackup) {
        mHasBaseBackup = hasBaseBackup;
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public void setInstalled(boolean installed) {
        mInstalled = installed;
    }
}
