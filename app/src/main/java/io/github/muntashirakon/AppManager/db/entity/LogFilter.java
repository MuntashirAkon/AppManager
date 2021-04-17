/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
