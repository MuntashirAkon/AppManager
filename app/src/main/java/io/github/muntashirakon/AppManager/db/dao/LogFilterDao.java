// SPDX-License-Identifier: GPL-3.0-or-later

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
