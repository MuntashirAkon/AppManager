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

package io.github.muntashirakon.AppManager.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.LogFilter;

@Dao
public interface LogFilterDao {
    @Query("SELECT * FROM log_filter")
    List<LogFilter> getAll();

    @Query("SELECT * FROM log_filter WHERE id = :id LIMIT 1")
    LogFilter get(long id);

    @Query("INSERT INTO log_filter (name) VALUES(:filterName)")
    long insert(String filterName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LogFilter logFilter);

    @Delete
    void delete(LogFilter logFilter);

    @Query("DELETE FROM log_filter WHERE id = :id")
    void delete(int id);
}
