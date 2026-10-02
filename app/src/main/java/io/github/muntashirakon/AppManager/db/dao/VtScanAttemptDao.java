// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;

@Dao
public interface VtScanAttemptDao {
    @Query("SELECT * FROM vt_scan_attempt WHERE file_id = :fileId ORDER BY created_at DESC")
    List<VtScanAttempt> getForFile(long fileId);

    @Query("SELECT * FROM vt_scan_attempt WHERE status IN " +
            "('pending_consent', 'queued', 'looking_up', 'uploading', 'analysing') " +
            "ORDER BY updated_at ASC")
    List<VtScanAttempt> getActive();

    @Nullable
    @Query("SELECT * FROM vt_scan_attempt WHERE file_id = :fileId AND status IN " +
            "('pending_consent', 'queued', 'looking_up', 'uploading', 'analysing') " +
            "ORDER BY updated_at DESC LIMIT 1")
    VtScanAttempt getActiveForFile(long fileId);

    @Nullable
    @Query("SELECT * FROM vt_scan_attempt WHERE id = :id LIMIT 1")
    VtScanAttempt get(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(@NonNull VtScanAttempt attempt);

    @Update
    void update(@NonNull VtScanAttempt attempt);

    @Query("DELETE FROM vt_scan_attempt WHERE id IN (SELECT id FROM vt_scan_attempt " +
            "WHERE file_id = :fileId AND status IN ('completed', 'failed', 'cancelled') " +
            "ORDER BY updated_at ASC LIMIT -1 OFFSET :keepCount)")
    int deleteOldTerminalForFile(long fileId, int keepCount);

    @Query("DELETE FROM vt_scan_attempt WHERE id IN (SELECT id FROM vt_scan_attempt " +
            "WHERE status IN ('completed', 'failed', 'cancelled') " +
            "ORDER BY updated_at ASC LIMIT -1 OFFSET :keepCount)")
    int deleteOldTerminal(int keepCount);

    @Delete
    void delete(@NonNull VtScanAttempt attempt);

    @Query("DELETE FROM vt_scan_attempt WHERE id = :id")
    void delete(long id);
}
