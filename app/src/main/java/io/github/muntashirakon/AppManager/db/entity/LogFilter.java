// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Comparator;

@SuppressWarnings("NullableProblems")
@Entity(tableName = "log_filter", indices = {@Index(name = "index_name", value = {"name"}, unique = true)})
public class LogFilter implements Comparable<LogFilter> {
    public static final Comparator<LogFilter> COMPARATOR = (o1, o2) -> {
        String n1 = o1.name != null ? o1.name : "";
        String n2 = o2.name != null ? o2.name : "";
        return n1.compareToIgnoreCase(n2);
    };

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @Override
    public int compareTo(@NonNull LogFilter o) {
        return COMPARATOR.compare(this, o);
    }
}
