// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "op_history")
public class OpHistory {
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "type")
    @NonNull
    public String type;

    @ColumnInfo(name = "time")
    public long execTime;

    @ColumnInfo(name = "data")
    @NonNull
    public String serializedData;

    @ColumnInfo(name = "status")
    @NonNull
    public String status;

    @ColumnInfo(name = "extra")
    @Nullable
    public String serializedExtra;
}
