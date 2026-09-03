// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * App Manager-owned permission overlays.
 */
@Entity(tableName = "permission_override", primaryKeys = {"package_name", "user_id", "permission_name"})
public class PermissionOverride {
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName;
    @ColumnInfo(name = "user_id")
    public int userId;
    @NonNull
    @ColumnInfo(name = "permission_name")
    public String permissionName;
    @ColumnInfo(name = "desired_granted")
    public boolean desiredGranted;
    @NonNull
    @ColumnInfo(name = "controller")
    public String controller = "";
    @ColumnInfo(name = "sync_status")
    public int syncStatus;
    @ColumnInfo(name = "sync_time")
    public long syncTime;

    public PermissionOverride() {
    }

    public PermissionOverride(@NonNull String packageName, int userId, @NonNull String permissionName,
                              boolean desiredGranted, @NonNull String controller) {
        this.packageName = packageName;
        this.userId = userId;
        this.permissionName = permissionName;
        this.desiredGranted = desiredGranted;
        this.controller = controller;
    }
}
