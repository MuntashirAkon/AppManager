// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import io.github.muntashirakon.AppManager.utils.FreezeUtils;

@Entity(tableName = "freeze_type")
public class FreezeType {
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName;

    @ColumnInfo(name = "type")
    @FreezeUtils.FreezeMethod
    public int type;

    public FreezeType() {}

    public FreezeType(@NonNull String packageName, @FreezeUtils.FreezeMethod int type) {
        this.packageName = packageName;
        this.type = type;
    }
}
