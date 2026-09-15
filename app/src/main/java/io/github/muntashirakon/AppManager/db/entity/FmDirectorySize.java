// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fm_directory_size")
public class FmDirectorySize {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "directory_key")
    public String directoryKey = "";

    @ColumnInfo(name = "size_bytes")
    public long sizeBytes;

    @ColumnInfo(name = "calculated_at")
    public long calculatedAt;

    @ColumnInfo(name = "last_used_at")
    public long lastUsedAt;
}
