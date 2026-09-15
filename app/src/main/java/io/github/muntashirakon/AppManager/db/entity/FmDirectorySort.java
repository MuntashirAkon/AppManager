// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fm_directory_sort")
public class FmDirectorySort {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "directory_key")
    public String directoryKey = "";

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @ColumnInfo(name = "reverse_sort")
    public boolean reverseSort;

    @ColumnInfo(name = "last_used_at")
    public long lastUsedAt;
}
