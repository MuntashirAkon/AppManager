// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.VtFileSource;

@Dao
public interface VtFileSourceDao {
    @Query("SELECT * FROM vt_file_source WHERE file_id = :fileId ORDER BY last_seen_at DESC")
    List<VtFileSource> getForFile(long fileId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(@NonNull VtFileSource source);

    @Query("DELETE FROM vt_file_source WHERE file_id = :fileId")
    void deleteForFile(long fileId);
}
