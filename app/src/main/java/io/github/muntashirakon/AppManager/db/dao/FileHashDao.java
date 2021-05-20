// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.github.muntashirakon.AppManager.db.entity.FileHash;

@Dao
public interface FileHashDao {
    @Query("SELECT hash FROM file_hash WHERE path = :path LIMIT 1")
    String getHash(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FileHash fileHash);
}
