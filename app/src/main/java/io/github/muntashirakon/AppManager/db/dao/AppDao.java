// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.App;

@Dao
public interface AppDao {
    @Query("SELECT * FROM app")
    List<App> getAll();

    @Query("SELECT * FROM app WHERE is_installed = 1")
    List<App> getAllInstalled();

    @Query("SELECT * FROM app WHERE package_name = :packageName")
    List<App> getAll(String packageName);

    @Query("SELECT * FROM app WHERE package_name = :packageName AND user_id = :userId")
    List<App> getAll(String packageName, int userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<App> apps);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(App app);

    @Update
    void update(App app);

    @Query("DELETE FROM app WHERE 1")
    void deleteAll();

    @Delete
    void delete(App app);

    @Delete
    void delete(List<App> apps);

    @Query("DELETE FROM app WHERE package_name = :packageName AND user_id = :userId")
    void delete(String packageName, int userId);
}
