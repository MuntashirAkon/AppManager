// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;

@Dao
public interface ArchivedAppDao {
    @Query("SELECT * FROM archived_apps ORDER BY app_name ASC")
    LiveData<List<ArchivedApp>> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ArchivedApp archivedApp);

    @Delete
    void delete(ArchivedApp archivedApp);

    @Query("DELETE FROM archived_apps WHERE package_name = :packageName")
    void deleteByPackageName(String packageName);
}
