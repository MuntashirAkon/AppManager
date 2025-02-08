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
    @FreezeUtils.FreezeType
    public int type;
}
