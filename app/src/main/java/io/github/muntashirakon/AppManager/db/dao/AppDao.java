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

import androidx.room.*;
import io.github.muntashirakon.AppManager.db.entity.App;

import java.util.List;

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

    @Delete
    void delete(App app);

    @Delete
    void delete(List<App> apps);

    @Query("DELETE FROM app WHERE package_name = :packageName AND user_id = :userId")
    void delete(String packageName, int userId);
}
