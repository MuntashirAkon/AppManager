// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fm_favorite")
public class FmFavorite {
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    public long id;
    @ColumnInfo(name = "name")
    @NonNull
    public String name;
    @ColumnInfo(name = "uri")
    @NonNull
    public String uri;
    @ColumnInfo(name = "init_uri")
    @Nullable
    public String initUri;
    @ColumnInfo(name = "options")
    public int options;
    @ColumnInfo(name = "order")
    public long order;
    @ColumnInfo(name = "type")
    public int type;
}
