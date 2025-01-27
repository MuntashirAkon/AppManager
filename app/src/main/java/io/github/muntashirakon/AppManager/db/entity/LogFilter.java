// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Comparator;

import io.github.muntashirakon.AppManager.utils.AlphanumComparator;

@Entity(tableName = "log_filter", indices = {@Index(name = "index_name", value = {"name"}, unique = true)})
public class LogFilter implements Comparable<LogFilter> {
    public static final Comparator<LogFilter> COMPARATOR = (o1, o2) ->
            AlphanumComparator.compareStringIgnoreCase(o1.name, o2.name);

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @Override
    public int compareTo(@NonNull LogFilter o) {
        return COMPARATOR.compare(this, o);
    }
}
