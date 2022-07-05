// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.Backup;

@Dao
public interface BackupDao {
    @Query("SELECT * FROM backup")
    List<Backup> getAll();

    @Query("SELECT * FROM backup WHERE package_name = :packageName")
    List<Backup> get(String packageName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<Backup> backups);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Backup backup);

    @Update
    void update(Backup backup);

    @Delete
    void delete(Backup backup);

    @Delete
    void delete(List<Backup> backups);

    @Query("DELETE FROM backup WHERE package_name = :packageName AND backup_name = :backupName")
    void delete(String packageName, String backupName);

    @Query("DELETE FROM backup WHERE 1")
    void deleteAll();
}
