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

    private CharSequence appLabel;
    @NonNull
    private List<MetadataManager.Metadata> backups = Collections.emptyList();
    private boolean installed;
    private boolean hasBaseBackup;

    BackupInfo(@NonNull String packageName, int userId) {
        this.packageName = packageName;
        this.userIds.add(userId);
        appLabel = packageName;
    }

    @NonNull
    public CharSequence getAppLabel() {
        return appLabel;
    }

    public void setAppLabel(@NonNull CharSequence appLabel) {
        this.appLabel = appLabel;
    }

    @NonNull
    public List<MetadataManager.Metadata> getBackups() {
        return backups;
    }

    public void setBackups(@NonNull List<MetadataManager.Metadata> backups) {
        this.backups = backups;
    }

    public boolean hasBaseBackup() {
        return hasBaseBackup;
    }

    public void setHasBaseBackup(boolean hasBaseBackup) {
        this.hasBaseBackup = hasBaseBackup;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }
}
