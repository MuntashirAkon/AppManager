// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.OpHistory;

@Dao
public interface OpHistoryDao {
    @Query("SELECT * FROM op_history")
    List<OpHistory> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(OpHistory opHistory);

    @Query("DELETE FROM op_history WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM op_history WHERE 1")
    void deleteAll();
}
