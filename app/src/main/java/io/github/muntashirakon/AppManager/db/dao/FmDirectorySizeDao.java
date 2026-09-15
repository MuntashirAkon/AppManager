// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.github.muntashirakon.AppManager.db.entity.FmDirectorySize;

@Dao
public interface FmDirectorySizeDao {
    @Nullable
    @Query("SELECT * FROM fm_directory_size WHERE directory_key = :directoryKey LIMIT 1")
    FmDirectorySize get(@NonNull String directoryKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull FmDirectorySize size);

    @Query("DELETE FROM fm_directory_size WHERE directory_key = :directoryKey")
    void delete(@NonNull String directoryKey);

    @Query("DELETE FROM fm_directory_size WHERE last_used_at < :cutoff")
    int deleteOlderThan(long cutoff);
}
